package kr.polytech.lms.statistics.sgis.service;

import kr.polytech.lms.statistics.sgis.client.SgisAddressClient;
import kr.polytech.lms.statistics.sgis.client.SgisAddressClient.StageRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SgisAdministrativeCodeService {
    // 왜: 프론트(캠퍼스 매핑)가 보내는 행정구역 코드는 주로 행안부 코드(예: 41=경기, 28=인천)입니다.
    //     그런데 SGIS OpenAPI는 다른 시도 코드 체계(예: 31=경기, 23=인천)를 사용합니다.
    //     그래서 (1) 시도 코드는 변환하고, (2) 시군구는 stage API로 '이름'을 기준으로 매칭해 SGIS cd로 바꿉니다.

    private static final Logger log = LoggerFactory.getLogger(SgisAdministrativeCodeService.class);

    private static final Map<String, String> LOCAL_SIDO_TO_SGIS_SIDO = Map.ofEntries(
            Map.entry("11", "11"), // 서울
            Map.entry("26", "21"), // 부산
            Map.entry("27", "22"), // 대구
            Map.entry("28", "23"), // 인천
            Map.entry("29", "24"), // 광주
            Map.entry("30", "25"), // 대전
            Map.entry("31", "26"), // 울산
            Map.entry("36", "29"), // 세종
            Map.entry("41", "31"), // 경기
            Map.entry("42", "32"), // 강원
            Map.entry("43", "33"), // 충북
            Map.entry("44", "34"), // 충남
            Map.entry("45", "35"), // 전북
            Map.entry("46", "36"), // 전남
            Map.entry("47", "37"), // 경북
            Map.entry("48", "38"), // 경남
            Map.entry("50", "39")  // 제주
    );

    // 왜: 전국(전체) 산업 통계처럼 "시도 단위로 합산"이 필요할 때, SGIS 시도 코드를 안정적으로 제공하기 위함입니다.
    // - Map 순서는 구현에 따라 달라질 수 있어 List로 고정합니다.
    private static final List<String> ALL_SGIS_SIDO_CODES = List.of(
            "11", // 서울
            "21", // 부산
            "22", // 대구
            "23", // 인천
            "24", // 광주
            "25", // 대전
            "26", // 울산
            "29", // 세종
            "31", // 경기
            "32", // 강원
            "33", // 충북
            "34", // 충남
            "35", // 전북
            "36", // 전남
            "37", // 경북
            "38", // 경남
            "39"  // 제주
    );

    private final SgisAddressClient sgisAddressClient;

    // 왜: stage 목록은 자주 바뀌지 않으므로, 시도 단위로 한 번만 가져와 캐시합니다.
    private final ConcurrentMap<String, List<StageRow>> stageCacheBySgisSido = new ConcurrentHashMap<>();

    public SgisAdministrativeCodeService(SgisAddressClient sgisAddressClient) {
        this.sgisAddressClient = sgisAddressClient;
    }

    public Resolution resolveToSgisAdmCd(String requestedAdmCd, String requestedAdmNm) {
        String rawCd = normalize(requestedAdmCd);
        String rawNm = normalize(requestedAdmNm);

        if (!StringUtils.hasText(rawCd) || "전체".equals(rawCd)) {
            // 전국: population API에서는 adm_cd를 보내지 않는 것이 정상 동작입니다.
            return new Resolution(rawCd, rawNm, null, false);
        }

        if (!rawCd.matches("\\d+")) {
            // 숫자 코드가 아니면(예: 기타 문자열) 전국 처리로 돌립니다.
            return new Resolution(rawCd, rawNm, null, true);
        }

        if (rawCd.length() == 2) {
            String sgisSidoCd = mapSgisSidoCode(rawCd);
            return new Resolution(rawCd, rawNm, sgisSidoCd, !rawCd.equals(sgisSidoCd));
        }

        // 5자리 이상(시군구/읍면동 등): 행안부 코드의 앞 2자리(시도)를 SGIS 시도 코드로 변환 후,
        //                    stage API로 full_addr(예: "경기도 성남시 수정구")를 매칭해 SGIS cd를 찾습니다.
        String localSido = rawCd.substring(0, 2);
        String sgisSidoCd = mapSgisSidoCode(localSido);

        if (!StringUtils.hasText(sgisSidoCd)) {
            return new Resolution(rawCd, rawNm, null, true);
        }

        if (!StringUtils.hasText(rawNm) || rawNm.contains("전체")) {
            // 이름 정보가 없으면 시도 단위로만 처리합니다.
            return new Resolution(rawCd, rawNm, sgisSidoCd, true);
        }

        try {
            StageRow matched = findStageByName(sgisSidoCd, rawNm);
            if (matched != null && StringUtils.hasText(matched.cd())) {
                boolean converted = !rawCd.equals(matched.cd());
                return new Resolution(rawCd, rawNm, matched.cd().trim(), converted);
            }
        } catch (Exception e) {
            // 왜: stage API 장애로 전체 통계가 0으로 보이면 사용자 입장에서 더 치명적입니다.
            //     이 경우에는 "시도 단위"로라도 통계를 보여주기 위해 변환만 적용하고 넘어갑니다.
            log.warn("SGIS stage 조회 실패 → 시도 단위로 대체합니다. requestedAdmCd={}, requestedAdmNm={}, sgisSidoCd={}",
                    rawCd, rawNm, sgisSidoCd, e);
        }

        // 이름 매칭 실패: 시도 단위로라도 보여줍니다.
        log.info("SGIS 행정구역 매칭 실패 → 시도 단위로 대체합니다. requestedAdmCd={}, requestedAdmNm={}, sgisSidoCd={}",
                rawCd, rawNm, sgisSidoCd);
        return new Resolution(rawCd, rawNm, sgisSidoCd, true);
    }

    public List<String> getAllSgisSidoCodes() {
        return ALL_SGIS_SIDO_CODES;
    }

    private StageRow findStageByName(String sgisSidoCd, String fullAddr) throws Exception {
        List<StageRow> stages = getOrLoadStages(sgisSidoCd);
        if (stages.isEmpty()) {
            return null;
        }

        String normalizedFullAddr = normalizeSpaces(fullAddr);

        // 1) full_addr 정확히 일치(가장 안전)
        for (StageRow r : stages) {
            if (!StringUtils.hasText(r.fullAddr())) continue;
            if (normalizeSpaces(r.fullAddr()).equals(normalizedFullAddr)) {
                return r;
            }
        }

        // 2) addr_name 일치(예: "서울특별시 강남구" → "강남구", "경상남도 창원시 의창구" → "창원시 의창구")
        String addrNameCandidate = extractAddrNameCandidate(fullAddr);
        if (StringUtils.hasText(addrNameCandidate)) {
            String normalizedAddrName = normalizeSpaces(addrNameCandidate);
            for (StageRow r : stages) {
                if (!StringUtils.hasText(r.addrName())) continue;
                if (normalizeSpaces(r.addrName()).equals(normalizedAddrName)) {
                    return r;
                }
            }
        }

        // 3) 공백 차이/접미 일치까지 허용(최후 수단)
        if (StringUtils.hasText(addrNameCandidate)) {
            String normalizedAddrName = normalizeSpaces(addrNameCandidate);
            for (StageRow r : stages) {
                if (StringUtils.hasText(r.fullAddr()) && normalizeSpaces(r.fullAddr()).endsWith(normalizedAddrName)) {
                    return r;
                }
                if (StringUtils.hasText(r.addrName()) && normalizeSpaces(r.addrName()).endsWith(normalizedAddrName)) {
                    return r;
                }
            }
        }

        return null;
    }

    private List<StageRow> getOrLoadStages(String sgisSidoCd) throws Exception {
        List<StageRow> cached = stageCacheBySgisSido.get(sgisSidoCd);
        if (cached != null) {
            return cached;
        }

        List<StageRow> fetched = sgisAddressClient.fetchStages(sgisSidoCd);
        stageCacheBySgisSido.put(sgisSidoCd, fetched);
        return fetched;
    }

    private String mapSgisSidoCode(String localSidoCode) {
        String trimmed = normalize(localSidoCode);
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return LOCAL_SIDO_TO_SGIS_SIDO.getOrDefault(trimmed, trimmed);
    }

    private String extractAddrNameCandidate(String fullAddr) {
        if (!StringUtils.hasText(fullAddr)) return null;
        String[] parts = fullAddr.trim().split("\\s+");
        if (parts.length <= 1) {
            return null;
        }
        // 예: ["경상남도","창원시","의창구"] -> "창원시 의창구"
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSpaces(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    public record Resolution(
            String requestedAdmCd,
            String requestedAdmNm,
            String sgisAdmCd,
            boolean mappingFallbackApplied
    ) {
        public Map<String, Object> toDebugMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requestedAdmCd", requestedAdmCd);
            m.put("requestedAdmNm", requestedAdmNm);
            m.put("sgisAdmCd", sgisAdmCd);
            m.put("mappingFallbackApplied", mappingFallbackApplied);
            return m;
        }
    }
}

