-- 왜: Kollus 미디어 콘텐츠 키는 숫자가 아니라 문자열인 경우가 많습니다.
-- 기존 TB_RECO_CONTENT.lesson_id가 INT라면 아래처럼 VARCHAR로 바꿔야 "media_content_key"를 그대로 저장할 수 있습니다.
ALTER TABLE TB_RECO_CONTENT
  MODIFY lesson_id VARCHAR(100) NULL;

-- (선택) 동일 영상에 대해 요약이 중복 저장되는 것을 막고 싶다면 유니크 키를 추가하세요.
-- 주의: 이미 중복 데이터가 있으면 실패합니다. 먼저 중복을 정리한 뒤 실행해 주세요.
-- ALTER TABLE TB_RECO_CONTENT
--   ADD UNIQUE KEY ux_lesson_id (lesson_id);

