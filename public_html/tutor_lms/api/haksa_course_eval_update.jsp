<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%!
// 왜: 점수(0~100)를 등급으로 바꿀 때 서버 기준을 하나로 고정해야,
//     화면/엑셀/재조회 결과가 항상 같은 값으로 맞춰집니다.
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

// 왜: 컷오프 JSON이 누락/오타 상태면 잘못된 등급이 대량 반영될 수 있어 저장 단계에서 즉시 차단합니다.
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

// 왜: 평가항목 구성은 운영 기준(출석/중간/기말/과제/기타/참여도)으로 고정해 데이터 해석 혼선을 막습니다.
private int parseWeight(JSONObject weights, String key) throws Exception {
	if(weights == null || !weights.has(key)) {
		throw new Exception("weights." + key + " 값이 필요합니다.");
	}
	Object raw = weights.get(key);
	int value = 0;
	try {
		if(raw instanceof Number) value = ((Number)raw).intValue();
		else value = Integer.parseInt(String.valueOf(raw).trim());
	} catch(Exception e) {
		throw new Exception("weights." + key + " 값은 숫자여야 합니다.");
	}
	if(value < 0 || value > 100) {
		throw new Exception("weights." + key + " 값은 0~100 범위여야 합니다.");
	}
	return value;
}
%><%

//왜 필요한가:
//- 학사 과목의 평가/수료 기준을 DB에 저장합니다.

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
f.addElement("eval_json", "", "hname:'평가 기준 JSON'");

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
String evalJson = f.get("eval_json");

if("".equals(evalJson)) {
	result.put("rst_code", "1002");
	result.put("rst_message", "eval_json이 비어 있습니다.");
	result.print();
	return;
}

JSONObject evalObj = null;
JSONObject weights = null;
JSONObject cutoffs = null;
int weightAttendance = 0;
int weightMidterm = 0;
int weightFinal = 0;
int weightAssignment = 0;
int weightEtc = 0;
int weightParticipation = 0;
int cutoffAPlus = 95;
int cutoffA = 90;
int cutoffBPlus = 85;
int cutoffB = 80;
int cutoffCPlus = 75;
int cutoffC = 70;
int cutoffDPlus = 65;
int cutoffD = 60;
try {
	evalObj = new JSONObject(evalJson);
	weights = evalObj.optJSONObject("weights");
	weightAttendance = parseWeight(weights, "attendance");
	weightMidterm = parseWeight(weights, "midterm");
	weightFinal = parseWeight(weights, "final");
	weightAssignment = parseWeight(weights, "assignment");
	weightEtc = parseWeight(weights, "etc");
	weightParticipation = parseWeight(weights, "participation");

	cutoffs = evalObj.optJSONObject("cutoffs");
	cutoffAPlus = parseCutoff(cutoffs, "A+");
	cutoffA = parseCutoff(cutoffs, "A");
	cutoffBPlus = parseCutoff(cutoffs, "B+");
	cutoffB = parseCutoff(cutoffs, "B");
	cutoffCPlus = parseCutoff(cutoffs, "C+");
	cutoffC = parseCutoff(cutoffs, "C");
	cutoffDPlus = parseCutoff(cutoffs, "D+");
	cutoffD = parseCutoff(cutoffs, "D");
} catch(Exception e) {
	result.put("rst_code", "1002");
	result.put("rst_message", "평가 기준 JSON이 올바르지 않습니다. " + e.getMessage());
	result.print();
	return;
}

int weightSum = weightAttendance + weightMidterm + weightFinal + weightAssignment + weightEtc + weightParticipation;
if(weightSum != 100) {
	result.put("rst_code", "1004");
	result.put("rst_message", "평가항목 비율 합계는 100이어야 합니다. (현재: " + weightSum + ")");
	result.print();
	return;
}

if(!(cutoffAPlus > cutoffA && cutoffA > cutoffBPlus && cutoffBPlus > cutoffB
	&& cutoffB > cutoffCPlus && cutoffCPlus > cutoffC && cutoffC > cutoffDPlus && cutoffDPlus > cutoffD)) {
	result.put("rst_code", "1005");
	result.put("rst_message", "등급 컷오프는 A+ > A > B+ > B > C+ > C > D+ > D 순서여야 합니다.");
	result.print();
	return;
}

