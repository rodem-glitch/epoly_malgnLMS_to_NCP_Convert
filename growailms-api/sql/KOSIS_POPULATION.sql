-- 왜: KOSIS 인구 통계를 "매번 외부 호출"하지 않도록 DB에 캐시하기 위한 테이블입니다.
-- 사용처: `kr.polytech.lms.statistics.kosis.persistence.KosisPopulation`

CREATE TABLE IF NOT EXISTS kosis_population (
    `year`      VARCHAR(4)   NOT NULL,
    `age_type`  VARCHAR(8)   NOT NULL,
    `gender`    VARCHAR(2)   NOT NULL,
    `adm_cd`    VARCHAR(20)  NOT NULL,
    `adm_nm`    VARCHAR(200) NOT NULL,
    `population` BIGINT      NOT NULL,
    PRIMARY KEY (`year`, `age_type`, `gender`, `adm_cd`)
);

