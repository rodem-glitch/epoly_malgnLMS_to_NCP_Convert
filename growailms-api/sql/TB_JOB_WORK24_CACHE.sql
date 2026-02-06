-- 채용 캐시 테이블 (Work24/잡코리아 공통, 기존 DB의 regioncode/occupationcode는 그대로 사용)

CREATE TABLE IF NOT EXISTS job_recruit_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_key VARCHAR(200) NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'WORK24',
    region_code VARCHAR(50),
    occupation_code VARCHAR(50),
    start_page INT NOT NULL,
    `display` INT NOT NULL,
    total INT NOT NULL,
    payload_json LONGTEXT NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_job_recruit_cache_query (provider, query_key)
);
