package kr.go.growailms.statistics.kosis.service;

import kr.go.growailms.statistics.kosis.client.KosisClient;
import kr.go.growailms.statistics.kosis.client.dto.KosisPopulationRow;
import kr.go.growailms.statistics.kosis.persistence.KosisPopulation;
import kr.go.growailms.statistics.kosis.persistence.KosisPopulationId;
import kr.go.growailms.statistics.kosis.persistence.KosisPopulationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Year;
import java.util.Collection;
import java.util.List;

@Service
public class KosisStatisticsService {
    // 왜: 컨트롤러는 요청/응답에 집중하고, 비즈니스 규칙(기본값/검증)은 서비스로 분리합니다.

    private final KosisClient kosisClient;
    private final KosisPopulationRepository kosisPopulationRepository;

    public KosisStatisticsService(
            KosisClient kosisClient,
            KosisPopulationRepository kosisPopulationRepository
    ) {
        this.kosisClient = kosisClient;
        this.kosisPopulationRepository = kosisPopulationRepository;
    }

    public List<KosisPopulationRow> getPopulation(String year, String ageType, String gender) throws IOException {
        return getPopulation(year, ageType, gender, null);
    }

    public List<KosisPopulationRow> getPopulation(String year, String ageType, String gender, String admCd) throws IOException {
        String resolvedYear = resolveYear(year);
        String resolvedAgeType = resolveAgeType(ageType);
        String resolvedGender = resolveGender(gender);
        String resolvedAdmCd = resolveAdmCd(admCd);

        List<KosisPopulationRow> cached = findPopulationFromCache(resolvedYear, resolvedAgeType, resolvedGender, resolvedAdmCd);
        if (!cached.isEmpty()) {
            return cached;
        }

        List<KosisPopulationRow> fetched = kosisClient.fetchPopulation(resolvedYear, resolvedAgeType, resolvedGender, resolvedAdmCd);
        savePopulationToCache(resolvedYear, resolvedAgeType, resolvedGender, fetched);
        return fetched;
    }

    private List<KosisPopulationRow> findPopulationFromCache(String year, String ageType, String gender, String admCd) {
        // 왜: 레거시와 동일하게 "DB에 있으면 DB 우선"으로 외부 API 호출을 줄입니다.
        List<KosisPopulation> entities = (StringUtils.hasText(admCd))
                ? kosisPopulationRepository.findByIdYearAndIdAgeTypeAndIdGenderAndIdAdmCd(year, ageType, gender, admCd)
                : kosisPopulationRepository.findByIdYearAndIdAgeTypeAndIdGenderOrderByIdAdmCdAsc(year, ageType, gender);
        return entities.stream().map(this::toRow).toList();
    }

    private void savePopulationToCache(String year, String ageType, String gender, Collection<KosisPopulationRow> rows) {
        // 왜: 결과가 비어있을 때는 저장 의미가 없고, 오히려 "빈 캐시"가 되어 다음에 다시 조회가 막힐 수 있습니다.
        if (rows == null || rows.isEmpty()) {
            return;
        }

        List<KosisPopulation> entities = rows.stream()
                .map(row -> toEntity(year, ageType, gender, row))
                .toList();

        kosisPopulationRepository.saveAll(entities);
    }

    private KosisPopulation toEntity(String year, String ageType, String gender, KosisPopulationRow row) {
        // 왜: 외부 응답의 필드가 비어있으면 캐시 키가 깨지므로 저장 전에 방어합니다.
        String admCd = (row.getAdmCd() == null) ? "" : row.getAdmCd().trim();
        String admNm = (row.getAdmNm() == null) ? "" : row.getAdmNm().trim();
        long population = row.getPopulation();

        if (!StringUtils.hasText(admCd) || !StringUtils.hasText(admNm)) {
            throw new IllegalStateException("KOSIS 응답에 행정구역 코드/명이 비어 있습니다. 캐시 저장이 불가능합니다.");
        }

        return new KosisPopulation(new KosisPopulationId(year, ageType, gender, admCd), admNm, population);
    }

    private KosisPopulationRow toRow(KosisPopulation entity) {
        KosisPopulationRow row = new KosisPopulationRow();
        row.setAdmCd(entity.getId().getAdmCd());
        row.setAdmNm(entity.getAdmNm());
        row.setPopulation(entity.getPopulation());
        return row;
    }

    private String resolveYear(String year) {
        // 왜: 기본 화면 기본값을 2024로 고정해 혼선을 줄입니다.
        if (!StringUtils.hasText(year)) {
            return "2024";
        }
        return year.trim();
    }

    private String resolveAgeType(String ageType) {
        // 왜: 레거시 기본값(20대=32)을 그대로 사용해 화면 테스트를 쉽게 합니다.
        if (!StringUtils.hasText(ageType)) {
            return "32";
        }
        return ageType.trim();
    }

    private String resolveGender(String gender) {
        // 왜: 레거시 기본값(전체=0)을 그대로 사용해 화면 테스트를 쉽게 합니다.
        if (!StringUtils.hasText(gender)) {
            return "0";
        }
        return gender.trim();
    }

    private String resolveAdmCd(String admCd) {
        // 왜: 행정구역을 고정하면 응답이 1행만 내려와서 "연령대별 분포" 계산이 훨씬 빨라집니다.
        if (!StringUtils.hasText(admCd)) {
            return null;
        }
        return admCd.trim();
    }
}
