<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- "담당과목" 화면의 년도 필터는 데이터가 존재하는 년도만(또는 최소한의 기본 범위) 보여줘야 사용자가 헷갈리지 않습니다.
//- 프리즘(LM_COURSE)과 학사 미러(LM_POLY_COURSE)는 서로 다른 소스라, 둘을 합쳐서 년도 옵션을 만들어야 합니다.
//  (특히 화면 기본 탭이 '학사'이므로, 학사 데이터만 있어도 년도 옵션이 비어 보이면 사용자가 '데이터가 없다'고 오해합니다.)

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
PolyCourseDao polyCourse = new PolyCourseDao();
PolyCourseProfDao polyCourseProf = new PolyCourseProfDao();
PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();
UserDao user = new UserDao();

int tutorId = m.ri("tutor_id"); //관리자용 필터(선택)

//왜: 프론트에서 바로 select 옵션으로 쓰기 쉽도록, 서버에서 중복 제거/정렬까지 끝내서 내려줍니다.
java.util.HashSet<String> yearSet = new java.util.HashSet<String>();

//------------------------------------------------------------------------------
// 1) 프리즘(LM_COURSE) 기준 년도
//------------------------------------------------------------------------------
//왜: 교수자는 본인 과목만, 관리자는 전체(또는 특정 교수자) 과목의 년도를 조회할 수 있어야 합니다.
String accessWhere = "";
if(!isAdmin) {
	// 왜: 연도 필터도 담당과목과 같은 기준(주/보조강사 + 과정담당자 + manager_id)으로 계산해야
	//     목록에는 보이는데 년도 필터에는 안 뜨는 불일치를 막을 수 있습니다.
	accessWhere =
		" AND (c.manager_id = " + userId
		+ " OR EXISTS (SELECT 1 FROM " + courseTutor.table + " ct "
		+ " WHERE ct.course_id = c.id AND ct.user_id = " + userId + " AND ct.site_id = " + siteId
		+ " AND ct.type IN ('major', 'minor')) "
		+ " OR EXISTS (SELECT 1 FROM " + courseManager.table + " cm "
		+ " WHERE cm.course_id = c.id AND cm.user_id = " + userId + " AND cm.site_id = " + siteId + ")) ";
} else if(0 < tutorId) {
	accessWhere =
		" AND (c.manager_id = " + tutorId
		+ " OR EXISTS (SELECT 1 FROM " + courseTutor.table + " ct "
		+ " WHERE ct.course_id = c.id AND ct.user_id = " + tutorId + " AND ct.site_id = " + siteId
		+ " AND ct.type IN ('major', 'minor')) "
		+ " OR EXISTS (SELECT 1 FROM " + courseManager.table + " cm "
		+ " WHERE cm.course_id = c.id AND cm.user_id = " + tutorId + " AND cm.site_id = " + siteId + ")) ";
}

try {
	DataSet prismYears = course.query(
		" SELECT DISTINCT c.year "
		+ " FROM " + course.table + " c "
		+ " WHERE c.site_id = " + siteId + " AND c.status != -1 AND c.onoff_type != 'P' AND c.year > 0 "
		+ accessWhere
		+ " ORDER BY c.year DESC "
	);
	while(prismYears.next()) {
		String y = prismYears.s("year").trim();
		if(!"".equals(y)) yearSet.add(y);
	}
} catch(Exception ignore) {}

//------------------------------------------------------------------------------
// 2) 학사 미러(LM_POLY_COURSE) 기준 년도
//------------------------------------------------------------------------------
//왜: 학사 탭은 교수자-과목 매핑(LM_POLY_COURSE_PROF) 기준으로만 보여야 "내 과목"이 됩니다.
String joinProf = "";
if(!isAdmin) {
	//왜: login_id ↔ member_key 매핑이 섞인 케이스가 있어, 별칭 테이블을 통해 최대한 안전하게 member_key를 해석합니다.
	String dbLoginId = loginId;
	try {
		DataSet loginInfo = user.find("id = " + userId);
		if(loginInfo.next() && !"".equals(loginInfo.s("login_id"))) dbLoginId = loginInfo.s("login_id");
	} catch(Exception ignore) {}

	String safeLoginId = m.replace(loginId, "'", "''");
	String safeDbLoginId = m.replace(dbLoginId, "'", "''");
	String resolvedMemberKey = "";
	try {
		DataSet mk = polyMemberKey.query(
			"SELECT member_key FROM " + polyMemberKey.table
			+ " WHERE alias_key = '" + safeDbLoginId + "' OR member_key = '" + safeDbLoginId + "'"
			+ " OR alias_key = '" + safeLoginId + "' OR member_key = '" + safeLoginId + "'"
			+ " LIMIT 1"
		);
		if(mk.next()) resolvedMemberKey = mk.s("member_key");
	} catch(Exception ignore) {}
	if("".equals(resolvedMemberKey)) resolvedMemberKey = dbLoginId;
	String safeResolvedMemberKey = m.replace(resolvedMemberKey, "'", "''");

	joinProf = " INNER JOIN " + polyCourseProf.table + " cp "
		+ " ON cp.course_code = c.course_code AND cp.open_year = c.open_year "
		+ " AND cp.open_term = c.open_term AND cp.bunban_code = c.bunban_code "
		+ " AND cp.group_code = c.group_code "
		+ " AND cp.member_key = '" + safeResolvedMemberKey + "' ";
}

try {
	DataSet haksaYears = polyCourse.query(
		" SELECT DISTINCT c.open_year year "
		+ " FROM " + polyCourse.table + " c "
		+ joinProf
		+ " WHERE c.open_year IS NOT NULL AND c.open_year != '' "
		+ " ORDER BY c.open_year DESC "
	);
	while(haksaYears.next()) {
		String y = haksaYears.s("year").trim();
		if(!"".equals(y)) yearSet.add(y);
	}
} catch(Exception ignore) {}

//------------------------------------------------------------------------------
// 3) 폴백(최소 범위) - 현재년도/직전 2개년도는 항상 옵션으로 노출
//------------------------------------------------------------------------------
//왜: 학사 미러를 아직 동기화하지 않았거나, 과목이 없는 초기 환경에서도 UI가 '텅 빈 옵션'으로 보이지 않게 합니다.
int nowYear = 0;
try { nowYear = Integer.parseInt(m.time("yyyy")); } catch(Exception ignore) {}
if(nowYear > 0) {
	yearSet.add("" + nowYear);
	yearSet.add("" + (nowYear - 1));
	yearSet.add("" + (nowYear - 2));
}

java.util.ArrayList<String> years = new java.util.ArrayList<String>(yearSet);
java.util.Collections.sort(years, new java.util.Comparator<String>() {
	public int compare(String a, String b) {
		//왜: 문자열 비교는 2026/2025 정렬이 우연히 맞더라도, 안전하게 숫자로 비교합니다.
		int ia = 0;
		int ib = 0;
		try { ia = Integer.parseInt(a); } catch(Exception ignore) {}
		try { ib = Integer.parseInt(b); } catch(Exception ignore) {}
		return (ib - ia);
	}
});

DataSet list = new DataSet();
for(int i = 0; i < years.size(); i++) {
	String y = years.get(i);
	if("".equals(y)) continue;
	java.util.Map<String, Object> row = new java.util.HashMap<String, Object>();
	row.put("year", y);
	list.addRow(row);
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
