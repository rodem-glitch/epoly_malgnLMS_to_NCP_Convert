-- 왜: 산업/인구 비교 "최종 계산 결과"를 파라미터 기준으로 캐시해,
--     동일 조회 시 외부 통계 API 호출/비율 재계산을 줄이기 위한 테이블입니다.
-- 주의: 운영에서는 DBA/마이그레이션 도구 기준으로 DDL을 관리하는 것을 권장드립니다.

CREATE TABLE IF NOT EXISTS statistics_dashboard_cache (
    cache_key VARCHAR(128) NOT NULL,
    cache_type VARCHAR(32) NOT NULL,
    campus VARCHAR(255) NULL,
    adm_cd VARCHAR(32) NULL,
    adm_nm VARCHAR(255) NULL,
    stats_year INT NULL,
    payload_json LONGTEXT NOT NULL,
    hit_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (cache_key),
    KEY idx_statistics_dashboard_cache_type_year (cache_type, stats_year)
);

