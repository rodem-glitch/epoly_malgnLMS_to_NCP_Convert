package kr.go.growailms.job.classify;

import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import kr.go.growailms.job.code.JobKoreaCodeCatalog;
import kr.go.growailms.job.service.dto.JobRecruitItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class JobRecruitReclassificationService {
    // 왜: 잡코리아 결과는 외부 직무코드(업직종)만으로는 분류가 섞일 수 있어,
    //      공고 텍스트를 기준으로 "표준 소분류(occupationcode depth3)"로 재분류해 필터링합니다.

    private static final Logger log = LoggerFactory.getLogger(JobRecruitReclassificationService.class);

    private static final String FILTER_EXPRESSION = "source == 'occupationcode'";
    private static final int DEFAULT_TOP_K = 10;
    private static final double DEFAULT_THRESHOLD = 0.0;
    private static final int CACHE_MAX_SIZE = 5000;

    private final JobOccupationVectorIndexService jobOccupationVectorIndexService;
    private final VectorStoreService vectorStoreService;

    private final Map<String, List<JobStandardClassification>> cache = Collections.synchronizedMap(
        new LinkedHashMap<>(1024, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<JobStandardClassification>> eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        }
    );

    public JobRecruitReclassificationService(
        JobOccupationVectorIndexService jobOccupationVectorIndexService,
        VectorStoreService vectorStoreService
    ) {
        this.jobOccupationVectorIndexService = Objects.requireNonNull(jobOccupationVectorIndexService);
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
    }

    public Optional<JobStandardClassification> classify(JobRecruitItem item) {
        List<JobStandardClassification> topK = classifyTopK(item);
        if (topK.isEmpty()) return Optional.empty();
        return Optional.of(topK.get(0));
    }

    public List<JobStandardClassification> classifyTopK(JobRecruitItem item) {
        if (item == null) {
            throw new IllegalArgumentException("직무 재분류 실패: 공고(item)가 null입니다.");
        }
        String cacheKey = buildCacheKey(item);
        List<JobStandardClassification> cached = cache.get(cacheKey);
        if (cached != null) return cached;

        try {
            jobOccupationVectorIndexService.ensureIndexed();
        } catch (Exception e) {
            // 왜: 폴백으로 조용히 넘기면(=빈 결과) 화면에서는 "그냥 안나오는" 현상만 보입니다. 실패는 즉시 에러로 노출합니다.
            log.error(
                "직무 소분류 벡터 색인 준비 실패: provider={}, id={}, title={}",
                toString(item.infoSvc()),
                toString(item.wantedAuthNo()),
                toString(item.title()),
                e
            );
            throw new IllegalStateException("직무 재분류 실패: 벡터 색인 준비 실패", e);
        }

        String query = buildQueryText(item);
        if (query.isBlank()) {
            log.error(
                "직무 재분류 실패: 검색 텍스트가 비었습니다. provider={}, id={}, title={}",
                toString(item.infoSvc()),
                toString(item.wantedAuthNo()),
                toString(item.title())
            );
            throw new IllegalStateException("직무 재분류 실패: 검색 텍스트가 비었습니다.");
        }

        List<VectorSearchResult> results;
        try {
            results = vectorStoreService.similaritySearch(query, DEFAULT_TOP_K, DEFAULT_THRESHOLD, FILTER_EXPRESSION);
        } catch (Exception e) {
            log.error(
                "직무 재분류 벡터 검색 실패: provider={}, id={}, title={}",
                toString(item.infoSvc()),
                toString(item.wantedAuthNo()),
                toString(item.title()),
                e
            );
            throw new IllegalStateException("직무 재분류 실패: 벡터 검색 실패", e);
        }

        if (results == null || results.isEmpty()) {
            log.error(
                "직무 재분류 벡터 검색 결과가 비었습니다: provider={}, id={}, title={}",
                toString(item.infoSvc()),
                toString(item.wantedAuthNo()),
                toString(item.title())
            );
            throw new IllegalStateException("직무 재분류 실패: 검색 결과 0건");
        }

        List<JobStandardClassification> classifications = results.stream()
            .map(r -> toClassification(item, r))
            .filter(Objects::nonNull)
            .toList();

        if (classifications.isEmpty()) {
            log.error(
                "직무 재분류 벡터 검색 결과에서 usable classification이 없습니다: provider={}, id={}, title={}",
                toString(item.infoSvc()),
                toString(item.wantedAuthNo()),
                toString(item.title())
            );
            throw new IllegalStateException("직무 재분류 실패: usable classification 0건");
        }

        cache.put(cacheKey, classifications);
        return classifications;
    }

    public boolean matchesRequestedOccupation(JobRecruitItem item, String requestedOccupationCode) {
        String requested = toString(requestedOccupationCode);
        if (requested.isBlank()) return true;

        List<JobStandardClassification> classified = classifyTopK(item);
        if (classified.isEmpty()) return false;

        for (JobStandardClassification c : classified) {
            if (c == null) continue;
            if (requested.equals(c.standardCode())
                || requested.equals(c.depth2Code())
                || requested.equals(c.depth1Code())) {
                return true;
            }
        }
        return false;
    }

    private String buildQueryText(JobRecruitItem item) {
        // 왜: 잡코리아 API는 상세 직무설명이 항상 오지 않아서, 우선 "제목+회사" 중심으로 임베딩합니다.
        String title = toString(item.title());
        String company = toString(item.company());
        if (title.isBlank() && company.isBlank()) return "";

        StringBuilder sb = new StringBuilder();
        if (!title.isBlank()) sb.append("공고: ").append(title).append("\n");
        if (!company.isBlank()) sb.append("회사: ").append(company).append("\n");

        // 왜: 잡코리아 공고는 업직종 코드(jobsCd)가 함께 오므로, 코드명까지 같이 넣어주면 재분류 품질이 올라갑니다.
        // (제목만으로는 "백엔드/웹/앱"처럼 뭉뚱그려져 소분류가 흔들리는 케이스가 많습니다)
        if ("JOBKOREA".equalsIgnoreCase(toString(item.infoSvc()))) {
            String jobCode = toString(item.jobsCd());
            if (looksLikeJobKoreaOccupationCode(jobCode)) {
                String label = JobKoreaCodeCatalog.resolveOccupationDisplayName(jobCode);
                if (!label.isBlank()) sb.append("잡코리아 직무: ").append(label).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private boolean looksLikeJobKoreaOccupationCode(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.isBlank()) return false;
        return value.matches("^100\\d{2,4}$");
    }

    private String buildCacheKey(JobRecruitItem item) {
        String provider = toString(item.infoSvc());
        String id = toString(item.wantedAuthNo());
        if (!provider.isBlank() && !id.isBlank()) {
            return provider + ":" + id;
        }
        // 왜: 외부 공고 id가 없을 때도 캐시가 완전히 깨지지 않도록 title 기반의 "대체 키"를 사용합니다.
        String title = toString(item.title());
        String company = toString(item.company());
        return provider + ":" + title + ":" + company;
    }

    private String toString(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        return s.isBlank() ? "" : s;
    }

    private JobStandardClassification toClassification(JobRecruitItem item, VectorSearchResult result) {
        if (result == null) return null;
        Map<String, Object> meta = result.metadata();
        if (meta == null) {
            log.warn(
                "직무 재분류 벡터 검색 결과 메타데이터가 비었습니다: provider={}, id={}, title={}",
                toString(item == null ? null : item.infoSvc()),
                toString(item == null ? null : item.wantedAuthNo()),
                toString(item == null ? null : item.title())
            );
            return null;
        }

        String standardCode = toString(meta.get("standard_code"));
        if (standardCode.isBlank()) {
            log.warn(
                "직무 재분류 표준코드(standard_code)가 비었습니다: provider={}, id={}, title={}, metaKeys={}",
                toString(item == null ? null : item.infoSvc()),
                toString(item == null ? null : item.wantedAuthNo()),
                toString(item == null ? null : item.title()),
                meta.keySet()
            );
            return null;
        }

        return new JobStandardClassification(
            standardCode,
            toString(meta.get("depth1_code")),
            toString(meta.get("depth2_code")),
            result.score()
        );
    }

    public record JobStandardClassification(
        String standardCode,
        String depth1Code,
        String depth2Code,
        double score
    ) {
    }
}
