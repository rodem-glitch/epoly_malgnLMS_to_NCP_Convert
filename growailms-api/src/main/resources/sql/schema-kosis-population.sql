-- 왜: KOSIS 인구 통계를 DB에 캐시하기 위한 테이블을 로컬 환경에서 자동 생성합니다.
-- 주의: 운영에서는 DBA/마이그레이션 도구로 DDL을 관리하는 것을 권장드립니다.

CREATE TABLE IF NOT EXISTS kosis_population (
    `year`      VARCHAR(4)   NOT NULL,
    `age_type`  VARCHAR(8)   NOT NULL,
    `gender`    VARCHAR(2)   NOT NULL,
    `adm_cd`    VARCHAR(20)  NOT NULL,
    `adm_nm`    VARCHAR(200) NOT NULL,
    `population` BIGINT      NOT NULL,
    PRIMARY KEY (`year`, `age_type`, `gender`, `adm_cd`)
);

-- 왜: 산업(사업체수) 통계를 DB에 캐시하기 위한 테이블입니다.
-- - 연도/행정구역/산업코드 조합으로 조회가 반복되므로, 외부 API 호출을 최소화합니다.
CREATE TABLE IF NOT EXISTS sgis_company (
    `year`      VARCHAR(4)   NOT NULL,
    `adm_cd`    VARCHAR(20)  NOT NULL,
    `class_code` VARCHAR(20) NOT NULL,
    `corp_cnt`  BIGINT       NULL,
    `tot_worker` BIGINT      NULL,
    PRIMARY KEY (`year`, `adm_cd`, `class_code`)
);
