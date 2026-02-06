package kr.go.growailms.statistics.kosis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KosisPopulationRepository extends JpaRepository<KosisPopulation, KosisPopulationId> {
    // 왜: 동일 조건(year/age_type/gender)으로 조회가 잦아서 메소드 쿼리로 고정합니다.

    List<KosisPopulation> findByIdYearAndIdAgeTypeAndIdGenderOrderByIdAdmCdAsc(String year, String ageType, String gender);

    List<KosisPopulation> findByIdYearAndIdAgeTypeAndIdGenderAndIdAdmCd(String year, String ageType, String gender, String admCd);
}
