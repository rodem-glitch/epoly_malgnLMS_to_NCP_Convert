-- 지역/직종 코드 테이블 (Work24 화면 셀렉트용)

CREATE TABLE IF NOT EXISTS regioncode (
    idx INT AUTO_INCREMENT PRIMARY KEY,
    depth1 VARCHAR(100) NOT NULL,
    depth2 VARCHAR(100) NOT NULL,
    depth3 VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS occupationcode (
    idx INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    parent_code VARCHAR(50),
    depth1 VARCHAR(100) NOT NULL,
    depth2 VARCHAR(100) NOT NULL,
    depth3 VARCHAR(100) NOT NULL
);
