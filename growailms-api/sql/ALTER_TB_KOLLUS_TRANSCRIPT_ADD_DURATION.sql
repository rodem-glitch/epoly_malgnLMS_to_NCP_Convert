-- 왜: 영상 길이 정보를 DB에 저장하면 Gemini API 비용 분석, 
-- 영상 길이 기반 필터링 등 다양한 활용이 가능합니다.
-- 기존 TB_KOLLUS_TRANSCRIPT 테이블에 컬럼을 추가하여 전사 파이프라인과 통합합니다.

-- Step 1: 컬럼 추가
ALTER TABLE TB_KOLLUS_TRANSCRIPT
  ADD COLUMN duration_seconds INT UNSIGNED NULL COMMENT '영상 길이(초)' AFTER title;

-- Step 2: 기존 데이터 백필 (선택사항)
-- 이 쿼리는 애플리케이션에서 Kollus API를 호출하여 업데이트해야 합니다.
-- UPDATE TB_KOLLUS_TRANSCRIPT SET duration_seconds = ? WHERE media_content_key = ?;

-- Step 3: 인덱스 추가 (필요시)
-- ALTER TABLE TB_KOLLUS_TRANSCRIPT ADD INDEX idx_duration (duration_seconds);
