-- 학사 과목 운영 데이터 저장용 DDL
-- 왜 필요한가:
-- - 학사 과목은 외부 시스템에서 들어오므로, LMS에서 추가로 관리하는 설정/강의목차/시험/성적을 따로 저장해야 합니다.
-- - LM_POLY_COURSE의 PK(강좌코드+연도+학기+분반+학부/대학원)를 그대로 키로 사용합니다.

-- 1) 학사 과목 설정/목차/시험 저장
CREATE TABLE IF NOT EXISTS `LM_POLY_COURSE_SETTING` (
  `SITE_ID` int NOT NULL COMMENT '사이트 ID',
  `COURSE_CODE` varchar(20) NOT NULL COMMENT '강좌코드',
  `OPEN_YEAR` varchar(10) NOT NULL COMMENT '연도',
  `OPEN_TERM` varchar(10) NOT NULL COMMENT '학기',
  `BUNBAN_CODE` varchar(20) NOT NULL COMMENT '분반코드',
  `GROUP_CODE` varchar(20) NOT NULL COMMENT '학부/대학원 구분',

  `EVAL_JSON` mediumtext COMMENT '학사 평가/수료 기준(JSON)',
  `CURRICULUM_JSON` mediumtext COMMENT '학사 강의목차(JSON)',
  `EXAMS_JSON` mediumtext COMMENT '학사 시험 설정(JSON)',

  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  `STATUS` int DEFAULT 1 COMMENT '상태(1=정상,0=중지,-1=삭제)',
  PRIMARY KEY (`SITE_ID`,`COURSE_CODE`,`OPEN_YEAR`,`OPEN_TERM`,`BUNBAN_CODE`,`GROUP_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='학사 과목 운영 설정';

-- 2) 학사 과목 성적(A/B/C/D/F) 저장
CREATE TABLE IF NOT EXISTS `LM_POLY_COURSE_GRADE` (
  `SITE_ID` int NOT NULL COMMENT '사이트 ID',
  `COURSE_CODE` varchar(20) NOT NULL COMMENT '강좌코드',
  `OPEN_YEAR` varchar(10) NOT NULL COMMENT '연도',
  `OPEN_TERM` varchar(10) NOT NULL COMMENT '학기',
  `BUNBAN_CODE` varchar(20) NOT NULL COMMENT '분반코드',
  `GROUP_CODE` varchar(20) NOT NULL COMMENT '학부/대학원 구분',
  `MEMBER_KEY` varchar(50) NOT NULL COMMENT '학번/회원키',

  `GRADE` varchar(10) DEFAULT NULL COMMENT '등급(A/B/C/D/F)',
  `SCORE` int DEFAULT 0 COMMENT '점수(선택)',

  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  `STATUS` int DEFAULT 1 COMMENT '상태(1=정상,0=중지,-1=삭제)',
  PRIMARY KEY (`SITE_ID`,`COURSE_CODE`,`OPEN_YEAR`,`OPEN_TERM`,`BUNBAN_CODE`,`GROUP_CODE`,`MEMBER_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='학사 과목 성적';
