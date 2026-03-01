<%@ page contentType="application/json; charset=utf-8" %><%@ include file="init.jsp" %><%!

//왜 필요한가:
//- 학사(View) 데이터는 외부(e-poly)에서 내려오며, API는 cnt 제한 때문에 “앞부분만” 받으면 수강생/회원 조인이 깨질 수 있습니다.
//- 그래서 하루 1~2회 이 JSP를 호출해 우리 DB에 미리 저장(미러링)해두고, 화면은 로컬 DB만 조회하도록 만듭니다.

public java.util.HashSet<String> parsePipeSet(String raw) {
	java.util.HashSet<String> set = new java.util.HashSet<String>();
	if(raw == null) return set;
	String trimmed = raw.trim();
	if("".equals(trimmed)) return set;

	// 왜: 운영에서 설정값을 '|' 또는 줄바꿈/콤마로 관리하는 경우가 있어 모두 허용합니다.
	String normalized = trimmed.replace("\r", "|").replace("\n", "|").replace(",", "|");
	String[] parts = normalized.split("\\|");
	for(String p : parts) {
		if(p == null) continue;
		String v = p.trim();
		if("".equals(v)) continue;
		set.add(v);
	}
	return set;
}

public String unwrapPre(String raw) {
	if(raw == null) return "";
	int preStart = raw.indexOf("<pre");
	if(preStart < 0) return raw;
	int gt = raw.indexOf(">", preStart);
	if(gt < 0) return raw;
	int preEnd = raw.indexOf("</pre>", gt);
	if(preEnd < 0) return raw;
	return raw.substring(gt + 1, preEnd);
}

public malgnsoft.db.DataSet parsePolyResponse(String raw) {
	malgnsoft.db.DataSet rs = new malgnsoft.db.DataSet();
	if(raw == null) return rs;

	String body = unwrapPre(raw);
	if(body == null) body = "";
	String trimmed = body.trim();
	if("".equals(trimmed)) return rs;

	// 1) JSON(유사 JSON) 형식이면 우선 Json.decode 시도
	if(trimmed.startsWith("[") || trimmed.startsWith("{")) {
		try { rs = malgnsoft.util.Json.decode(trimmed); return rs; }
		catch(Exception ignore) { /* fallthrough */ }
	}

	// 2) 텍스트(key: value) 형식 파싱
	String[] lines = body.split("\n");
	java.util.Map<String, String> currentRow = new java.util.HashMap<String, String>();
	for(String line : lines) {
		if(line == null) continue;
		String l = line.trim();
		if(l.startsWith("--")) continue;
		if(!l.contains(":")) continue;

		int sep = l.indexOf(":");
		String key = l.substring(0, sep).trim();
		String val = l.substring(sep + 1).trim().replaceAll(",\\s*$", "");

		if("".equals(key)) continue;
		if(currentRow.containsKey(key)) {
			rs.addRow(currentRow);
			currentRow = new java.util.HashMap<String, String>();
		}
		currentRow.put(key, val);
	}
	if(!currentRow.isEmpty()) rs.addRow(currentRow);
	return rs;
}

public String pick(malgnsoft.db.DataSet ds, String keyLower) {
	if(ds == null || keyLower == null) return "";
	String v = ds.s(keyLower);
	if(!"".equals(v)) return v;
	return ds.s(keyLower.toUpperCase());
}

public int toInt(String raw) {
	if(raw == null) return 0;
	try { return Integer.parseInt(raw.trim()); }
	catch(Exception ignore) { return 0; }
}

public int maxOpenYearFromRaw(String raw) {
	if(raw == null) return 0;
	String body = unwrapPre(raw);
	if(body == null) body = "";

	//왜: 외부 응답은 완전한 JSON이 아닐 수 있어, 문자열에서 open_year 값만 빠르게 최대값을 찾습니다.
	java.util.regex.Pattern ptn = java.util.regex.Pattern.compile("open_year\\s*:\\s*(\\d{4})");
	java.util.regex.Matcher mt = ptn.matcher(body);
	int max = 0;
	while(mt.find()) {
		int y = toInt(mt.group(1));
		if(y > max) max = y;
	}
	return max;
}

