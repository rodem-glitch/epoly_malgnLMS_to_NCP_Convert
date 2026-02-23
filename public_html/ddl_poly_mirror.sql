-- 폴리텍(e-poly) View 미러링용 DDL
-- 왜 필요한가:
-- - 학사 View를 매번 API로 부분 조회(cnt 제한)하면 수강생/회원 정보가 누락되어 조인이 깨질 수 있습니다.
-- - 하루 1~2회 우리 DB에 미리 저장해두면, 화면은 로컬 DB만 조회해서 빠르고 안정적으로 동작합니다.
--
-- 주의:
-- - 운영 반영 시 기존 데이터 보호를 위해 DROP은 하지 않습니다.
-- - 이미 테이블이 있으면 “없는 것만” 생성합니다.

-- 1) 학사 과목(코스) 미러
CREATE TABLE IF NOT EXISTS `LM_POLY_COURSE` (
  `COURSE_CODE` varchar(20) NOT NULL COMMENT '강좌코드',
  `OPEN_YEAR` varchar(10) NOT NULL COMMENT '연도',
  `OPEN_TERM` varchar(10) NOT NULL COMMENT '학기',
  `BUNBAN_CODE` varchar(20) NOT NULL COMMENT '분반코드',
  `GROUP_CODE` varchar(20) NOT NULL COMMENT '학부/대학원 구분',
  `SYNC_DATE` varchar(14) NOT NULL COMMENT '동기화 기준시각(yyyyMMddHHmmss)',

  `COURSE_NAME` varchar(255) DEFAULT NULL COMMENT '강좌명(한글)',
  `COURSE_ENAME` varchar(255) DEFAULT NULL COMMENT '강좌명(영문)',
  `DEPT_CODE` varchar(50) DEFAULT NULL COMMENT '학과/전공 코드',
  `DEPT_NAME` varchar(255) DEFAULT NULL COMMENT '학과/전공 이름',
  `GRAD_CODE` varchar(50) DEFAULT NULL COMMENT '단과대학 코드',
  `GRAD_NAME` varchar(255) DEFAULT NULL COMMENT '단과대학 이름',
  `WEEK` varchar(50) DEFAULT NULL COMMENT '주차',
  `GRADE` varchar(50) DEFAULT NULL COMMENT '학년',
  `DAY_CD` varchar(50) DEFAULT NULL COMMENT '강의 요일',
  `CLASSROOM` varchar(255) DEFAULT NULL COMMENT '강의실',
  `CURRICULUM_CODE` varchar(50) DEFAULT NULL COMMENT '과목구분 코드',
  `CURRICULUM_NAME` varchar(255) DEFAULT NULL COMMENT '과목구분 이름',
  `TYPE_SYLLABUS` varchar(50) DEFAULT NULL COMMENT '강의계획서 구분',
  `IS_SYLLABUS` varchar(50) DEFAULT NULL COMMENT '강의계획서 존재여부',
  `ENGLISH` varchar(50) DEFAULT NULL COMMENT '영문 강좌 여부',
  `HOUR1` varchar(50) DEFAULT NULL COMMENT '강의 시간',
  `CATEGORY` varchar(50) DEFAULT NULL COMMENT '강좌형태',
  `VISIBLE` varchar(1) DEFAULT NULL COMMENT '강좌 폐강 여부 (Y=정상, N=폐강)',
  `STARTDATE` varchar(20) DEFAULT NULL COMMENT '강좌시작일',
  `ENDDATE` varchar(20) DEFAULT NULL COMMENT '강좌종료일',

  `RAW_JSON` mediumtext COMMENT '원본(디버깅용)',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`COURSE_CODE`,`OPEN_YEAR`,`OPEN_TERM`,`BUNBAN_CODE`,`GROUP_CODE`),
  KEY `IDX_LM_POLY_COURSE_CODE` (`COURSE_CODE`),
  KEY `IDX_LM_POLY_COURSE_TERM` (`OPEN_YEAR`,`OPEN_TERM`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 학사 과목 미러';

-- 임시 테이블 (스왑용)
CREATE TABLE IF NOT EXISTS `LM_POLY_COURSE_TMP` LIKE `LM_POLY_COURSE`;

-- 2) 학사 회원(사람) 미러
CREATE TABLE IF NOT EXISTS `LM_POLY_MEMBER` (
  `MEMBER_KEY` varchar(50) NOT NULL COMMENT '회원키(학번/사번 등)',
  `RPST_MEMBER_KEY` varchar(100) DEFAULT NULL COMMENT '대표회원키(로그인ID 등)',
  `SYNC_DATE` varchar(14) NOT NULL COMMENT '동기화 기준시각(yyyyMMddHHmmss)',
  `USER_TYPE` varchar(20) DEFAULT NULL COMMENT '사용자구분',
  `KOR_NAME` varchar(255) DEFAULT NULL COMMENT '이름(한글)',
  `ENG_NAME` varchar(255) DEFAULT NULL COMMENT '이름(영문)',
  `EMAIL` varchar(255) DEFAULT NULL COMMENT '이메일',
  `MOBILE` varchar(50) DEFAULT NULL COMMENT '휴대폰',
  `PHONE` varchar(50) DEFAULT NULL COMMENT '전화번호',
  `CAMPUS_CODE` varchar(50) DEFAULT NULL COMMENT '캠퍼스 코드',
  `CAMPUS_NAME` varchar(255) DEFAULT NULL COMMENT '캠퍼스 이름',
  `INSTITUTION_CODE` varchar(50) DEFAULT NULL COMMENT '기관 코드',
  `INSTITUTION_NAME` varchar(255) DEFAULT NULL COMMENT '기관 이름',
  `DEPT_CODE` varchar(50) DEFAULT NULL COMMENT '학과/전공 코드',
  `DEPT_NAME` varchar(255) DEFAULT NULL COMMENT '학과/전공 이름',
  `STATE` varchar(20) DEFAULT NULL COMMENT '상태',
  `USE_YN` varchar(1) DEFAULT NULL COMMENT '사용여부',
  `GENDER` varchar(1) DEFAULT NULL COMMENT '성별',

  `RAW_JSON` mediumtext COMMENT '원본(디버깅용)',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`MEMBER_KEY`),
  KEY `IDX_LM_POLY_MEMBER_RPST` (`RPST_MEMBER_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 학사 회원 미러';

-- 임시 테이블 (스왑용)
CREATE TABLE IF NOT EXISTS `LM_POLY_MEMBER_TMP` LIKE `LM_POLY_MEMBER`;

-- 3) 회원키 별칭 맵(조인 안정화용)
-- 왜: STUDENT_VIEW의 member_key가 MEMBER_VIEW의 member_key 또는 rpst_member_key 중 무엇과 맞는지 케이스가 섞일 수 있습니다.
--     그래서 alias_key(둘 다 가능)를 하나의 매핑 테이블로 묶어, 조인을 단순하게 만듭니다.
CREATE TABLE IF NOT EXISTS `LM_POLY_MEMBER_KEY` (
  `ALIAS_KEY` varchar(100) NOT NULL COMMENT '조인용 키(학번/로그인ID 등)',
  `MEMBER_KEY` varchar(50) NOT NULL COMMENT '기준 회원키(LM_POLY_MEMBER.MEMBER_KEY)',
  `SYNC_DATE` varchar(14) NOT NULL COMMENT '동기화 기준시각(yyyyMMddHHmmss)',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`ALIAS_KEY`),
  KEY `IDX_LM_POLY_MEMBER_KEY_MEMBER` (`MEMBER_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 회원키 별칭 매핑';

-- 임시 테이블 (스왑용)
CREATE TABLE IF NOT EXISTS `LM_POLY_MEMBER_KEY_TMP` LIKE `LM_POLY_MEMBER_KEY`;

-- 4) 학사 수강(수강생-과목) 미러
CREATE TABLE IF NOT EXISTS `LM_POLY_STUDENT` (
  `COURSE_CODE` varchar(20) NOT NULL COMMENT '강좌코드',
  `OPEN_YEAR` varchar(10) NOT NULL COMMENT '연도',
  `OPEN_TERM` varchar(10) NOT NULL COMMENT '학기',
  `BUNBAN_CODE` varchar(20) NOT NULL COMMENT '분반코드',
  `GROUP_CODE` varchar(20) NOT NULL COMMENT '학부/대학원 구분',
  `MEMBER_KEY` varchar(50) NOT NULL COMMENT '학번/회원키',
  `VISIBLE` varchar(1) DEFAULT NULL COMMENT '수강상태(원본 값)',
  `SYNC_DATE` varchar(14) NOT NULL COMMENT '동기화 기준시각(yyyyMMddHHmmss)',

  `RAW_JSON` mediumtext COMMENT '원본(디버깅용)',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`COURSE_CODE`,`OPEN_YEAR`,`OPEN_TERM`,`BUNBAN_CODE`,`GROUP_CODE`,`MEMBER_KEY`),
  KEY `IDX_LM_POLY_STUDENT_MEMBER` (`MEMBER_KEY`),
  KEY `IDX_LM_POLY_STUDENT_COURSE` (`COURSE_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 학사 수강 미러';

-- 임시 테이블 (스왑용)
CREATE TABLE IF NOT EXISTS `LM_POLY_STUDENT_TMP` LIKE `LM_POLY_STUDENT`;

-- 5) 동기화 로그(마지막 갱신 시간)
CREATE TABLE IF NOT EXISTS `LM_POLY_SYNC_LOG` (
  `SYNC_KEY` varchar(50) NOT NULL COMMENT '구분키(예: poly_mirror)',
  `LAST_SYNC_DATE` varchar(14) DEFAULT NULL COMMENT '마지막 동기화(yyyyMMddHHmmss)',
  `LAST_RESULT` varchar(20) DEFAULT NULL COMMENT '결과코드(OK/ERR)',
  `LAST_MESSAGE` varchar(255) DEFAULT NULL COMMENT '메시지',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`SYNC_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 동기화 로그';

-- 6) 학사 교수자(Professor) 미러
CREATE TABLE IF NOT EXISTS `LM_POLY_PROFESSOR` (
  `MEMBER_KEY` varchar(50) NOT NULL COMMENT '교수자 키(학사 회원키)',
  `PROF_NAME` varchar(255) DEFAULT NULL COMMENT '교수자 이름',
  `EMAIL` varchar(255) DEFAULT NULL COMMENT '이메일',
  `MOBILE` varchar(50) DEFAULT NULL COMMENT '휴대폰',
  `PHONE` varchar(50) DEFAULT NULL COMMENT '전화번호',
  `DEPT_CODE` varchar(50) DEFAULT NULL COMMENT '학과/전공 코드',
  `DEPT_NAME` varchar(255) DEFAULT NULL COMMENT '학과/전공 이름',
  `CAMPUS_CODE` varchar(50) DEFAULT NULL COMMENT '캠퍼스 코드',
  `CAMPUS_NAME` varchar(255) DEFAULT NULL COMMENT '캠퍼스 이름',
  `INSTITUTION_CODE` varchar(50) DEFAULT NULL COMMENT '기관 코드',
  `INSTITUTION_NAME` varchar(255) DEFAULT NULL COMMENT '기관 이름',

  `RAW_JSON` mediumtext COMMENT '원본(디버깅용)',
  `SYNC_DATE` varchar(14) NOT NULL COMMENT '동기화 기준시각(yyyyMMddHHmmss)',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`MEMBER_KEY`),
  KEY `IDX_LM_POLY_PROFESSOR_DEPT` (`DEPT_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 학사 교수자 미러';

-- 임시 테이블 (스왑용)
CREATE TABLE IF NOT EXISTS `LM_POLY_PROFESSOR_TMP` LIKE `LM_POLY_PROFESSOR`;

-- 7) 학사 과목-교수 매핑
CREATE TABLE IF NOT EXISTS `LM_POLY_COURSE_PROF` (
  `COURSE_CODE` varchar(20) NOT NULL COMMENT '강좌코드',
  `OPEN_YEAR` varchar(10) NOT NULL COMMENT '연도',
  `OPEN_TERM` varchar(10) NOT NULL COMMENT '학기',
  `BUNBAN_CODE` varchar(20) NOT NULL COMMENT '분반코드',
  `GROUP_CODE` varchar(20) NOT NULL COMMENT '학부/대학원 구분',
  `MEMBER_KEY` varchar(50) NOT NULL COMMENT '교수자 키',
  `ROLE` varchar(20) DEFAULT NULL COMMENT '역할(주/보조 등)',

  `RAW_JSON` mediumtext COMMENT '원본(디버깅용)',
  `SYNC_DATE` varchar(14) NOT NULL COMMENT '동기화 기준시각(yyyyMMddHHmmss)',
  `REG_DATE` varchar(14) NOT NULL COMMENT '등록일',
  `MOD_DATE` varchar(14) DEFAULT NULL COMMENT '수정일',
  PRIMARY KEY (`COURSE_CODE`,`OPEN_YEAR`,`OPEN_TERM`,`BUNBAN_CODE`,`GROUP_CODE`,`MEMBER_KEY`),
  KEY `IDX_LM_POLY_COURSE_PROF_MEMBER` (`MEMBER_KEY`),
  KEY `IDX_LM_POLY_COURSE_PROF_COURSE` (`COURSE_CODE`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='폴리텍 학사 과목-교수 매핑';

-- 임시 테이블 (스왑용)
CREATE TABLE IF NOT EXISTS `LM_POLY_COURSE_PROF_TMP` LIKE `LM_POLY_COURSE_PROF`;
