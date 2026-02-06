package kr.go.growailms.job.service;

import jakarta.annotation.PreDestroy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.job.classify.JobKoreaOccupationCodeRecommendationService;
import kr.go.growailms.job.classify.JobRecruitReclassificationService;
import kr.go.growailms.job.classify.JobOccupationQueryRecommendationService;
import kr.go.growailms.job.client.JobKoreaClient;
import kr.go.growailms.job.client.Work24Client;
import kr.go.growailms.job.client.Work24CodeClient;
import kr.go.growailms.job.config.JobKoreaProperties;
import kr.go.growailms.job.config.Work24Properties;
import kr.go.growailms.job.repository.JobRepository;
import kr.go.growailms.job.repository.JobRepository.JobOccupationCodeRow;
import kr.go.growailms.job.repository.JobRepository.JobRecruitCacheKey;
import kr.go.growailms.job.repository.JobRepository.JobRecruitCacheRow;
import kr.go.growailms.job.repository.JobRepository.JobRegionCodeRow;
import kr.go.growailms.job.repository.JobRepository.OccupationCodeInsertRow;
import kr.go.growailms.job.repository.JobRepository.RegionCodeInsertRow;
import kr.go.growailms.job.service.dto.JobOccupationCodeResponse;
import kr.go.growailms.job.service.dto.JobRecruitItem;
import kr.go.growailms.job.service.dto.JobRecruitListResponse;
import kr.go.growailms.job.service.dto.JobRecruitSearchCriteria;
import kr.go.growailms.job.service.dto.JobRegionCodeResponse;
import kr.go.growailms.job.service.dto.JobCodeSyncResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
public class JobService {
    // 왜: 채용 API는 Work24 실시간 조회 + DB 캐시를 동시에 지원해야 해서 서비스에서 정책을 통합합니다.

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final Pattern WORK24_SMALL_OCCUPATION_CODE = Pattern.compile("^\\d{6}$");
    private static final int JOB_RECLASSIFY_THREADS = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
    private static final int JOB_RECLASSIFY_BATCH_SIZE = 20;
    private static final long JOB_RECLASSIFY_TASK_TIMEOUT_SECONDS = 15L;

    private final JobRepository jobRepository;
    private final Work24Client work24Client;
    private final Work24CodeClient work24CodeClient;
    private final Work24Properties work24Properties;
    private final JobKoreaClient jobKoreaClient;
    private final JobKoreaProperties jobKoreaProperties;
    private final JobKoreaOccupationCodeRecommendationService jobKoreaOccupationCodeRecommendationService;
    private final JobRecruitReclassificationService jobRecruitReclassificationService;
    private final JobOccupationQueryRecommendationService jobOccupationQueryRecommendationService;
    private final ObjectMapper objectMapper;
    private final ExecutorService jobReclassifyExecutor;

