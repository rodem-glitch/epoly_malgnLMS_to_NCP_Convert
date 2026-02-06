-- 왜: JPA ddl-auto가 none이라서, 운영/개발 DB에 테이블을 먼저 만들어야 합니다.
CREATE TABLE TB_RECO_CONTENT (
  id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  -- 왜: 이 프로젝트에서는 Kollus 미디어 콘텐츠 키(문자열)를 lesson_id로 저장해 "영상 1개 = 요약 1개"를 매핑합니다.
  -- - 기존 LMS의 숫자 lesson_id를 쓰는 경우가 있다면, 운영 DB에서는 별도 컬럼을 추가하거나 타입을 유지해 주세요.
  lesson_id   VARCHAR(100) NULL,
  category_nm VARCHAR(100) NOT NULL,
  title       VARCHAR(200) NOT NULL,
  summary     TEXT NOT NULL,
  keywords    VARCHAR(500) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_lesson_id (lesson_id),
  KEY idx_lesson_id (lesson_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
