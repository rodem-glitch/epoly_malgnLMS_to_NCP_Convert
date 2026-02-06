package kr.go.growailms.job.classify;

import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.job.code.JobKoreaCodeCatalog;
import kr.go.growailms.job.code.JobKoreaCodeCatalog.CodeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class JobKoreaOccupationVectorIndexService {
    // 왜: Work24(KEIS) 표준코드 ↔ 잡코리아(rpcd) 직접 매핑은 품질/유지보수가 어렵습니다.
    //      그래서 "잡코리아 업직종 코드명" 자체를 벡터로 색인해두고,
    //      표준 직무명과 유사한 잡코리아 코드를 자동으로 추천(=자동 매핑)할 수 있게 합니다.

    private static final Logger log = LoggerFactory.getLogger(JobKoreaOccupationVectorIndexService.class);

    private static final String FILTER_EXPRESSION = "source == 'jobkorea_code'";

    private final VectorStoreService vectorStoreService;

    private volatile boolean indexAttempted = false;
    private volatile boolean indexed = false;

    public JobKoreaOccupationVectorIndexService(VectorStoreService vectorStoreService) {
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
    }

    public synchronized void ensureIndexed() {
        if (indexed) return;
        if (indexAttempted) return;
        indexAttempted = true;

        try {
            if (hasAnyIndexedDocuments()) {
                indexed = true;
                return;
            }
        } catch (Exception e) {
            // 왜: 로컬/운영에서 벡터 DB 설정이 덜 되어 있어도 서버 전체가 죽지 않게 합니다.
            log.warn("잡코리아 업직종 벡터 색인 여부 확인 실패(일단 미색인으로 진행)", e);
        }

        List<CodeItem> rbcdList = JobKoreaCodeCatalog.rbcd();
        if (rbcdList.isEmpty()) {
            log.warn("잡코리아 업직종 코드표(rbcd)가 비어 있어 벡터 색인을 건너뜁니다.");
            return;
        }

        int total = 0;
        for (CodeItem rbcd : rbcdList) {
            List<CodeItem> rpcd = JobKoreaCodeCatalog.rpcd(rbcd.code());
            total += 1; // rbcd 1건
            total += rpcd == null ? 0 : rpcd.size();
        }

        log.info("잡코리아 업직종 벡터 색인 시작: 대상 {}건(rbcd+rpcd)", total);

        int indexedCount = 0;
        for (CodeItem rbcd : rbcdList) {
            if (rbcd == null) continue;

            // rbcd(대분류)
            String rbcdId = "jobkorea:rbcd:" + rbcd.code();
            vectorStoreService.upsertText(rbcdId, buildRbcdText(rbcd), buildRbcdMeta(rbcd));
            indexedCount++;

            // rpcd(소분류)
            List<CodeItem> rpcdList = JobKoreaCodeCatalog.rpcd(rbcd.code());
            if (rpcdList != null) {
                for (CodeItem rpcd : rpcdList) {
                    if (rpcd == null) continue;
                    String rpcdId = "jobkorea:rpcd:" + rpcd.code();
                    vectorStoreService.upsertText(rpcdId, buildRpcdText(rbcd, rpcd), buildRpcdMeta(rbcd, rpcd));
                    indexedCount++;
                }
            }

            if (indexedCount % 100 == 0) {
                log.info("잡코리아 업직종 벡터 색인 진행: {} / {}", indexedCount, total);
            }
        }

        indexed = true;
        log.info("잡코리아 업직종 벡터 색인 완료: {}건", indexedCount);
    }

    private boolean hasAnyIndexedDocuments() {
        return !vectorStoreService.similaritySearch("개발자", 1, 0.0, FILTER_EXPRESSION).isEmpty();
    }

    private Map<String, Object> buildRbcdMeta(CodeItem rbcd) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "jobkorea_code");
        meta.put("code_type", "rbcd");
        meta.put("code", safe(rbcd.code()));
        meta.put("name", safe(rbcd.name()));
        meta.put("indexed_at", Instant.now().getEpochSecond());
        return meta;
    }

    private Map<String, Object> buildRpcdMeta(CodeItem rbcd, CodeItem rpcd) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "jobkorea_code");
        meta.put("code_type", "rpcd");
        meta.put("code", safe(rpcd.code()));
        meta.put("name", safe(rpcd.name()));
        meta.put("parent_rbcd", safe(rbcd.code()));
        meta.put("parent_rbcd_name", safe(rbcd.name()));
        meta.put("indexed_at", Instant.now().getEpochSecond());
        return meta;
    }

    private String buildRbcdText(CodeItem rbcd) {
        // 왜: 표준 코드명과의 의미 비교가 목적이라 "대분류"라는 힌트를 같이 넣습니다.
        return ("잡코리아 업직종 대분류: " + safe(rbcd.name())).trim();
    }

    private String buildRpcdText(CodeItem rbcd, CodeItem rpcd) {
        // 왜: rpcd만 단독으로 임베딩하면 문맥이 약할 수 있어, 부모 대분류명을 같이 넣습니다.
        StringBuilder sb = new StringBuilder();
        if (rbcd != null && rbcd.name() != null && !rbcd.name().isBlank()) {
            sb.append("잡코리아 업직종 대분류: ").append(rbcd.name().trim()).append("\n");
        }
        if (rpcd != null && rpcd.name() != null && !rpcd.name().isBlank()) {
            sb.append("잡코리아 업직종 소분류: ").append(rpcd.name().trim()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String safe(String value) {
        if (value == null) return "";
        String v = value.trim();
        return v.isBlank() ? "" : v;
    }
}

