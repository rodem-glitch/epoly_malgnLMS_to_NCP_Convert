-- 왜 필요한가:
-- 과제 템플릿 탭에서 교수자가 만든 템플릿을 브라우저 localStorage가 아닌 서버 DB에 저장하기 위함입니다.
-- 이렇게 해야 기기/브라우저가 바뀌어도 같은 템플릿을 다시 사용할 수 있습니다.

CREATE TABLE IF NOT EXISTS LM_HOMEWORK_TEMPLATE (
  id INT NOT NULL,
  site_id INT NOT NULL,
  manager_id INT NOT NULL,
  template_nm VARCHAR(200) NOT NULL COMMENT '템플릿 제목',
  description TEXT COMMENT '템플릿 설명',
  total_score INT NOT NULL DEFAULT 100 COMMENT '기본 배점',
  submission_type VARCHAR(10) NOT NULL DEFAULT 'file' COMMENT '제출방식(file/text/both)',
  file_types VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '허용 확장자(.pdf,.docx ...)',
  max_file_size INT NOT NULL DEFAULT 10 COMMENT '최대 파일 크기(MB)',
  allow_late_submission_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT '지각 제출 허용 여부(Y/N)',
  late_penalty INT NOT NULL DEFAULT 0 COMMENT '지각 감점률(%)',
  reg_date VARCHAR(14) NOT NULL,
  mod_date VARCHAR(14) NOT NULL,
  status INT NOT NULL DEFAULT 1 COMMENT '1=정상, 0=중지, -1=삭제',
  PRIMARY KEY (id),
  KEY idx_ht_site_manager_status (site_id, manager_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='교수자 과제 템플릿';

