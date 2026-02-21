<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%!
// 왜: CSV 컬럼 안에 쉼표/줄바꿈/따옴표가 들어오면 엑셀 열이 깨지므로 서버에서 안전하게 이스케이프합니다.
private String csv(String value) {
	String v = value == null ? "" : value;
	v = v.replace("\"", "\"\"");
	return "\"" + v + "\"";
}

private String resolveGradeByScore(int score, int aPlus, int a, int bPlus, int b, int cPlus, int c, int dPlus, int d) {
	int safeScore = score;
	if(safeScore < 0) safeScore = 0;
	if(safeScore > 100) safeScore = 100;

	if(safeScore >= aPlus) return "A+";
	if(safeScore >= a) return "A";
	if(safeScore >= bPlus) return "B+";
	if(safeScore >= b) return "B";
	if(safeScore >= cPlus) return "C+";
	if(safeScore >= c) return "C";
	if(safeScore >= dPlus) return "D+";
	if(safeScore >= d) return "D";
	return "F";
}

private int parseCutoff(JSONObject cutoffs, String key) throws Exception {
	if(cutoffs == null || !cutoffs.has(key)) {
		throw new Exception("cutoffs." + key + " 값이 필요합니다.");
	}
	Object raw = cutoffs.get(key);
	int value = 0;
	try {
		if(raw instanceof Number) value = ((Number)raw).intValue();
		else value = Integer.parseInt(String.valueOf(raw).trim());
	} catch(Exception e) {
		throw new Exception("cutoffs." + key + " 값은 숫자여야 합니다.");
	}
	if(value < 0 || value > 100) {
		throw new Exception("cutoffs." + key + " 값은 0~100 범위여야 합니다.");
	}
	return value;
}
%><%

//왜 필요한가:
//- 평가 연동 상태와 상관없이, 교수자가 현재 성적 데이터를 즉시 내려받아 확인/공유할 수 있어야 합니다.

String courseCode = m.rs("course_code");
String openYear = m.rs("open_year");
String openTerm = m.rs("open_term");
String bunbanCode = m.rs("bunban_code");
String groupCode = m.rs("group_code");

if("".equals(courseCode) || "".equals(openYear) || "".equals(openTerm) || "".equals(bunbanCode) || "".equals(groupCode)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "학사 과목 키(course_code/open_year/open_term/bunban_code/group_code)가 필요합니다.");
	result.print();
	return;
}

m.log(
	"haksa_grade_export",
	"export_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", open_year=" + openYear
	+ ", open_term=" + openTerm
	+ ", bunban_code=" + bunbanCode
	+ ", group_code=" + groupCode
);

int cutoffAPlus = 95;
int cutoffA = 90;
int cutoffBPlus = 85;
int cutoffB = 80;
int cutoffCPlus = 75;
int cutoffC = 70;
int cutoffDPlus = 65;
int cutoffD = 60;
boolean useCutoff = false;

PolyCourseSettingDao setting = new PolyCourseSettingDao();
DataSet sinfo = setting.find(
	"site_id = " + siteId
	+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
	+ " AND status != -1"
	, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
);
if(sinfo.next() && !"".equals(sinfo.s("eval_json"))) {
	try {
		JSONObject evalObj = new JSONObject(sinfo.s("eval_json"));
		JSONObject cutoffs = evalObj.optJSONObject("cutoffs");
		cutoffAPlus = parseCutoff(cutoffs, "A+");
		cutoffA = parseCutoff(cutoffs, "A");
		cutoffBPlus = parseCutoff(cutoffs, "B+");
		cutoffB = parseCutoff(cutoffs, "B");
		cutoffCPlus = parseCutoff(cutoffs, "C+");
		cutoffC = parseCutoff(cutoffs, "C");
		cutoffDPlus = parseCutoff(cutoffs, "D+");
		cutoffD = parseCutoff(cutoffs, "D");
		useCutoff = true;
	} catch(Exception e) {
		result.put("rst_code", "1002");
		result.put("rst_message", "평가 기준 JSON이 올바르지 않습니다. " + e.getMessage());
		result.print();
		return;
	}
}

PolyCourseGradeDao grade = new PolyCourseGradeDao();
UserDao user = new UserDao();
DataSet list = grade.query(
	" SELECT g.member_key student_id, IFNULL(u.user_nm, '') user_nm, g.score, g.grade "
	+ " FROM " + grade.table + " g "
	// 왜: 운영 DB에서 login_id/member_key 컬레이션이 달라 조인 비교 시 SQL 에러가 발생하므로 동일 컬레이션으로 맞춥니다.
	+ " LEFT JOIN " + user.table + " u ON u.site_id = " + siteId
	+ " AND u.login_id COLLATE utf8mb4_unicode_ci = g.member_key COLLATE utf8mb4_unicode_ci"
	+ " AND u.status != -1 "
	+ " WHERE g.site_id = " + siteId
	+ " AND g.course_code = ? AND g.open_year = ? AND g.open_term = ? "
	+ " AND g.bunban_code = ? AND g.group_code = ? AND g.status != -1 "
	+ " ORDER BY g.member_key ASC "
	, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
);

String filename = "haksa_grade_" + courseCode + "_" + openYear + "_" + openTerm + "_" + m.time("yyyyMMddHHmmss") + ".csv";

response.reset();
response.setContentType("text/csv; charset=UTF-8");
response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
response.setHeader("Pragma", "no-cache");
response.setHeader("Expires", "0");

out.print('\uFEFF');
out.print("No,학번,이름,점수,등급\n");

int rowNo = 0;
int queriedCount = list.size();
// 왜: DataSet.first() 뒤에 next()를 바로 호출하면 단건 조회 시 첫 행이 건너뛰는 케이스가 있어 CSV 행 누락이 발생합니다.
// 그래서 export는 next()만으로 처음 행부터 순회해 실제 조회 행을 모두 출력합니다.
while(list.next()) {
	rowNo++;
	int score = list.i("score");
	if(score < 0) score = 0;
	if(score > 100) score = 100;

	String gradeValue = list.s("grade");
	if(useCutoff) {
		gradeValue = resolveGradeByScore(
			score,
			cutoffAPlus,
			cutoffA,
			cutoffBPlus,
			cutoffB,
			cutoffCPlus,
			cutoffC,
			cutoffDPlus,
			cutoffD
		);
	}

	out.print(rowNo + ",");
	out.print(csv(list.s("student_id")) + ",");
	out.print(csv(list.s("user_nm")) + ",");
	out.print(score + ",");
	out.print(csv(gradeValue) + "\n");
}

m.log(
	"haksa_grade_export",
	"export_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", queried_count=" + queriedCount
	+ ", row_count=" + rowNo
	+ ", use_cutoff=" + (useCutoff ? "Y" : "N")
);

%>
