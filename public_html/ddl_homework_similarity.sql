-- 왜 필요한가:
-- 과제 제출물(제목/본문/첨부)의 학생 간 일치율 분석 결과를 저장/조회하기 위한 테이블입니다.
-- 정규/비정규 모두 course_id + homework_id 기준으로 동일하게 처리합니다.

CREATE TABLE IF NOT EXISTS LM_HOMEWORK_SIMILARITY_RUN (
  id INT NOT NULL,
  site_id INT NOT NULL,
  course_id INT NOT NULL,
  homework_id INT NOT NULL,
  run_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/AUTO_SUBMIT/AUTO_CANCEL/AUTO_FILE',
  threshold_score DECIMAL(5,2) NOT NULL DEFAULT 70.00 COMMENT '저장 임계치(점수 이상만 결과 저장)',
  pair_total INT NOT NULL DEFAULT 0 COMMENT '비교한 전체 쌍 수',
  pair_saved INT NOT NULL DEFAULT 0 COMMENT '저장된 쌍 수',
  requested_user_id INT NOT NULL DEFAULT 0 COMMENT '실행 요청자(자동화는 이벤트 발생 사용자)',
  started_at VARCHAR(14) NOT NULL,
  ended_at VARCHAR(14) NOT NULL DEFAULT '',
  message VARCHAR(255) DEFAULT '' COMMENT '실행 요약/오류 메시지',
  status INT NOT NULL DEFAULT 0 COMMENT '0=진행중, 1=성공, -1=실패',
  reg_date VARCHAR(14) NOT NULL,
  mod_date VARCHAR(14) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_hsr_site_course_homework_status (site_id, course_id, homework_id, status),
  KEY idx_hsr_site_homework_reg (site_id, homework_id, reg_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='과제 제출 유사도 분석 실행 이력';

CREATE TABLE IF NOT EXISTS LM_HOMEWORK_SIMILARITY_RESULT (
  id INT NOT NULL,
  run_id INT NOT NULL,
  site_id INT NOT NULL,
  course_id INT NOT NULL,
  homework_id INT NOT NULL,
  left_course_user_id INT NOT NULL,
  right_course_user_id INT NOT NULL,
  subject_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '제목 일치율',
  content_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '본문 일치율',
  file_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '첨부 일치율',
  total_score DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '최종 일치율',
  reason_json TEXT COMMENT '계산 근거(토큰수/교집합 등)',
  reg_date VARCHAR(14) NOT NULL,
  mod_date VARCHAR(14) NOT NULL,
  status INT NOT NULL DEFAULT 1 COMMENT '1=정상, 0=중지, -1=삭제',
  PRIMARY KEY (id),
  UNIQUE KEY uq_hwsr_pair (homework_id, left_course_user_id, right_course_user_id),
  KEY idx_hwsr_site_course_homework_score (site_id, course_id, homework_id, total_score),
  KEY idx_hwsr_run (run_id),
  KEY idx_hwsr_site_homework_status (site_id, homework_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='과제 제출 유사도 분석 결과';

-- 왜: MalgnLMS는 다수 테이블에서 tb_sequence 기반으로 PK를 발급하므로 신규 테이블도 시퀀스를 등록합니다.
INSERT INTO tb_sequence (id, seq) VALUES ('LM_HOMEWORK_SIMILARITY_RUN', 0)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO tb_sequence (id, seq) VALUES ('LM_HOMEWORK_SIMILARITY_RESULT', 0)
ON DUPLICATE KEY UPDATE id = id;

