-- 왜 필요한가:
-- 과제별로 학생 제출 첨부파일 허용 형식을 옵션(프리셋/직접입력)으로 저장하기 위한 컬럼 추가

ALTER TABLE LM_HOMEWORK
  ADD COLUMN submit_file_ext_mode VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT '제출첨부허용형식모드(ALL/DOC/IMAGE/ARCHIVE/AUDIO/CUSTOM)' AFTER homework_file,
  ADD COLUMN submit_file_exts VARCHAR(255) DEFAULT '' COMMENT '제출첨부허용확장자(|구분, CUSTOM 모드에서 사용)' AFTER submit_file_ext_mode;

