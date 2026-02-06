-- Work24(6자리 소분류) ↔ 잡코리아 업직종(rbcd/rpcd) 매핑 테이블
-- 왜: 통합(ALL) 검색 화면은 Work24 직종코드를 기준으로 필터링하지만,
--      잡코리아는 별도 코드체계(rbcd/rpcd)를 쓰므로 DB에서 매핑을 조회해 변환합니다.

CREATE TABLE IF NOT EXISTS job_work24_jobkorea_occupation_map (
    work24_code CHAR(6) NOT NULL,
    jobkorea_code VARCHAR(10) NOT NULL,
    PRIMARY KEY (work24_code, jobkorea_code),
    KEY idx_jobkorea_code (jobkorea_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

