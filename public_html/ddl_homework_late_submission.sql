-- 왜 필요한가:
-- 과제별 "지각 제출 허용"과 "지각 제출 감점(%)"을 저장해,
-- 과제 수정 시 변경한 설정이 다음 조회/수정 화면에도 그대로 반영되게 하기 위한 컬럼 추가

ALTER TABLE LM_HOMEWORK
  ADD COLUMN allow_late_submission_yn VARCHAR(1) NOT NULL DEFAULT 'N' COMMENT '지각제출허용여부(Y/N)' AFTER submit_file_exts,
  ADD COLUMN late_penalty INT NOT NULL DEFAULT 0 COMMENT '지각제출감점률(0~100)' AFTER allow_late_submission_yn;