public String fetchPolyRaw(String endpoint, String tb, int cnt, int retry, int sleepMs, String wh) {
	String lastRaw = "";
	int maxTry = Math.max(1, retry);
	for(int i = 0; i < maxTry; i++) {
		try {
			malgnsoft.util.Http http = new malgnsoft.util.Http(endpoint);
			http.setParam("tb", tb);
			http.setParam("cnt", "" + cnt);
			if(wh != null && !"".equals(wh)) {
				http.setParam("wh", wh);
			}
			lastRaw = http.send("POST");

			if(parsePolyResponse(lastRaw).size() > 0) return lastRaw;
		} catch(Exception ignore) {}

		if(i + 1 < maxTry && sleepMs > 0) {
			try { Thread.sleep(sleepMs); } catch(Exception ignore) {}
		}
	}
	return lastRaw;
}
%><%

Json result = new Json(out);
result.put("rst_code", "9999");
result.put("rst_message", "올바른 접근이 아닙니다.");

//보안: 서버 로컬(스케줄러)에서만 호출하도록 제한합니다.
//왜: 외부에서 이 URL을 호출하면 DB에 대량 쓰기가 발생할 수 있어 위험합니다.
boolean localOnly = userIp.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(userIp) || "::1".equals(userIp);
// 왜: Spring Boot 스케줄러가 Docker 네트워크(172.x.x.x)에서 호출하므로, 토큰 인증을 추가합니다.
String syncToken = SiteConfig.s("poly_sync_token");
boolean tokenOk = !"".equals(syncToken) && syncToken.equals(m.rs("token"));
if(!localOnly && !tokenOk) {
	result.put("rst_code", "4030");
	result.put("rst_message", "로컬에서만 실행할 수 있습니다.");
	result.print();
	return;
}

int memberCnt = m.ri("member_cnt", 100000);
int studentCnt = m.ri("student_cnt", 100000);
int courseCnt = m.ri("course_cnt", 100000);
int professorCnt = m.ri("professor_cnt", 100000);
//왜: 기본은 최대치(100만)까지 받아서 로컬 DB에 저장하는 방식으로 운영합니다.
int requireYear = m.ri("require_year", toInt(m.time("yyyy")));
//왜: 년도별로 쪼개서 받기 위해 시작/끝 년도를 분리합니다.
int startYear = m.ri("start_year", requireYear);
int endYear = m.ri("end_year", requireYear);
String syncMode = m.rs("mode");
boolean studentOnly = "student_only".equals(syncMode);

String syncKey = "poly_mirror";
String syncDate = m.time("yyyyMMddHHmmss");

PolySyncLogDao syncLog = new PolySyncLogDao();

// === 학사 삭제 사용자 자동삭제(개인정보영향평가 대응) 설정 ===
// 왜: 학사(View)에서 사라진 사용자는 우리 DB(미러/회원)에도 남으면 개인정보 보관 이슈가 될 수 있습니다.
//     다만 외부 API 장애/누락 시 “대량 오삭제” 위험이 있어서, 설정 기반 + 안전장치(최소 비율 가드)를 둡니다.
boolean enableUserAutoDelete = "Y".equalsIgnoreCase(SiteConfig.s("poly_auto_delete_yn"));
boolean dryRunUserDelete = "dry_run".equalsIgnoreCase(syncMode) || "Y".equalsIgnoreCase(m.rs("dry_run_user_delete"));
boolean matchLoginIdAlso = "Y".equalsIgnoreCase(SiteConfig.s("poly_auto_delete_match_login_id_yn"));
java.util.HashSet<String> excludeLoginIds = parsePipeSet(
	!"".equals(m.rs("exclude_login_ids")) ? m.rs("exclude_login_ids") : SiteConfig.s("poly_auto_delete_exclude_login_ids")
);
int minMemberRatio = m.ri("min_member_ratio", m.parseInt(SiteConfig.s("poly_auto_delete_min_member_ratio")));
if(minMemberRatio <= 0) minMemberRatio = 50;
if(minMemberRatio > 100) minMemberRatio = 100;