m.log(
	"haksa_course_eval",
	"update_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", open_year=" + openYear
	+ ", open_term=" + openTerm
	+ ", bunban_code=" + bunbanCode
	+ ", group_code=" + groupCode
	+ ", weight_sum=" + weightSum
	+ ", eval_json_len=" + evalJson.length()
);

PolyCourseSettingDao setting = new PolyCourseSettingDao();
// 왜: Resin이 예전 클래스(기본 PK=id)로 로딩한 상태여도, 여기서 명시적으로 고정해 INSERT(id 자동추가) 오류를 막습니다.
setting.PK = "site_id,course_code,open_year,open_term,bunban_code,group_code";
setting.useSeq = "N";

int count = setting.findCount(
	"site_id = " + siteId
	+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
	+ " AND status != -1"
	, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
);

setting.item("eval_json", evalJson);
setting.item("mod_date", m.time("yyyyMMddHHmmss"));

if(count > 0) {
	String safeCourseCode = m.replace(courseCode, "'", "''");
	String safeOpenYear = m.replace(openYear, "'", "''");
	String safeOpenTerm = m.replace(openTerm, "'", "''");
	String safeBunbanCode = m.replace(bunbanCode, "'", "''");
	String safeGroupCode = m.replace(groupCode, "'", "''");

	if(!setting.update(
		"site_id = " + siteId
		+ " AND course_code = '" + safeCourseCode + "'"
		+ " AND open_year = '" + safeOpenYear + "'"
		+ " AND open_term = '" + safeOpenTerm + "'"
		+ " AND bunban_code = '" + safeBunbanCode + "'"
		+ " AND group_code = '" + safeGroupCode + "'"
		+ " AND status != -1"
	)) {
		result.put("rst_code", "2000");
		result.put("rst_message", "저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
} else {
	setting.item("site_id", siteId);
	setting.item("course_code", courseCode);
	setting.item("open_year", openYear);
	setting.item("open_term", openTerm);
	setting.item("bunban_code", bunbanCode);
	setting.item("group_code", groupCode);
	setting.item("reg_date", m.time("yyyyMMddHHmmss"));
	setting.item("status", 1);

	if(!setting.insert()) {
		result.put("rst_code", "2000");
		result.put("rst_message", "저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

PolyCourseGradeDao grade = new PolyCourseGradeDao();
String whereBase =
	"site_id = " + siteId
	+ " AND course_code = '" + m.replace(courseCode, "'", "''") + "'"
	+ " AND open_year = '" + m.replace(openYear, "'", "''") + "'"
	+ " AND open_term = '" + m.replace(openTerm, "'", "''") + "'"
	+ " AND bunban_code = '" + m.replace(bunbanCode, "'", "''") + "'"
	+ " AND group_code = '" + m.replace(groupCode, "'", "''") + "'"
	+ " AND status != -1";

DataSet gradeRows = grade.find(whereBase, "member_key, score, grade");
int targetCount = gradeRows.size();
int changedCount = 0;

gradeRows.first();
while(gradeRows.next()) {
	int score = gradeRows.i("score");
	String newGrade = resolveGradeByScore(
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
	if(newGrade.equals(gradeRows.s("grade"))) continue;

	grade.item("grade", newGrade);
	grade.item("mod_date", m.time("yyyyMMddHHmmss"));
	if(!grade.update(
		whereBase
		+ " AND member_key = '" + m.replace(gradeRows.s("member_key"), "'", "''") + "'"
	)) {
		m.log(
			"haksa_course_eval",
			"grade_recalc_update_failed manager_id=" + userId
			+ ", site_id=" + siteId
			+ ", course_code=" + courseCode
			+ ", member_key=" + gradeRows.s("member_key")
			+ ", score=" + score
			+ ", grade=" + newGrade
		);
		result.put("rst_code", "2001");
		result.put("rst_message", "평가 기준 저장 후 성적 반영 중 오류가 발생했습니다.");
		result.print();
		return;
	}
	changedCount++;
}

m.log(
	"haksa_course_eval",
	"update_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", target_count=" + targetCount
	+ ", changed_count=" + changedCount
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", changedCount);
result.put("rst_target_count", targetCount);
result.put("rst_changed_count", changedCount);
result.print();

%>