    public JobService(
        JobRepository jobRepository,
        Work24Client work24Client,
        Work24CodeClient work24CodeClient,
        Work24Properties work24Properties,
        JobKoreaClient jobKoreaClient,
        JobKoreaProperties jobKoreaProperties,
        JobKoreaOccupationCodeRecommendationService jobKoreaOccupationCodeRecommendationService,
        JobRecruitReclassificationService jobRecruitReclassificationService,
        JobOccupationQueryRecommendationService jobOccupationQueryRecommendationService,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = Objects.requireNonNull(jobRepository);
        this.work24Client = Objects.requireNonNull(work24Client);
        this.work24CodeClient = Objects.requireNonNull(work24CodeClient);
        this.work24Properties = Objects.requireNonNull(work24Properties);
        this.jobKoreaClient = Objects.requireNonNull(jobKoreaClient);
        this.jobKoreaProperties = Objects.requireNonNull(jobKoreaProperties);
        this.jobKoreaOccupationCodeRecommendationService = Objects.requireNonNull(jobKoreaOccupationCodeRecommendationService);
        this.jobRecruitReclassificationService = Objects.requireNonNull(jobRecruitReclassificationService);
        this.jobOccupationQueryRecommendationService = Objects.requireNonNull(jobOccupationQueryRecommendationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.jobReclassifyExecutor = Executors.newFixedThreadPool(JOB_RECLASSIFY_THREADS, new JobThreadFactory("job-reclassify-"));
    }

    @PreDestroy
    public void shutdownExecutors() {
        // 왜: devtools 재시작/서버 종료 시 스레드가 남아 포트 점유/프로세스 종료가 지연되는 것을 막습니다.
        jobReclassifyExecutor.shutdownNow();
    }

    public List<JobRegionCodeResponse> getRegionCodes(String depthType, String depth1) {
        return getRegionCodes(depthType, depth1, Provider.WORK24);
    }

    public List<JobRegionCodeResponse> getRegionCodes(String depthType, String depth1, Provider provider) {
        Provider safeProvider = provider == null ? Provider.WORK24 : provider;
        if (safeProvider == Provider.JOBKOREA) {
            // 왜: 잡코리아는 별도 코드(알파벳+숫자)라서 Work24 코드 테이블을 그대로 재사용할 수 없습니다.
            // 현재 화면은 depthType=1(시/도) 단일 셀렉트만 쓰므로, 여기서는 상위 코드만 제공합니다.
            if (!"1".equals(String.valueOf(depthType).trim())) return List.of();
            return kr.go.growailms.job.code.JobKoreaCodeCatalog.topLevelAreaCodes().stream()
                .map(item -> new JobRegionCodeResponse(
                    item.code(),
                    item.name(),
                    item.name(),
                    "",
                    ""
                ))
                .toList();
        }

        List<JobRegionCodeRow> rows = jobRepository.findRegionCodes(depthType, depth1);
        return rows.stream()
            .map(row -> new JobRegionCodeResponse(
                String.valueOf(row.idx()),
                row.title(),
                row.depth1(),
                row.depth2(),
                row.depth3()
            ))
            .toList();
    }

    public List<JobOccupationCodeResponse> getOccupationCodes(String depthType, String depth1, String depth2) {
        return getOccupationCodes(depthType, depth1, depth2, Provider.WORK24);
    }

    public List<JobOccupationCodeResponse> getOccupationCodes(String depthType, String depth1, String depth2, Provider provider) {
        Provider safeProvider = provider == null ? Provider.WORK24 : provider;
        if (safeProvider == Provider.JOBKOREA) {
            String safeDepthType = (depthType == null || depthType.isBlank()) ? "1" : depthType.trim();
            return switch (safeDepthType) {
                case "1" -> kr.go.growailms.job.code.JobKoreaCodeCatalog.rbcd().stream()
                    .map(item -> new JobOccupationCodeResponse(
                        0,
                        item.code(),
                        item.name(),
                        item.name(),
                        "",
                        ""
                    ))
                    .toList();
                case "2" -> {
                    String parent = (depth1 == null) ? "" : depth1.trim();
                    yield kr.go.growailms.job.code.JobKoreaCodeCatalog.rpcd(parent).stream()
                        .map(item -> new JobOccupationCodeResponse(
                            0,
                            item.code(),
                            item.name(),
                            "",
                            item.name(),
                            ""
                        ))
                        .toList();
                }
                default -> List.of();
            };
        }

        List<JobOccupationCodeRow> rows = jobRepository.findOccupationCodes(depthType, depth1, depth2);
        return rows.stream()
            .map(row -> new JobOccupationCodeResponse(
                row.idx(),
                row.code(),
                row.title(),
                row.depth1(),
                row.depth2(),
                row.depth3()
            ))
            .toList();
    }

    public JobCodeSyncResponse refreshWork24Codes(boolean refreshRegion, boolean refreshOccupation) {
        int regionCount = 0;
        int occupationCount = 0;

        if (!refreshRegion && !refreshOccupation) {
            throw new IllegalArgumentException("refresh 대상이 비어 있습니다. target=ALL/REGION/OCCUPATION 중 선택해 주세요.");
        }

        if (refreshRegion) {
            // 왜: 근무지역 셀렉트는 regioncode 테이블을 그대로 보므로, 여기서 최신 코드를 적재해야 합니다.
            List<Work24CodeClient.RegionCodeItem> items = work24CodeClient.fetchRegionCodes();
            List<RegionCodeInsertRow> rows = new ArrayList<>();
            for (Work24CodeClient.RegionCodeItem item : items) {
                Integer idx = parseRegionCode(item.code());
                if (idx == null) {
                    // 왜: 지역 코드는 숫자여야 API 파라미터로 사용 가능합니다.
                    log.warn("Work24 지역 코드가 숫자가 아닙니다. code={}", item.code());
                    continue;
                }
                rows.add(new RegionCodeInsertRow(
                    idx,
                    safe(item.depth1()),
                    safe(item.depth2()),
                    safe(item.depth3())
                ));
            }
            jobRepository.replaceRegionCodes(rows);
            regionCount = rows.size();
        }

        if (refreshOccupation) {
            List<OccupationCodeInsertRow> rows = loadOccupationCodesFromCsv();
            jobRepository.replaceOccupationCodes(rows);
            occupationCount = rows.size();
        }

        return new JobCodeSyncResponse(regionCount, occupationCount);
    }

    private List<OccupationCodeInsertRow> loadOccupationCodesFromCsv() {
        Path csvPath = resolveOccupationCsvPath();
        List<OccupationCodeInsertRow> rows = new ArrayList<>();
        Set<String> seenCodes = new HashSet<>();

        String currentDepth1Name = "";
        String currentDepth1Code = "";
        String currentDepth2Name = "";
        String currentDepth2Code = "";

        Charset charset = Charset.forName("MS949");
        try (BufferedReader reader = Files.newBufferedReader(csvPath, charset)) {
            String header = reader.readLine();
            if (header == null || header.isBlank()) {
                throw new IllegalStateException("직종코드 CSV 헤더가 비어 있습니다.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 4) continue;

                String rawId = cols.get(0).trim();
                String depth1 = safe(cols.get(1));
                String depth2 = safe(cols.get(2));
                String depth3 = safe(cols.get(3));

                if (!depth1.isEmpty()) {
                    currentDepth1Name = depth1;
                    currentDepth1Code = normalizeDepth1Code(rawId);
                    currentDepth2Name = "";
                    currentDepth2Code = "";

                    addOccupationRow(rows, seenCodes,
                        currentDepth1Code,
                        null,
                        currentDepth1Name,
                        "",
                        ""
                    );
                    continue;
                }

                if (!depth2.isEmpty()) {
                    if (currentDepth1Code.isEmpty()) {
                        // 왜: CSV 구조상 대분류가 먼저 와야 하지만, 예외 데이터가 있으면 건너뜁니다.
                        log.warn("직종코드 CSV: 대분류 없이 중분류가 등장했습니다. code={}", rawId);
                        continue;
                    }
                    currentDepth2Name = depth2;
                    currentDepth2Code = normalizeDepth2Code(rawId);

                    addOccupationRow(rows, seenCodes,
                        currentDepth2Code,
                        currentDepth1Code,
                        currentDepth1Name,
                        currentDepth2Name,
                        ""
                    );
                    continue;
                }

                if (!depth3.isEmpty()) {
                    if (currentDepth2Code.isEmpty()) {
                        // 왜: CSV 구조상 중분류가 먼저 와야 하지만, 예외 데이터가 있으면 건너뜁니다.
                        log.warn("직종코드 CSV: 중분류 없이 소분류가 등장했습니다. code={}", rawId);
                        continue;
                    }
                    String code = normalizeDepth3Code(rawId);
                    addOccupationRow(rows, seenCodes,
                        code,
                        currentDepth2Code,
                        currentDepth1Name,
                        currentDepth2Name,
                        depth3
                    );
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("직종코드 CSV 로딩에 실패했습니다: " + e.getMessage(), e);
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException("직종코드 CSV에서 적재할 데이터가 없습니다.");
        }

        return rows;
    }

    private Path resolveOccupationCsvPath() {
        String raw = work24Properties.getOccupationCsvPath();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("work24.occupation-csv-path가 비어 있습니다.");
        }

        Path path = Paths.get(raw.trim());
        if (path.isAbsolute()) return path;

        // 왜: bootRun 실행 디렉터리 기준 상대경로를 허용하면 로컬에서 바로 테스트가 가능합니다.
        String baseDir = System.getProperty("user.dir", ".");
        return Paths.get(baseDir).resolve(path).normalize();
    }

    private void addOccupationRow(
        List<OccupationCodeInsertRow> rows,
        Set<String> seenCodes,
        String code,
        String parentCode,
        String depth1,
        String depth2,
        String depth3
    ) {
        if (code == null || code.isBlank()) return;
        if (!seenCodes.add(code)) return;
        rows.add(new OccupationCodeInsertRow(
            code.trim(),
            parentCode == null ? null : parentCode.trim(),
            safe(depth1),
            safe(depth2),
            safe(depth3)
        ));
    }

    private static String safe(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.isBlank() ? "" : trimmed;
    }

    private static String normalizeDepth1Code(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.isBlank()) return "";
        if (v.matches("^\\d+$")) {
            return v.length() == 1 ? ("0" + v) : v;
        }
        return v;
    }

    private static String normalizeDepth2Code(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.isBlank()) return "";
        if (v.matches("^\\d{2}$")) return "0" + v;
        return v;
    }

    private static String normalizeDepth3Code(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.isBlank()) return "";
        if (v.matches("^\\d{5}$")) return "0" + v;
        return v;
    }