//테이블 존재 확인(없으면 DDL을 먼저 적용해야 합니다)
String[] baseTables = {
	"LM_POLY_COURSE", "LM_POLY_MEMBER", "LM_POLY_MEMBER_KEY", "LM_POLY_STUDENT",
	"LM_POLY_PROFESSOR", "LM_POLY_COURSE_PROF", "LM_POLY_SYNC_LOG",
	// 왜: 삭제 감지를 위해 “이번 스냅샷”을 TMP에 적재한 뒤, 정상일 때만 스왑합니다.
	"LM_POLY_MEMBER_TMP", "LM_POLY_MEMBER_KEY_TMP"
};
int missing = 0;
for(int i = 0; i < baseTables.length; i++) {
	int exists = 0;
	try {
		exists = syncLog.getOneInt(
			" SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
			+ " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + baseTables[i] + "' "
		);
	} catch(Exception ignore) {}
	if(exists <= 0) missing++;
}
if(missing > 0) {
	result.put("rst_code", "5001");
	result.put("rst_message", "미러 테이블이 없습니다. DB에 `public_html/ddl_poly_mirror.sql`을 먼저 적용해 주세요.");
	result.put("rst_missing", missing);
	result.print();
	return;
}

String endpoint = "https://e-poly.kopo.ac.kr/main/vpn_test.jsp";

