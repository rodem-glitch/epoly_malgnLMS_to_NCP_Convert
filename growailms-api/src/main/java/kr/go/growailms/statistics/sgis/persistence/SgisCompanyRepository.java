package kr.go.growailms.statistics.sgis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SgisCompanyRepository extends JpaRepository<SgisCompany, SgisCompanyId> {
    // 왜: 캐시 테이블이므로 PK 기반 조회(findById)가 대부분입니다.
}

