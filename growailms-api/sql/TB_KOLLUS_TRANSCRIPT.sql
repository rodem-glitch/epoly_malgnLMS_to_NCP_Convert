-- 왜: 전사/요약 파이프라인은 "여러 번 재시도 + 부분 완료"가 자주 발생합니다.
-- 그래서 콘텐츠 키(media_content_key) 기준으로 상태/에러/결과를 저장할 별도 테이블이 필요합니다.
CREATE TABLE TB_KOLLUS_TRANSCRIPT (
  id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
  site_id           INT NOT NULL,
  channel_key       VARCHAR(100) NOT NULL,
  media_content_key VARCHAR(100) NOT NULL,
  title             VARCHAR(255) DEFAULT '',
  transcript_text   LONGTEXT NULL,
  status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  retry_count       INT NOT NULL DEFAULT 0,
  last_error        TEXT NULL,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  transcribed_at    DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY ux_site_media (site_id, media_content_key),
  KEY idx_status (status),
  KEY idx_channel (channel_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

