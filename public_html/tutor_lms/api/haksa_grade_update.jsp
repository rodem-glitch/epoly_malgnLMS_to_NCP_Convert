<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%!
// 왜: 성적 저장은 프론트 계산값을 그대로 믿지 않고, 서버에 저장된 평가 기준으로 다시 계산해 일관성을 지킵니다.
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
//- 학사 과목의 성적(A/B/C/D/F)을 DB에 저장합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

f.addElement("course_code", null, "hname:'강좌코드', required:'Y'");
f.addElement("open_year", null, "hname:'연도', required:'Y'");
f.addElement("open_term", null, "hname:'학기', required:'Y'");
f.addElement("bunban_code", null, "hname:'분반코드', required:'Y'");
f.addElement("group_code", null, "hname:'학부/대학원 구분', required:'Y'");
f.addElement("grades_json", "", "hname:'성적 JSON'");

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

String courseCode = f.get("course_code");
String openYear = f.get("open_year");
String openTerm = f.get("open_term");
String bunbanCode = f.get("bunban_code");
String groupCode = f.get("group_code");
String gradesJson = f.get("grades_json");

m.log(
	"haksa_grade_update",
	"update_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", open_year=" + openYear
	+ ", open_term=" + openTerm
	+ ", bunban_code=" + bunbanCode
	+ ", group_code=" + groupCode
	+ ", grades_json_len=" + gradesJson.length()
);

DataSet items = new DataSet();
if(!"".equals(gradesJson)) {
	try {
		items = malgnsoft.util.Json.decode(gradesJson);
	} catch(Exception e) {
		result.put("rst_code", "1002");
		result.put("rst_message", "grades_json 파싱에 실패했습니다.");
		result.print();
		return;
	}
}

PolyCourseSettingDao setting = new PolyCourseSettingDao();
DataSet sinfo = setting.find(
	"site_id = " + siteId
	+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
	+ " AND status != -1"
	, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
);
if(!sinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "평가 기준 정보가 없습니다. 먼저 평가항목을 저장해 주세요.");
	result.print();
	return;
}

String evalJson = sinfo.s("eval_json");
if("".equals(evalJson)) {
	result.put("rst_code", "4041");
	result.put("rst_message", "평가 기준 정보가 비어 있습니다. 먼저 평가항목을 저장해 주세요.");
	result.print();
	return;
}

int cutoffAPlus = 95;
int cutoffA = 90;
int cutoffBPlus = 85;
int cutoffB = 80;
int cutoffCPlus = 75;
int cutoffC = 70;
int cutoffDPlus = 65;
int cutoffD = 60;
try {
	JSONObject evalObj = new JSONObject(evalJson);
	JSONObject cutoffs = evalObj.optJSONObject("cutoffs");
	cutoffAPlus = parseCutoff(cutoffs, "A+");
	cutoffA = parseCutoff(cutoffs, "A");
	cutoffBPlus = parseCutoff(cutoffs, "B+");
	cutoffB = parseCutoff(cutoffs, "B");
	cutoffCPlus = parseCutoff(cutoffs, "C+");
	cutoffC = parseCutoff(cutoffs, "C");
	cutoffDPlus = parseCutoff(cutoffs, "D+");
	cutoffD = parseCutoff(cutoffs, "D");
} catch(Exception e) {
	result.put("rst_code", "1003");
	result.put("rst_message", "저장된 평가 기준 JSON이 올바르지 않습니다. " + e.getMessage());
	result.print();
	return;
}

if(!(cutoffAPlus > cutoffA && cutoffA > cutoffBPlus && cutoffBPlus > cutoffB
	&& cutoffB > cutoffCPlus && cutoffCPlus > cutoffC && cutoffC > cutoffDPlus && cutoffDPlus > cutoffD)) {
	result.put("rst_code", "1004");
	result.put("rst_message", "저장된 등급 컷오프 순서가 올바르지 않습니다.");
	result.print();
	return;
}

PolyCourseGradeDao grade = new PolyCourseGradeDao();
// 왜: Resin이 예전 클래스(기본 PK=id)로 로딩한 상태여도, 여기서 명시적으로 고정해 INSERT(id 자동추가) 오류를 막습니다.
grade.PK = "site_id,course_code,open_year,open_term,bunban_code,group_code,member_key";
grade.useSeq = "N";

// 왜: 화면에서 전달된 전체 목록을 기준으로 덮어쓰기하여 상태를 단순화합니다.
grade.delete(
	"site_id = " + siteId
	+ " AND course_code = '" + m.replace(courseCode, "'", "''") + "'"
	+ " AND open_year = '" + m.replace(openYear, "'", "''") + "'"
	+ " AND open_term = '" + m.replace(openTerm, "'", "''") + "'"
	+ " AND bunban_code = '" + m.replace(bunbanCode, "'", "''") + "'"
	+ " AND group_code = '" + m.replace(groupCode, "'", "''") + "'"
);

items.first();
int savedCount = 0;
while(items.next()) {
	String studentId = items.s("student_id");
	int score = items.i("score");

	if("".equals(studentId)) continue;
	if(score < 0) score = 0;
	if(score > 100) score = 100;

	// 왜: 프론트에서 전달한 grade가 무엇이든, 서버 저장 시점에 최신 평가 기준으로 다시 계산합니다.
	String gradeValue = resolveGradeByScore(
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

	grade.item("site_id", siteId);
	grade.item("course_code", courseCode);
	grade.item("open_year", openYear);
	grade.item("open_term", openTerm);
	grade.item("bunban_code", bunbanCode);
	grade.item("group_code", groupCode);
	grade.item("member_key", studentId);
	grade.item("grade", gradeValue);
	grade.item("score", score);
	grade.item("reg_date", m.time("yyyyMMddHHmmss"));
	grade.item("status", 1);

	if(!grade.insert()) {
		result.put("rst_code", "2000");
		result.put("rst_message", "저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
	savedCount++;
}

m.log(
	"haksa_grade_update",
	"update_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", saved_count=" + savedCount
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", savedCount);
result.print();

%>

