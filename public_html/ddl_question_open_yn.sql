-- 왜 필요한가:
-- 교수자가 만든 문제를 기본 공유하되, 공개/비공개를 직접 선택할 수 있도록 문제 가시성 컬럼을 추가합니다.
-- 기본값은 공유 정책에 맞춰 공개(Y)로 설정합니다.

ALTER TABLE LM_QUESTION
  ADD COLUMN open_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT '문제 공개여부(Y=공개, N=비공개)' AFTER manager_id;

