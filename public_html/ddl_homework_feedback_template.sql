-- 왜 필요한가:
-- 교수자가 과제 피드백에서 자주 쓰는 문구를 템플릿으로 저장/재사용하기 위한 테이블입니다.
-- "5개 제한"은 요구사항이 아니므로 서버에서 개수 제한을 두지 않습니다.

CREATE TABLE IF NOT EXISTS LM_HOMEWORK_FEEDBACK_TEMPLATE (
  id INT NOT NULL,
  site_id INT NOT NULL,
  course_id INT NOT NULL,
  manager_id INT NOT NULL,
  sort INT NOT NULL DEFAULT 0 COMMENT '노출 순서(작을수록 우선)',
  content TEXT COMMENT '피드백 템플릿 본문',
  reg_date VARCHAR(14) NOT NULL,
  mod_date VARCHAR(14) NOT NULL,
  status INT NOT NULL DEFAULT 1 COMMENT '1=정상, 0=중지, -1=삭제',
  PRIMARY KEY (id),
  KEY idx_hft_site_course_manager_status (site_id, course_id, manager_id, status),
  KEY idx_hft_site_manager_status (site_id, manager_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='과제 피드백 템플릿';