try {
	//1) 외부 데이터 조회 (빈 응답이면 재시도)
	malgnsoft.db.DataSet courseList = new malgnsoft.db.DataSet();
	malgnsoft.db.DataSet memberList = new malgnsoft.db.DataSet();
	int targetCourseMaxYear = 0;

	if(!studentOnly) {
		String rawCourse = fetchPolyRaw(endpoint, "COM.LMS_COURSE_VIEW", courseCnt, 3, 2000, "");
		courseList = parsePolyResponse(rawCourse);

		if(courseList.size() == 0) {
			result.put("rst_code", "5002");
			result.put("rst_message", "학사 과목 데이터가 비어 있습니다. 외부 응답을 확인해 주세요.");
			result.print();
			return;
		}

		courseList.first();
		while(courseList.next()) {
			int y = toInt(pick(courseList, "open_year"));
			if(y > targetCourseMaxYear) targetCourseMaxYear = y;
		}
		courseList.first();

		String rawMember = fetchPolyRaw(endpoint, "COM.LMS_MEMBER_VIEW", memberCnt, 3, 2000, "");
		memberList = parsePolyResponse(rawMember);
	}

	//2) DB 저장(동기화 기준시각(sync_date)으로 “이번에 받은 것만” 남깁니다)
	PolyCourseDao polyCourse = new PolyCourseDao();
	PolyStudentDao polyStudent = new PolyStudentDao();
	PolyMemberDao polyMember = new PolyMemberDao();
	PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();
	PolyMemberDao polyMemberTmp = new PolyMemberDao(); polyMemberTmp.table = "LM_POLY_MEMBER_TMP";
	PolyMemberKeyDao polyMemberKeyTmp = new PolyMemberKeyDao(); polyMemberKeyTmp.table = "LM_POLY_MEMBER_KEY_TMP";
	PolyProfessorDao polyProfessor = new PolyProfessorDao();
	PolyCourseProfDao polyCourseProf = new PolyCourseProfDao();

	int courseSaved = 0;
	int studentSaved = 0;
	int memberSaved = 0;
	int aliasSaved = 0;
	int professorSaved = 0;
	int courseProfSaved = 0;
	int studentMaxYear = 0;
	int memberOldCount = 0;
	int memberNewCount = 0;
	int memberDeletedKeys = 0;
	int userDeleteCandidate = 0;
	int userDeleteDone = 0;
	int userDeleteSkipped = 0;
	int userDeleteFailed = 0;
	boolean memberSwapDone = false;
	boolean memberGuardBlocked = false;

	//2-1) 과목
	if(!studentOnly) {
		courseList.first();
		while(courseList.next()) {
			String courseCode = pick(courseList, "course_code");
			String openYear = pick(courseList, "open_year");
			String openTerm = pick(courseList, "open_term");
			String bunbanCode = pick(courseList, "bunban_code");
			String groupCode = pick(courseList, "group_code");
			if("".equals(courseCode) || "".equals(openYear) || "".equals(openTerm) || "".equals(bunbanCode)) continue;
			if("".equals(groupCode)) groupCode = "U";

			String courseName = pick(courseList, "course_name");
			String courseEname = pick(courseList, "course_ename");
			String deptCode = pick(courseList, "dept_code");
			String deptName = pick(courseList, "dept_name");
			String gradCode = pick(courseList, "grad_code");
			String gradName = pick(courseList, "grad_name");
			String week = pick(courseList, "week");
			String grade = pick(courseList, "grade");
			String dayCd = pick(courseList, "day_cd");
			String classroom = pick(courseList, "classroom");
			String curriculumCode = pick(courseList, "curriculum_code");
			String curriculumName = pick(courseList, "curriculum_name");
			String typeSyllabus = pick(courseList, "type_syllabus");
			String isSyllabus = pick(courseList, "is_syllabus");
			String english = pick(courseList, "english");
			String hour1 = pick(courseList, "hour1");
			String category = pick(courseList, "category");
			String visible = pick(courseList, "visible");
			String startdate = pick(courseList, "startdate");
			String enddate = pick(courseList, "enddate");

			int ret = polyCourse.execute(
				" REPLACE INTO " + polyCourse.table
				+ " (course_code, open_year, open_term, bunban_code, group_code, sync_date"
				+ " , course_name, course_ename, dept_code, dept_name, grad_code, grad_name"
				+ " , week, grade, day_cd, classroom, curriculum_code, curriculum_name"
				+ " , type_syllabus, is_syllabus, english, hour1"
				+ " , category, visible, startdate, enddate, raw_json, reg_date, mod_date) "
				+ " VALUES(?,?,?,?,?,? ,?,?,?,?,?,? ,?,?,?,?,?,? ,?,?,?,? ,?,?,?,?,?,?,?) "
				, new Object[] {
					courseCode, openYear, openTerm, bunbanCode, groupCode, syncDate
					, courseName, courseEname, deptCode, deptName, gradCode, gradName
					, week, grade, dayCd, classroom, curriculumCode, curriculumName
					, typeSyllabus, isSyllabus, english, hour1
					, category, visible, startdate, enddate, null, syncDate, syncDate
				}
			);
			if(-1 < ret) courseSaved++;
		}
	}

	//2-2) 회원
	if(!studentOnly) {
		// 왜: 이번에 받은 “스냅샷”을 TMP 테이블에 먼저 적재합니다.
		//     외부 API가 누락/장애로 0건이 내려오면, 기존 데이터를 지우면 안 되므로 스왑 방식으로 안전하게 처리합니다.
		try { polyMemberTmp.execute("TRUNCATE TABLE " + polyMemberTmp.table); }
		catch(Exception e1) {
			// 왜: 일부 DB에서는 TRUNCATE 권한/옵션 이슈가 있을 수 있어, 안전하게 DELETE로 한 번 더 시도합니다.
			polyMemberTmp.execute("DELETE FROM " + polyMemberTmp.table);
		}
		try { polyMemberKeyTmp.execute("TRUNCATE TABLE " + polyMemberKeyTmp.table); }
		catch(Exception e1) {
			polyMemberKeyTmp.execute("DELETE FROM " + polyMemberKeyTmp.table);
		}

		memberList.first();
		while(memberList.next()) {
			String memberKey = pick(memberList, "member_key");
			if("".equals(memberKey)) continue;

			String rpstKey = pick(memberList, "rpst_member_key");
			String userType = pick(memberList, "user_type");
			String name = pick(memberList, "kor_name");
			String engName = pick(memberList, "eng_name");
			String email = pick(memberList, "email");
			String mobile = pick(memberList, "mobile");
			String phone = pick(memberList, "phone");
			String campusCode = pick(memberList, "campus_code");
			String campusName = pick(memberList, "campus_name");
			String institutionCode = pick(memberList, "institution_code");
			String institutionName = pick(memberList, "institution_name");
			String deptCode = pick(memberList, "dept_code");
			String deptName = pick(memberList, "dept_name");
			String state = pick(memberList, "state");
			String useYn = pick(memberList, "use_yn");
			String gender = pick(memberList, "gender");

			int ret = polyMemberTmp.execute(
				" REPLACE INTO " + polyMemberTmp.table
				+ " (member_key, rpst_member_key, sync_date, user_type"
				+ " , kor_name, eng_name, email, mobile, phone"
				+ " , campus_code, campus_name, institution_code, institution_name"
				+ " , dept_code, dept_name, state, use_yn, gender"
				+ " , raw_json, reg_date, mod_date) "
				+ " VALUES(?,?,?,? ,?,?,?,?,? ,?,?,?,? ,?,?,?,?,? ,?,?,?) "
				, new Object[] {
					memberKey, rpstKey, syncDate, userType
					, name, engName, email, mobile, phone
					, campusCode, campusName, institutionCode, institutionName
					, deptCode, deptName, state, useYn, gender
					, null, syncDate, syncDate
				}
			);
			if(-1 < ret) memberSaved++;

			//별칭 매핑(두 키 모두 조회 가능하게)
			int retAlias1 = polyMemberKeyTmp.execute(
				" REPLACE INTO " + polyMemberKeyTmp.table + " (alias_key, member_key, sync_date, reg_date, mod_date) VALUES(?,?,?,?,?) "
				, new Object[] { memberKey, memberKey, syncDate, syncDate, syncDate }
			);
			if(-1 < retAlias1) aliasSaved++;

			if(!"".equals(rpstKey)) {
				int retAlias2 = polyMemberKeyTmp.execute(
					" REPLACE INTO " + polyMemberKeyTmp.table + " (alias_key, member_key, sync_date, reg_date, mod_date) VALUES(?,?,?,?,?) "
					, new Object[] { rpstKey, memberKey, syncDate, syncDate, syncDate }
				);
				if(-1 < retAlias2) aliasSaved++;
			}
		}

		//2-2.3) LMS 회원(로그인ID) ↔ 학사 member_key 추가 매핑
		// 왜: TB_USER.login_id가 학번/교번과 다르면, 학사 member_key로 조인이 깨질 수 있습니다.
		//     그래서 "학번/교번(예: etc3) → login_id"가 있는 경우 별칭 테이블에 추가해
		//     강의실/교수자 탭/수강현황에서 동일한 member_key로 매핑되게 합니다.
		try {
			// 기본 가정: TB_USER.etc3에 학번/교번이 들어옵니다. (운영에서 다르면 여기만 바꾸면 됩니다)
			int retAliasUser = polyMemberKeyTmp.execute(
				" REPLACE INTO " + polyMemberKeyTmp.table
				+ " (alias_key, member_key, sync_date, reg_date, mod_date) "
				+ " SELECT u.login_id, pm.member_key, '" + syncDate + "', '" + syncDate + "', '" + syncDate + "' "
				+ " FROM TB_USER u "
				+ " INNER JOIN " + polyMemberTmp.table + " pm ON pm.member_key = u.etc3 "
				+ " WHERE u.site_id = " + siteId + " AND u.status = 1 "
				+ " AND u.login_id IS NOT NULL AND u.login_id <> '' "
				+ " AND u.etc3 IS NOT NULL AND u.etc3 <> '' "
			);
			if(-1 < retAliasUser) aliasSaved += retAliasUser;
		} catch(Exception ignore) {}

		// 2-2.9) 삭제 감지 + (선택) TB_USER 자동삭제
		try { memberOldCount = polyMember.getOneInt("SELECT COUNT(*) FROM " + polyMember.table); } catch(Exception ignore) {}
		try { memberNewCount = polyMemberTmp.getOneInt("SELECT COUNT(*) FROM " + polyMemberTmp.table); } catch(Exception ignore) {}

		// 왜: 외부 API 장애/누락으로 new가 급감하면, 학사에 남아있는 사용자까지 오삭제될 수 있어 차단합니다.
		if(memberNewCount <= 0) memberGuardBlocked = true;
		if(!memberGuardBlocked && memberOldCount > 0 && (memberNewCount * 100) < (memberOldCount * minMemberRatio)) memberGuardBlocked = true;

		if(!memberGuardBlocked) {
			// 왜: 학사에서 “사라진(member_key 미존재)” 사용자를 차집합으로 구합니다.
			try {
				DataSet deletedKeys = polyMember.query(
					" SELECT o.member_key "
					+ " FROM " + polyMember.table + " o "
					+ " LEFT JOIN " + polyMemberTmp.table + " n ON n.member_key = o.member_key "
					+ " WHERE n.member_key IS NULL "
				);
				memberDeletedKeys = deletedKeys.size();
			} catch(Exception ignore) {}

			// 왜: TMP를 준비한 뒤, 테이블명을 스왑하면 읽는 쪽은 항상 LM_POLY_MEMBER/LM_POLY_MEMBER_KEY만 보면 됩니다.
			//     RENAME TABLE은 한 문장에 여러 스왑을 묶을 수 있어, 중간 상태(비어있는 테이블)를 최소화합니다.
			try {
				int swapExists = 0;
				swapExists += syncLog.getOneInt(
					" SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
					+ " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'LM_POLY_MEMBER_SWAP' "
				);
				swapExists += syncLog.getOneInt(
					" SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
					+ " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'LM_POLY_MEMBER_KEY_SWAP' "
				);
				if(swapExists > 0) {
					throw new RuntimeException("스왑 테이블(LM_POLY_MEMBER_SWAP/LM_POLY_MEMBER_KEY_SWAP)이 남아있습니다. 운영 반영 전 정리 후 다시 실행해 주세요.");
				}

				int renameRet = polyMember.execute(
					" RENAME TABLE "
					+ " " + polyMember.table + " TO LM_POLY_MEMBER_SWAP, " + polyMemberTmp.table + " TO " + polyMember.table + ", LM_POLY_MEMBER_SWAP TO " + polyMemberTmp.table
					+ " , " + polyMemberKey.table + " TO LM_POLY_MEMBER_KEY_SWAP, " + polyMemberKeyTmp.table + " TO " + polyMemberKey.table + ", LM_POLY_MEMBER_KEY_SWAP TO " + polyMemberKeyTmp.table
				);
				memberSwapDone = -1 < renameRet;
			} catch(Exception e) {
				syncLog.upsert(syncKey, "ERR", "회원 스냅샷 스왑 실패: " + e.getMessage());
				throw e;
			}

			// 왜: 학사에서 삭제된 사용자를 LMS 회원에서도 자동 처리(논리삭제)하려면,
			//     “old에는 있고 new에는 없는” member_key를 기준으로 TB_USER를 찾습니다.
			if(enableUserAutoDelete) {
				UserDao userDao = new UserDao();
				try {
					String joinCond = "u.etc3 = o.member_key";
					if(matchLoginIdAlso) joinCond = "(" + joinCond + " OR u.login_id = o.member_key)";

					DataSet delUsers = userDao.query(
						" SELECT DISTINCT u.id, u.login_id "
						+ " FROM TB_USER u "
						+ " INNER JOIN " + polyMemberTmp.table + " o ON " + joinCond
						+ " LEFT JOIN " + polyMember.table + " n ON n.member_key = o.member_key "
						+ " WHERE u.site_id = " + siteId
						+ " AND u.status != -1 AND u.user_kind = 'U' "
						+ " AND n.member_key IS NULL "
					);

					userDeleteCandidate = delUsers.size();
					delUsers.first();
					while(delUsers.next()) {
						String lid = delUsers.s("login_id");
						if(lid == null) lid = "";
						lid = lid.trim();

						// 왜: 테스트 계정/특수 계정은 운영 정책상 자동삭제 대상에서 제외해야 합니다.
						if(excludeLoginIds.contains(lid)) { userDeleteSkipped++; continue; }

						if(dryRunUserDelete) { continue; }

						boolean ok = userDao.deleteUser(delUsers.i("id"));
						if(ok) userDeleteDone++;
						else userDeleteFailed++;
					}
					delUsers.first();
				} catch(Exception e) {
					syncLog.upsert(syncKey, "ERR", "TB_USER 자동삭제 처리 실패: " + e.getMessage());
					throw e;
				}
			}
		}
	}

	//2-2.5) 교수자 뷰 미러 + 과목-교수 매핑
	if(!studentOnly) {
		String rawProfessor = fetchPolyRaw(endpoint, "COM.LMS_PROFESSOR_VIEW", professorCnt, 3, 2000, "");
		malgnsoft.db.DataSet professorList = parsePolyResponse(rawProfessor);

		professorList.first();
		while(professorList.next()) {
			String courseCode = pick(professorList, "course_code");
			String openYear = pick(professorList, "open_year");
			String openTerm = pick(professorList, "open_term");
			String bunbanCode = pick(professorList, "bunban_code");
			String groupCode = pick(professorList, "group_code");
			if("".equals(groupCode)) groupCode = "U";

			String profKey = pick(professorList, "member_key");
			if("".equals(profKey)) profKey = pick(professorList, "professor_key");
			if("".equals(profKey)) profKey = pick(professorList, "prof_key");
			if("".equals(profKey)) profKey = pick(professorList, "user_id");
			if("".equals(profKey)) continue;

			String profName = pick(professorList, "prof_name");
			if("".equals(profName)) profName = pick(professorList, "professor_name");
			if("".equals(profName)) profName = pick(professorList, "kor_name");
			if("".equals(profName)) profName = pick(professorList, "user_nm");
			if("".equals(profName)) profName = pick(professorList, "name");

			String email = pick(professorList, "email");
			String mobile = pick(professorList, "mobile");
			String phone = pick(professorList, "phone");
			String deptCode = pick(professorList, "dept_code");
			String deptName = pick(professorList, "dept_name");
			String campusCode = pick(professorList, "campus_code");
			String campusName = pick(professorList, "campus_name");
			String institutionCode = pick(professorList, "institution_code");
			String institutionName = pick(professorList, "institution_name");
			String role = pick(professorList, "role");
			if("".equals(role)) role = pick(professorList, "prof_role");
			if("".equals(role)) role = pick(professorList, "type");

			int retProf = polyProfessor.execute(
				" REPLACE INTO " + polyProfessor.table
				+ " (member_key, prof_name, email, mobile, phone, dept_code, dept_name"
				+ " , campus_code, campus_name, institution_code, institution_name"
				+ " , raw_json, sync_date, reg_date, mod_date) "
				+ " VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,?,?,?) "
				, new Object[] {
					profKey, profName, email, mobile, phone, deptCode, deptName
					, campusCode, campusName, institutionCode, institutionName
					, null, syncDate, syncDate, syncDate
				}
			);
			if(-1 < retProf) professorSaved++;

			if(!"".equals(courseCode) && !"".equals(openYear) && !"".equals(openTerm) && !"".equals(bunbanCode)) {
				int retMap = polyCourseProf.execute(
					" REPLACE INTO " + polyCourseProf.table
					+ " (course_code, open_year, open_term, bunban_code, group_code, member_key, role"
					+ " , raw_json, sync_date, reg_date, mod_date) "
					+ " VALUES(?,?,?,?,?,?,?, ?,?,?,?) "
					, new Object[] {
						courseCode, openYear, openTerm, bunbanCode, groupCode, profKey, role
						, null, syncDate, syncDate, syncDate
					}
				);
				if(-1 < retMap) courseProfSaved++;
			}
		}
	}

	//2-3) 수강(수강생-과목) - 년도별로 쪼개서 받습니다.
	int yearFrom = Math.min(startYear, endYear);
	int yearTo = Math.max(startYear, endYear);
	for(int y = yearFrom; y <= yearTo; y++) {
		String wh = "open_year=" + y;
		String rawStudent = fetchPolyRaw(endpoint, "COM.LMS_STUDENT_VIEW", studentCnt, 2, 1500, wh);
		malgnsoft.db.DataSet studentList = parsePolyResponse(rawStudent);
		if(studentList.size() == 0) continue;

		if(y > studentMaxYear) studentMaxYear = y;

		studentList.first();
		while(studentList.next()) {
			String courseCode = pick(studentList, "course_code");
			String openYear = pick(studentList, "open_year");
			String openTerm = pick(studentList, "open_term");
			String bunbanCode = pick(studentList, "bunban_code");
			String groupCode = pick(studentList, "group_code");
			String memberKey = pick(studentList, "member_key");
			String visible = pick(studentList, "visible");

			if("".equals(courseCode) || "".equals(openYear) || "".equals(openTerm) || "".equals(bunbanCode) || "".equals(memberKey)) continue;
			if("".equals(groupCode)) groupCode = "U";

			int ret = polyStudent.execute(
				" REPLACE INTO " + polyStudent.table
				+ " (course_code, open_year, open_term, bunban_code, group_code, member_key, visible, sync_date, raw_json, reg_date, mod_date) "
				+ " VALUES(?,?,?,?,?,?,?, ?, ?, ?, ?) "
				, new Object[] {
					courseCode, openYear, openTerm, bunbanCode, groupCode, memberKey, visible
					, syncDate, null, syncDate, syncDate
				}
			);
			if(-1 < ret) studentSaved++;
		}
	}

	//3) 로그 기록
	syncLog.upsert(syncKey, "OK"
		, "성공(req=" + requireYear + ", smax=" + studentMaxYear + ", cnt=" + studentCnt + ")"
		+ ", member(old=" + memberOldCount + ", new=" + memberNewCount + ", del=" + memberDeletedKeys + ", swap=" + (memberSwapDone ? "Y" : "N") + ")"
		+ (memberGuardBlocked ? ", member_guard=Y(min=" + minMemberRatio + "%)" : ", member_guard=N")
		+ (enableUserAutoDelete ? ", user_del(cand=" + userDeleteCandidate + ", done=" + userDeleteDone + ", skip=" + userDeleteSkipped + ", fail=" + userDeleteFailed + ", dry=" + (dryRunUserDelete ? "Y" : "N") + ")" : ", user_del(disabled)")
	);

	result.put("rst_code", "0000");
	result.put("rst_message", memberGuardBlocked ? "성공(회원 스냅샷 가드로 회원/자동삭제는 스킵됨)" : "성공");
	result.put("rst_sync_date", syncDate);
	result.put("rst_course_max_year", targetCourseMaxYear);
	result.put("rst_require_year", requireYear);
	result.put("rst_student_max_year", studentMaxYear);
	result.put("rst_student_cnt_used", studentCnt);
	result.put("rst_course_saved", courseSaved);
	result.put("rst_member_saved", memberSaved);
	result.put("rst_alias_saved", aliasSaved);
	result.put("rst_member_old_count", memberOldCount);
	result.put("rst_member_new_count", memberNewCount);
	result.put("rst_member_deleted_keys", memberDeletedKeys);
	result.put("rst_member_swap_done", memberSwapDone ? "Y" : "N");
	result.put("rst_member_guard_blocked", memberGuardBlocked ? "Y" : "N");
	result.put("rst_member_guard_min_ratio", minMemberRatio);
	result.put("rst_user_auto_delete_enabled", enableUserAutoDelete ? "Y" : "N");
	result.put("rst_user_auto_delete_dry_run", dryRunUserDelete ? "Y" : "N");
	result.put("rst_user_auto_delete_match_login_id", matchLoginIdAlso ? "Y" : "N");
	result.put("rst_user_delete_candidate", userDeleteCandidate);
	result.put("rst_user_delete_done", userDeleteDone);
	result.put("rst_user_delete_skipped", userDeleteSkipped);
	result.put("rst_user_delete_failed", userDeleteFailed);
	result.put("rst_professor_saved", professorSaved);
	result.put("rst_course_prof_saved", courseProfSaved);
	result.put("rst_student_saved", studentSaved);
	result.print();

} catch(Exception e) {
	try { syncLog.upsert(syncKey, "ERR", e.getMessage()); } catch(Exception ignore) {}
	result.put("rst_code", "9000");
	result.put("rst_message", "동기화 중 오류가 발생했습니다: " + e.getMessage());
	result.put("rst_sync_date", syncDate);
	result.print();
}

%>