    private static List<String> parseCsvLine(String line) {
        // 왜: CSV 안에 쉼표(,)가 들어간 항목이 있어 단순 split(",")로는 파싱이 깨집니다.
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
                continue;
            }
            sb.append(c);
        }
        out.add(sb.toString());
        return out;
    }

    public JobRecruitListResponse getRecruitments(
        String region,
        String occupation,
        String salTp,
        Integer minPay,
        Integer maxPay,
        String education,
        Integer startPage,
        Integer display,
        CachePolicy cachePolicy
    ) {
        return getRecruitments(region, occupation, salTp, minPay, maxPay, education, startPage, display, Provider.WORK24, cachePolicy);
    }

    public JobRecruitListResponse getRecruitments(
        String region,
        String occupation,
        String salTp,
        Integer minPay,
        Integer maxPay,
        String education,
        Integer startPage,
        Integer display,
        Provider provider,
        CachePolicy cachePolicy
    ) {
        Provider safeProvider = provider == null ? Provider.WORK24 : provider;
        JobRecruitSearchCriteria criteria = normalizeCriteria(region, occupation, salTp, minPay, maxPay, education, startPage, display);
        CachePolicy safePolicy = cachePolicy == null ? CachePolicy.PREFER_CACHE : cachePolicy;

        if (safeProvider == Provider.ALL) {
            if (hasWork24OnlyFilters(criteria)) {
                throw new IllegalArgumentException("급여/학력 필터는 통합 제공처에서 지원하지 않습니다.");
            }
            if (safePolicy != CachePolicy.FORCE_LIVE) {
                Optional<JobRecruitListResponse> cached = findCachedRecruitments(criteria, safeProvider, safePolicy);
                if (cached.isPresent()) {
                    return cached.get();
                }
            }

            JobRecruitListResponse merged = mergeRecruitments(criteria, safePolicy);
            saveRecruitCache(criteria, safeProvider, merged);
            return merged;
        }

        if (safePolicy != CachePolicy.FORCE_LIVE) {
            Optional<JobRecruitListResponse> cached = findCachedRecruitments(criteria, safeProvider, safePolicy);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        JobRecruitListResponse live = (safeProvider == Provider.WORK24 && criteria.education() != null)
            ? fetchWork24WithEducationUnion(criteria)
            : fetchFromProvider(criteria, safeProvider);
        saveRecruitCache(criteria, safeProvider, live);
        return live;
    }

    public JobRecruitListResponse getRelatedRecruitments(String occupation, Integer limit, CachePolicy cachePolicy) {
        int safeLimit = normalizeDisplay(limit, 3);
        return getRecruitments(null, occupation, null, null, null, null, 1, safeLimit, cachePolicy);
    }

    public JobRecruitListResponse getRecruitmentsByNaturalLanguageForAll(
        String query,
        String region,
        String salTp,
        Integer minPay,
        Integer maxPay,
        String education,
        Integer startPage,
        Integer display,
        CachePolicy cachePolicy
    ) {
        // 왜: 요청사항 - ALL 제공처에서만 자연어 입력으로 상위 3개 소분류를 뽑아 "합쳐서" 검색합니다.
        // - 폴백은 넣지 않습니다(문제는 로그/에러로 드러나야 합니다).
        String q = trimToNull(query);
        if (q == null) {
            throw new IllegalArgumentException("자연어 검색(query)이 비어 있습니다.");
        }

        // 왜: 요청사항 - 자연어 검색은 "필터와 무관하게" 통합(ALL)로만 조회합니다.
        //      따라서 region/occupation/급여/학력 입력은 모두 무시하고, 페이징(startPage/display)만 사용합니다.
        JobRecruitSearchCriteria base = normalizeCriteria(null, null, null, null, null, null, startPage, display);
        CachePolicy safePolicy = cachePolicy == null ? CachePolicy.PREFER_CACHE : cachePolicy;

        // 1) 자연어 → 표준 소분류 코드(top3) 추천
        List<JobOccupationQueryRecommendationService.RecommendedOccupation> rec = jobOccupationQueryRecommendationService.recommendTopK(q, 3);
        List<String> codes = rec.stream()
            .map(JobOccupationQueryRecommendationService.RecommendedOccupation::standardCode)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .limit(3)
            .toList();

        if (codes.isEmpty()) {
            // recommendTopK에서 이미 예외를 던지지만, 방어적으로 한 번 더 확인합니다.
            log.error("자연어 검색 추천 코드 0건: query='{}'", q);
            throw new IllegalStateException("자연어 검색 실패: 추천 코드 0건");
        }

        log.info("자연어 검색 시작(ALL): query='{}', recommendedCodes={}", q, codes);

        // 2) 상위 3개 코드로 각각 검색(ALL 병합 로직 재사용) 후 합치기
        // 왜: "합쳐서 검색"은 OR 조건이므로, 각 코드 결과를 합치고 중복을 제거합니다.
        List<JobRecruitListResponse> parts = new ArrayList<>(codes.size());
        int totalSum = 0;

        for (String code : codes) {
            JobRecruitListResponse res = getRecruitments(
                base.region(),
                code,
                null,
                null,
                null,
                null,
                base.startPage(),
                base.display(),
                Provider.ALL,
                safePolicy
            );
            parts.add(res);
            totalSum += Math.max(0, res == null ? 0 : res.total());
            log.info(
                "자연어 검색 부분 결과: query='{}', code={}, total={}, items={}",
                q,
                code,
                (res == null ? 0 : res.total()),
                (res == null || res.wanted() == null ? 0 : res.wanted().size())
            );
        }

        // 3) 결과 합치기(라운드로빈 + 중복 제거) + display 만큼만 반환
        LinkedHashMap<String, JobRecruitItem> mergedByKey = new LinkedHashMap<>();
        List<List<JobRecruitItem>> lists = parts.stream()
            // 왜: 통합(ALL) 결과는 내부적으로 work24를 먼저 붙이고 잡코리아를 뒤에 붙입니다.
            //      그런데 자연어 검색은 "상위 3개 소분류"를 합쳐서 다시 display로 자르기 때문에,
            //      앞쪽(Work24)만 계속 뽑혀 잡코리아가 화면에 안 섞이는 문제가 생깁니다.
            //      그래서 각 코드별 목록에서 work24/jobkorea를 미리 교차(interleave)시켜, 초반에도 잡코리아가 섞이게 만듭니다.
            .map(r -> JobRecruitInterleaveUtil.interleaveProvidersForAll((r == null || r.wanted() == null) ? List.<JobRecruitItem>of() : r.wanted()))
            .toList();
        int[] idx = new int[lists.size()];

        while (mergedByKey.size() < base.display()) {
            boolean advanced = false;
            for (int i = 0; i < lists.size(); i++) {
                List<JobRecruitItem> list = lists.get(i);
                int cursor = idx[i];
                if (cursor >= list.size()) continue;

                JobRecruitItem item = list.get(cursor);
                idx[i] = cursor + 1;
                advanced = true;

                if (item == null) continue;
                String key = buildRecruitItemKey(item);
                if (key.isBlank()) continue;
                mergedByKey.putIfAbsent(key, item);

                if (mergedByKey.size() >= base.display()) break;
            }
            if (!advanced) break;
        }

        if (mergedByKey.isEmpty()) {
            log.error("자연어 검색 합친 결과 0건: query='{}', codes={}", q, codes);
            throw new IllegalStateException("자연어 검색 결과가 0건입니다.");
        }

        // 최종 요약 로그(디버깅)
        int jobkoreaCount = 0;
        int work24Count = 0;
        for (JobRecruitItem item : mergedByKey.values()) {
            if (item == null || item.infoSvc() == null) {
                work24Count++;
                continue;
            }
            String svc = item.infoSvc().trim().toUpperCase();
            if ("JOBKOREA".equals(svc)) jobkoreaCount++; else work24Count++;
        }
        log.info(
            "자연어 검색 최종 결과(ALL): query='{}', codes={}, totalSum={}, returnedItems={}, jobkorea={}, work24={}",
            q,
            codes,
            totalSum,
            mergedByKey.size(),
            jobkoreaCount,
            work24Count
        );

        return new JobRecruitListResponse(totalSum, base.startPage(), base.display(), new ArrayList<>(mergedByKey.values()));
    }

    private boolean hasWork24OnlyFilters(JobRecruitSearchCriteria criteria) {
        if (criteria == null) return false;
        return criteria.salTp() != null
            || criteria.minPay() != null
            || criteria.maxPay() != null
            || criteria.education() != null;
    }

    private JobRecruitListResponse fetchWork24WithEducationUnion(JobRecruitSearchCriteria criteria) {
        // 왜: 요구사항상 “학력무관” 공고는 어떤 학력을 선택해도 항상 포함돼야 합니다.
        // 그런데 Work24의 education 파라미터는 OR 조건(education=선택학력 이하 + 무관)을 직접 표현하기 어렵습니다.
        // 그래서 (학력무관 00) + (초졸~선택학력) 각각을 조회해서 합칩니다.
        String education = criteria.education();
        if (education == null || education.isBlank()) {
            return work24Client.fetchRecruitList(criteria);
        }

        List<String> codes = buildEducationUnionCodes(education.trim());
        if (codes.isEmpty()) {
            return work24Client.fetchRecruitList(criteria);
        }

        LinkedHashMap<String, Integer> totalsByCode = new LinkedHashMap<>();
        for (String code : codes) {
            JobRecruitSearchCriteria one = withEducationAndPaging(criteria, code, 1, 1);
            JobRecruitListResponse res = work24Client.fetchRecruitList(one);
            totalsByCode.put(code, Math.max(0, res.total()));
        }

        int globalTotal = 0;
        for (Integer t : totalsByCode.values()) globalTotal += (t == null ? 0 : t);

        int display = criteria.display();
        int startPage = criteria.startPage();
        long offsetStart = (long) (Math.max(1, startPage) - 1) * (long) display;
        if (display <= 0) display = 10;

        List<JobRecruitItem> merged = new ArrayList<>(display);
        LinkedHashMap<String, JobRecruitListResponse> pageCache = new LinkedHashMap<>();

        long offset = offsetStart;
        int remaining = display;

        while (remaining > 0 && offset < globalTotal) {
            CodeWindow window = findCodeWindow(totalsByCode, offset);
            if (window == null) break;

            int within = (int) (offset - window.startOffset());
            int pageInCode = (within / display) + 1;
            int indexInPage = within % display;

            String cacheKey = window.code() + "|" + pageInCode;
            JobRecruitListResponse pageRes = pageCache.get(cacheKey);
            if (pageRes == null) {
                JobRecruitSearchCriteria one = withEducationAndPaging(criteria, window.code(), pageInCode, display);
                pageRes = work24Client.fetchRecruitList(one);
                pageCache.put(cacheKey, pageRes);
            }

            List<JobRecruitItem> wanted = (pageRes != null) ? pageRes.wanted() : null;
            if (wanted == null || wanted.isEmpty()) break;

            for (int i = indexInPage; i < wanted.size() && remaining > 0; i++) {
                merged.add(wanted.get(i));
                remaining--;
                offset++;
            }

            // 안전장치: 더 이상 진행이 안 되면 무한루프 방지
            if (indexInPage >= wanted.size()) break;
        }

        // 왜: total은 “합집합 총 건수”를 내려줍니다(페이지네이션이 일관되게 동작하도록).
        return new JobRecruitListResponse(globalTotal, startPage, display, merged);
    }

    private List<String> buildEducationUnionCodes(String selected) {
        // 왜: Work24 education 파라미터는 “선택한 학력 이하” 조건으로 동작하는 것으로 보고,
        // (학력무관 00)만 추가로 합치면 요구사항(무관은 항상 포함)을 만족할 수 있습니다.
        // 예: 선택=고졸(03) → (education=03 결과) + (education=00 결과)
        List<String> ordered = List.of("01", "02", "03", "04", "05", "06", "07");
        if (!ordered.contains(selected)) return List.of();
        return List.of("00", selected);
    }

    private JobRecruitSearchCriteria withEducationAndPaging(
        JobRecruitSearchCriteria base,
        String education,
        int startPage,
        int display
    ) {
        return new JobRecruitSearchCriteria(
            base.region(),
            base.occupation(),
            base.salTp(),
            base.minPay(),
            base.maxPay(),
            education,
            startPage,
            display,
            base.callType()
        );
    }

    private CodeWindow findCodeWindow(LinkedHashMap<String, Integer> totalsByCode, long offset) {
        long cursor = 0L;
        for (var entry : totalsByCode.entrySet()) {
            String code = entry.getKey();
            long size = (entry.getValue() == null) ? 0L : entry.getValue().longValue();
            long next = cursor + size;
            if (offset < next) {
                return new CodeWindow(code, cursor);
            }
            cursor = next;
        }
        return null;
    }

    private record CodeWindow(String code, long startOffset) {
    }

    public JobRecruitListResponse refreshRecruitments(
        String region,
        String occupation,
        String salTp,
        Integer minPay,
        Integer maxPay,
        String education,
        Integer startPage,
        Integer display
    ) {
        JobRecruitSearchCriteria criteria = normalizeCriteria(region, occupation, salTp, minPay, maxPay, education, startPage, display);
        JobRecruitListResponse live = work24Client.fetchRecruitList(criteria);
        saveRecruitCache(criteria, Provider.WORK24, live);
        return live;
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void refreshCachedRecruitmentsAtMorning() {
        List<JobRecruitCacheKey> keys = jobRepository.findAllRecruitCacheKeys();
        if (keys.isEmpty()) {
            return;
        }

        for (JobRecruitCacheKey key : keys) {
            try {
                refreshRecruitmentCache(key);
            } catch (Exception e) {
                log.warn("채용 캐시 갱신 실패: queryKey={}", key.queryKey(), e);
            }
        }
    }

    private void refreshRecruitmentCache(JobRecruitCacheKey key) {
        JobRecruitSearchCriteria criteria = new JobRecruitSearchCriteria(
            key.regionCode(),
            key.occupationCode(),
            null,
            null,
            null,
            null,
            key.startPage(),
            key.display(),
            "L"
        );
        Provider provider = Provider.from(key.provider());
        if (!isCacheEnabled(provider)) {
            return;
        }
        JobRecruitListResponse live = fetchFromProvider(criteria, provider);
        saveRecruitCache(criteria, provider, live);
    }

    private Optional<JobRecruitListResponse> findCachedRecruitments(
        JobRecruitSearchCriteria criteria,
        Provider provider,
        CachePolicy cachePolicy
    ) {
        if (!isCacheEnabled(provider)) {
            return Optional.empty();
        }

        String queryKey = buildQueryKey(criteria, provider);
        Optional<JobRecruitCacheRow> cached = jobRepository.findRecruitCache(queryKey, provider.name());
        if (cached.isEmpty()) {
            if (cachePolicy == CachePolicy.CACHE_ONLY) {
                throw new IllegalStateException("캐시된 채용 정보가 없습니다.");
            }
            return Optional.empty();
        }

        JobRecruitCacheRow row = cached.get();
        if (!isCacheFresh(provider, row.updatedAt())) {
            if (cachePolicy == CachePolicy.CACHE_ONLY) {
                return Optional.of(parseCache(row));
            }
            return Optional.empty();
        }

        return Optional.of(parseCache(row));
    }

    private JobRecruitListResponse parseCache(JobRecruitCacheRow row) {
        try {
            List<JobRecruitItem> items = objectMapper.readValue(
                row.payloadJson(),
                new TypeReference<List<JobRecruitItem>>() {}
            );
            return new JobRecruitListResponse(row.total(), row.startPage(), row.display(), items);
        } catch (Exception e) {
            throw new IllegalStateException("채용 캐시 파싱에 실패했습니다.", e);
        }
    }

    private void saveRecruitCache(JobRecruitSearchCriteria criteria, Provider provider, JobRecruitListResponse response) {
        if (!isCacheEnabled(provider)) {
            return;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(response.wanted());
            JobRecruitCacheRow row = new JobRecruitCacheRow(
                response.total(),
                response.startPage(),
                response.display(),
                payloadJson,
                LocalDateTime.now()
            );
            JobRecruitCacheKey key = new JobRecruitCacheKey(
                buildQueryKey(criteria, provider),
                provider.name(),
                criteria.region(),
                criteria.occupation(),
                criteria.startPage(),
                criteria.display()
            );
            jobRepository.upsertRecruitCache(row, key);
        } catch (Exception e) {
            log.warn("채용 캐시 저장에 실패했습니다.", e);
        }
    }

    private boolean isCacheFresh(Provider provider, LocalDateTime updatedAt) {
        long ttlMinutes = resolveTtlMinutes(provider);
        if (ttlMinutes <= 0) return false;
        LocalDateTime now = LocalDateTime.now();
        Duration age = Duration.between(updatedAt, now);
        return !age.isNegative() && age.toMinutes() <= ttlMinutes;
    }

    private JobRecruitSearchCriteria normalizeCriteria(
        String region,
        String occupation,
        String salTp,
        Integer minPay,
        Integer maxPay,
        String education,
        Integer startPage,
        Integer display
    ) {
        String safeSalTp = normalizeSalTp(salTp);
        Integer safeMinPay = normalizePay(minPay);
        Integer safeMaxPay = normalizePay(maxPay);
        String safeEducation = normalizeEducation(education);

        // 왜: 급여 범위는 유형+최소+최대가 함께 있어야 Work24 API에서 일관되게 동작합니다.
        if (safeSalTp != null || safeMinPay != null || safeMaxPay != null) {
            if (safeSalTp == null) {
                throw new IllegalArgumentException("급여 유형(salTp)을 선택해주세요.");
            }
            if (safeMinPay == null || safeMaxPay == null) {
                throw new IllegalArgumentException("급여 최소/최대 금액(minPay/maxPay)을 모두 입력해주세요.");
            }
            if (safeMinPay > safeMaxPay) {
                throw new IllegalArgumentException("급여 최소 금액이 최대 금액보다 클 수 없습니다.");
            }
        }

        return new JobRecruitSearchCriteria(
            trimToNull(region),
            trimToNull(occupation),
            safeSalTp,
            safeMinPay,
            safeMaxPay,
            safeEducation,
            normalizeStartPage(startPage, 1),
            normalizeDisplay(display, 10),
            "L"
        );
    }

    private String normalizeSalTp(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase();
        if (v.isBlank()) return null;
        return switch (v) {
            case "H", "D", "M", "Y" -> v;
            default -> throw new IllegalArgumentException("급여 유형(salTp)이 올바르지 않습니다.");
        };
    }

    private Integer normalizePay(Integer value) {
        if (value == null) return null;
        if (value < 0) throw new IllegalArgumentException("급여 금액은 0 이상이어야 합니다.");
        return value;
    }

    private String normalizeEducation(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isBlank()) return null;
        return switch (v) {
            case "00", "01", "02", "03", "04", "05", "06", "07" -> v;
            default -> throw new IllegalArgumentException("학력(education) 값이 올바르지 않습니다.");
        };
    }

    private int normalizeStartPage(Integer value, int fallback) {
        if (value == null || value <= 0) return fallback;
        return value;
    }

    private int normalizeDisplay(Integer value, int fallback) {
        if (value == null || value <= 0) return fallback;
        return Math.min(value, 100);
    }

    private String buildQueryKey(JobRecruitSearchCriteria criteria, Provider provider) {
        // 왜: 기존 운영 DB에는 “예전 포맷(provider|region|occupation|page|display)” 캐시가 이미 쌓여 있을 수 있습니다.
        // 필터를 안 쓰는 기본 검색은 예전 키를 그대로 써야, Work24 인증키가 없는 환경에서도 캐시로 정상 동작할 수 있습니다.
        if (!hasWork24OnlyFilters(criteria)) {
            return "%s|%s|%s|%d|%d".formatted(
                provider.name(),
                nullToDash(criteria.region()),
                nullToDash(criteria.occupation()),
                criteria.startPage(),
                criteria.display()
            );
        }

        return "%s|%s|%s|%s|%s|%s|%s|%d|%d".formatted(
            provider.name(),
            nullToDash(criteria.region()),
            nullToDash(criteria.occupation()),
            nullToDash(criteria.salTp()),
            criteria.minPay() == null ? "-" : String.valueOf(criteria.minPay()),
            criteria.maxPay() == null ? "-" : String.valueOf(criteria.maxPay()),
            nullToDash(criteria.education()),
            criteria.startPage(),
            criteria.display()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String nullToDash(String value) {
        return value == null ? "-" : value;
    }

    private JobRecruitListResponse mergeRecruitments(JobRecruitSearchCriteria criteria, CachePolicy cachePolicy) {
        JobRecruitListResponse work24 = getRecruitments(
            criteria.region(),
            criteria.occupation(),
            criteria.salTp(),
            criteria.minPay(),
            criteria.maxPay(),
            criteria.education(),
            criteria.startPage(),
            criteria.display(),
            Provider.WORK24,
            cachePolicy
        );

        // 왜: 통합(ALL)에서 잡코리아는 Work24 코드로는 필터가 적용되지 않습니다.
        //      그래서 (가능하면) 기존 매핑으로 "대충 좁혀서" 가져오고, 최종 노출은 "공고 텍스트 → 표준 소분류" 재분류로 걸러냅니다.
        JobRecruitListResponse jobkorea = fetchJobKoreaForAll(criteria, cachePolicy);
        jobkorea = filterJobKoreaByRequestedOccupation(jobkorea, criteria, cachePolicy);

        List<JobRecruitItem> merged = new java.util.ArrayList<>();
        if (work24.wanted() != null) merged.addAll(work24.wanted());
        if (jobkorea.wanted() != null) merged.addAll(jobkorea.wanted());
        int total = work24.total() + jobkorea.total();
        return new JobRecruitListResponse(total, criteria.startPage(), criteria.display(), merged);
    }

    private JobRecruitListResponse fetchJobKoreaForAll(JobRecruitSearchCriteria criteria, CachePolicy cachePolicy) {
        // 왜: 재분류(필터링) 후에도 화면에 보여줄 결과가 남도록, 잡코리아는 display를 조금 넉넉히 받아옵니다.
        int fetchDisplay = Math.min(100, Math.max(criteria.display(), criteria.display() * 5));

        String requested = trimToNull(criteria.occupation());
        if (requested != null) {
            boolean isLeaf = looksLikeWork24SmallOccupationCode(requested);

            // 왜: Work24(KEIS) 코드 ↔ 잡코리아(rpcd) 변환을 수동 테이블에만 의존하면 오매핑이 잦습니다.
            //      그래서 표준 코드명(대/중/소)을 기준으로 잡코리아 rpcd를 자동 추천해 먼저 조회합니다.
            List<String> recommended = jobKoreaOccupationCodeRecommendationService.recommendRpcdCodesByStandardCode(requested);

            // 왜: 운영에 매핑 테이블이 이미 있을 수 있으니, 소분류(6자리)일 때만 rpcd를 추가 후보로 섞습니다.
            // - rbcd(5자리)는 너무 넓어 오매핑 시 결과가 완전히 엉킬 수 있어 제외합니다.
            List<String> mapped = isLeaf
                ? jobRepository.findJobKoreaOccupationCodesByWork24Code(requested).stream()
                .filter(code -> code != null && code.trim().length() > 5)
                .toList()
                : List.of();

            List<String> candidates = new ArrayList<>();
            if (recommended != null) candidates.addAll(recommended);
            candidates.addAll(mapped);

            // 중복 제거 + 너무 많은 호출 방지
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String c : candidates) {
                if (c == null) continue;
                String v = c.trim();
                if (v.isBlank()) continue;
                unique.add(v);
                if (unique.size() >= 5) break; // 왜: 실시간 호출량 폭증을 막기 위해 상한을 둡니다.
            }

            if (unique.isEmpty()) {
                // 왜: 폴백(무필터)으로 넘어가면 "왜 잡코리아가 섞여서/안나와서" 원인이 가려집니다. 실패는 즉시 노출합니다.
                log.error("통합(ALL) 잡코리아 코드 후보가 비었습니다: standardCode={}, recommended={}, mappedRpcd={}", requested, recommended, mapped);
                throw new IllegalStateException("통합(ALL) 잡코리아 조회 실패: rpcd 후보 0건. standardCode=" + requested);
            }

            log.info("통합(ALL) 잡코리아 코드 후보: standardCode={}, recommended={}, mappedRpcd={}", requested, recommended, mapped);

            LinkedHashMap<String, JobRecruitItem> mergedByKey = new LinkedHashMap<>();
            int total = 0;

            List<String> codes = new ArrayList<>(unique);
            List<CompletableFuture<JobRecruitListResponse>> futures = new ArrayList<>(codes.size());
            for (String code : codes) {
                futures.add(CompletableFuture.supplyAsync(() -> getRecruitments(
                    criteria.region(),
                    code,
                    null,
                    null,
                    null,
                    null,
                    criteria.startPage(),
                    fetchDisplay,
                    Provider.JOBKOREA,
                    cachePolicy
                ), jobReclassifyExecutor));
            }

            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                CompletableFuture<JobRecruitListResponse> future = futures.get(i);
                JobRecruitListResponse res;
                try {
                    res = future.get(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // 왜: 폴백(continue)로 넘어가면 일부 rpcd 실패가 숨겨집니다. 실패는 즉시 노출합니다.
                    future.cancel(true);
                    log.error("통합(ALL) 잡코리아 조회 실패: rpcd={}, standardCode={}", code, requested, e);
                    throw new IllegalStateException("통합(ALL) 잡코리아 조회 실패: rpcd=" + code + ", standardCode=" + requested, e);
                }

                total += Math.max(0, res.total());
                if (res.wanted() == null) continue;
                for (JobRecruitItem item : res.wanted()) {
                    if (item == null) continue;
                    mergedByKey.putIfAbsent(buildRecruitItemKey(item), item);
                }
            }

            if (mergedByKey.isEmpty()) {
                log.error("통합(ALL) 잡코리아 조회 결과가 0건입니다: standardCode={}, rpcdCandidates={}", requested, codes);
                throw new IllegalStateException("통합(ALL) 잡코리아 조회 결과 0건: standardCode=" + requested);
            }

            return new JobRecruitListResponse(total, criteria.startPage(), fetchDisplay, new ArrayList<>(mergedByKey.values()));
        }

        // 왜: 직무 선택이 없는 '전체' 조회는 잡코리아도 무필터로 조회합니다.
        return getRecruitments(
            criteria.region(),
            null,
            null,
            null,
            null,
            null,
            criteria.startPage(),
            fetchDisplay,
            Provider.JOBKOREA,
            cachePolicy
        );
    }

    private JobRecruitListResponse filterJobKoreaByRequestedOccupation(
        JobRecruitListResponse jobkorea,
        JobRecruitSearchCriteria criteria,
        CachePolicy cachePolicy
    ) {
        int displayLimit = criteria == null ? 20 : criteria.display();
        if (jobkorea == null || jobkorea.wanted() == null || jobkorea.wanted().isEmpty()) {
            return new JobRecruitListResponse(0, criteria == null ? 1 : criteria.startPage(), displayLimit, List.of());
        }

        String requested = trimToNull(criteria == null ? null : criteria.occupation());
        if (requested == null) {
            // 왜: 직무 필터가 없으면(전체) 굳이 재분류로 비용을 쓰지 않습니다.
            return new JobRecruitListResponse(jobkorea.total(), jobkorea.startPage(), displayLimit, limit(jobkorea.wanted(), displayLimit));
        }

        int fetched = jobkorea.wanted().size();

        if (jobKoreaProperties != null
            && jobKoreaProperties.getReclassification() != null
            && !jobKoreaProperties.getReclassification().isEnabled()) {
            // 왜: 임시 실험/튜닝(=임베딩 호출량 절감) 목적일 때는, 재분류 필터를 끄고 "추천 rpcd 조회 결과"를 그대로 노출합니다.
            log.info(
                "통합(ALL) 잡코리아 재분류 비활성화: requestedCode={}, fetchedItems={}, displayLimit={}",
                requested,
                fetched,
                displayLimit
            );
            return new JobRecruitListResponse(jobkorea.total(), jobkorea.startPage(), displayLimit, limit(jobkorea.wanted(), displayLimit));
        }

        log.info("통합(ALL) 잡코리아 재분류 필터 시작: requestedCode={}, fetchedItems={}, displayLimit={}", requested, fetched, displayLimit);

        int checked = 0;
        int matched = 0;
        int matchedTopKOnly = 0;
        int classifyEmpty = 0;
        int mismatch = 0;

        List<String> emptySamples = new ArrayList<>();
        List<String> mismatchSamples = new ArrayList<>();

        LinkedHashMap<String, JobRecruitItem> matchedByKey = new LinkedHashMap<>();
        List<JobRecruitItem> items = jobkorea.wanted();
        for (int offset = 0; offset < items.size(); offset += JOB_RECLASSIFY_BATCH_SIZE) {
            if (matchedByKey.size() >= displayLimit) break;

            int end = Math.min(items.size(), offset + JOB_RECLASSIFY_BATCH_SIZE);
            List<JobRecruitItem> batch = items.subList(offset, end);

        List<CompletableFuture<List<JobRecruitReclassificationService.JobStandardClassification>>> futures = new ArrayList<>(batch.size());
        for (JobRecruitItem item : batch) {
            if (item == null) {
                futures.add(CompletableFuture.failedFuture(new IllegalArgumentException("잡코리아 재분류 실패: item이 null입니다.")));
                continue;
            }
            futures.add(CompletableFuture.supplyAsync(() -> jobRecruitReclassificationService.classifyTopK(item), jobReclassifyExecutor));
        }

            for (int i = 0; i < batch.size(); i++) {
                if (matchedByKey.size() >= displayLimit) break;

                JobRecruitItem item = batch.get(i);
                if (item == null) continue;
                checked++;

                List<JobRecruitReclassificationService.JobStandardClassification> topK;
                CompletableFuture<List<JobRecruitReclassificationService.JobStandardClassification>> future = futures.get(i);
                try {
                    topK = future.get(JOB_RECLASSIFY_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // 왜: 폴백(빈 결과)로 넘어가면 원인이 가려집니다. 실패는 즉시 노출합니다.
                    future.cancel(true);
                    log.error(
                        "통합(ALL) 잡코리아 재분류 실패: requestedCode={}, provider={}, id={}, title={}",
                        requested,
                        item.infoSvc(),
                        item.wantedAuthNo(),
                        item.title(),
                        e
                    );
                    throw new IllegalStateException("통합(ALL) 잡코리아 재분류 실패: requestedCode=" + requested, e);
                }

                if (topK == null || topK.isEmpty()) {
                    log.error(
                        "통합(ALL) 잡코리아 재분류 결과가 비었습니다: requestedCode={}, provider={}, id={}, title={}",
                        requested,
                        item.infoSvc(),
                        item.wantedAuthNo(),
                        item.title()
                    );
                    throw new IllegalStateException("통합(ALL) 잡코리아 재분류 실패: 결과 0건. requestedCode=" + requested);
                }

                JobRecruitReclassificationService.JobStandardClassification top1 = topK.get(0);

                boolean isLeafRequested = looksLikeWork24SmallOccupationCode(requested);

                boolean isMatchTop1 = requested.equals(top1.standardCode())
                    || requested.equals(top1.depth2Code())
                    || requested.equals(top1.depth1Code());

                boolean isMatchAny = isMatchTop1;
                int matchIndex = isMatchTop1 ? 0 : -1;
                double matchScore = isMatchTop1 ? top1.score() : 0.0;

                if (!isMatchAny && topK.size() > 1) {
                    for (int j = 1; j < topK.size(); j++) {
                        JobRecruitReclassificationService.JobStandardClassification c = topK.get(j);
                        if (c == null) continue;
                        if (requested.equals(c.standardCode())
                            || requested.equals(c.depth2Code())
                            || requested.equals(c.depth1Code())) {
                            isMatchAny = true;
                            matchIndex = j;
                            matchScore = c.score();
                            break;
                        }
                    }
                }

                if (isLeafRequested && isMatchAny && !isMatchTop1) {
                    // 왜: 소분류(6자리) 선택은 "topK에 우연히 끼는" 노이즈가 생길 수 있어,
                    //      top1이 아닐 때는 점수/순위 조건을 추가로 걸어 품질을 안정시킵니다.
                    // - matchIndex: 0(=top1)이면 이미 통과
                    // - matchScore: 절대 점수 하한
                    // - delta: top1과의 점수 차이가 너무 크면 제외(=확신 부족)
                    int maxRank = 2; // top3까지만 허용
                    double minScore = 0.85;
                    double maxDelta = 0.03;
                    double delta = Math.max(0.0, top1.score() - matchScore);

                    if (matchIndex < 0 || matchIndex > maxRank || matchScore < minScore || delta > maxDelta) {
                        isMatchAny = false;
                    }
                }

                if (isMatchAny) {
                    String key = buildRecruitItemKey(item);
                    if (!matchedByKey.containsKey(key)) {
                        matchedByKey.put(key, item);
                        matched++;
                        if (!isMatchTop1) matchedTopKOnly++;
                    }
                    continue;
                }

                mismatch++;
                if (mismatchSamples.size() < 5) {
                    mismatchSamples.add(safeMismatchSample(item, topK));
                }
            }
        }

        List<JobRecruitItem> out = new ArrayList<>(matchedByKey.values());

        log.info(
            "통합(ALL) 잡코리아 재분류 필터 결과: requestedCode={}, checked={}, matched={}, matchedTopKOnly={}, classifyEmpty={}, mismatch={}, returned={}",
            requested,
            checked,
            matched,
            matchedTopKOnly,
            classifyEmpty,
            mismatch,
            out.size()
        );
        if (!emptySamples.isEmpty()) {
            log.info("통합(ALL) 재분류 실패 샘플(최대 5): {}", emptySamples);
        }
        if (!mismatchSamples.isEmpty()) {
            log.info("통합(ALL) 재분류 불일치 샘플(최대 5): {}", mismatchSamples);
        }

        // 왜: 재분류 후 남은 건수만 화면에 주는 것이, "총 건수" 오해를 줄입니다.
        return new JobRecruitListResponse(out.size(), criteria == null ? jobkorea.startPage() : criteria.startPage(), displayLimit, out);
    }

    private String safeSampleTitle(JobRecruitItem item) {
        if (item == null) return "";
        String title = item.title() == null ? "" : item.title().trim();
        if (title.isBlank()) return "(제목없음)";
        return title.length() > 60 ? title.substring(0, 60) + "..." : title;
    }

    private String safeMismatchSample(JobRecruitItem item, List<JobRecruitReclassificationService.JobStandardClassification> topK) {
        String title = safeSampleTitle(item);
        if (topK == null || topK.isEmpty()) return title + " | (분류정보없음)";

        JobRecruitReclassificationService.JobStandardClassification top1 = topK.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append(title)
            .append(" | top1=")
            .append(nullToDash(top1.depth1Code()))
            .append("/")
            .append(nullToDash(top1.depth2Code()))
            .append("/")
            .append(nullToDash(top1.standardCode()))
            .append(" score=")
            .append(String.format("%.4f", top1.score()));

        // 왜: 소분류 선택에서 "top1은 다르지만 topK에는 있는지"가 중요한 디버깅 포인트라서, 상위 3개만 붙입니다.
        int max = Math.min(3, topK.size());
        sb.append(" | top").append(max).append("=");
        for (int i = 0; i < max; i++) {
            JobRecruitReclassificationService.JobStandardClassification c = topK.get(i);
            if (c == null) continue;
            if (i > 0) sb.append(", ");
            sb.append(nullToDash(c.standardCode())).append(":").append(String.format("%.3f", c.score()));
        }
        return sb.toString();
    }

    private String buildRecruitItemKey(JobRecruitItem item) {
        if (item == null) return "";
        String provider = item.infoSvc() == null ? "" : item.infoSvc().trim().toUpperCase();
        String id = item.wantedAuthNo() == null ? "" : item.wantedAuthNo().trim();
        if (!provider.isBlank() && !id.isBlank()) return provider + ":" + id;
        String title = item.title() == null ? "" : item.title().trim();
        String company = item.company() == null ? "" : item.company().trim();
        return provider + ":" + title + ":" + company;
    }

    private static List<JobRecruitItem> limit(List<JobRecruitItem> items, int limit) {
        if (items == null || items.isEmpty()) return List.of();
        if (limit <= 0) return List.of();
        if (items.size() <= limit) return items;
        return items.subList(0, limit);
    }

    private static boolean looksLikeWork24SmallOccupationCode(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.isBlank()) return false;
        return WORK24_SMALL_OCCUPATION_CODE.matcher(value).matches();
    }

    private JobRecruitListResponse fetchFromProvider(JobRecruitSearchCriteria criteria, Provider provider) {
        return switch (provider) {
            case WORK24 -> work24Client.fetchRecruitList(criteria);
            case JOBKOREA -> jobKoreaClient.fetchRecruitList(criteria);
            case ALL -> throw new IllegalArgumentException("ALL은 개별 호출 없이 병합으로만 처리합니다.");
        };
    }

    private boolean isCacheEnabled(Provider provider) {
        return switch (provider) {
            case WORK24 -> work24Properties.getCache().isEnabled();
            case JOBKOREA -> jobKoreaProperties.getCache().isEnabled();
            case ALL -> work24Properties.getCache().isEnabled() || jobKoreaProperties.getCache().isEnabled();
        };
    }

    private long resolveTtlMinutes(Provider provider) {
        return switch (provider) {
            case WORK24 -> work24Properties.getCache().getTtlMinutes();
            case JOBKOREA -> jobKoreaProperties.getCache().getTtlMinutes();
            // 왜: 통합 캐시는 둘 중 더 짧은 TTL을 따라가야 "오래된 데이터"가 오래 남는 문제를 줄일 수 있습니다.
            case ALL -> Math.min(work24Properties.getCache().getTtlMinutes(), jobKoreaProperties.getCache().getTtlMinutes());
        };
    }

    private Integer parseRegionCode(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class JobThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        private JobThreadFactory(String prefix) {
            this.prefix = prefix == null ? "" : prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }

    public enum Provider {
        // 왜: Work24 단일/잡코리아 단일/통합 조회를 선택할 수 있게 합니다.
        WORK24,
        JOBKOREA,
        ALL;

        public static Provider from(String raw) {
            if (raw == null || raw.isBlank()) return WORK24;
            String value = raw.trim().toUpperCase();
            return switch (value) {
                case "JOBKOREA" -> JOBKOREA;
                case "ALL" -> ALL;
                default -> WORK24;
            };
        }
    }

    public enum CachePolicy {
        // 왜: 화면/운영 상황에 따라 캐시 우선, 실시간 강제, 캐시만 사용을 구분합니다.
        PREFER_CACHE,
        FORCE_LIVE,
        CACHE_ONLY;

        public static CachePolicy from(String raw) {
            if (raw == null || raw.isBlank()) return PREFER_CACHE;
            String value = raw.trim().toUpperCase();
            return switch (value) {
                case "FORCE_LIVE" -> FORCE_LIVE;
                case "CACHE_ONLY" -> CACHE_ONLY;
                default -> PREFER_CACHE;
            };
        }
    }

}
