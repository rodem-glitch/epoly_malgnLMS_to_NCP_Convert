<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%!
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
//- 학사 과목의 성적(A/B/C/D/F)을 DB에서 읽어옵니다.

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

PolyCourseGradeDao grade = new PolyCourseGradeDao();
PolyCourseSettingDao setting = new PolyCourseSettingDao();

int cutoffAPlus = 95;
int cutoffA = 90;
int cutoffBPlus = 85;
int cutoffB = 80;
int cutoffCPlus = 75;
int cutoffC = 70;
int cutoffDPlus = 65;
int cutoffD = 60;
boolean useCutoff = false;

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

ArrayList<Object> params = new ArrayList<Object>();
params.add(siteId);
params.add(courseCode);
params.add(openYear);
params.add(openTerm);
params.add(bunbanCode);
params.add(groupCode);

DataSet list = grade.query(
	" SELECT member_key student_id, grade, score "
	+ " FROM " + grade.table
	+ " WHERE site_id = ? AND course_code = ? AND open_year = ? AND open_term = ? "
	+ " AND bunban_code = ? AND group_code = ? AND status != -1 "
	+ " ORDER BY member_key ASC "
	, params.toArray()
);

list.first();
while(list.next()) {
	int score = list.i("score");
	if(score < 0) score = 0;
	if(score > 100) score = 100;
	list.put("score", score);
	if(useCutoff) {
		list.put("grade", resolveGradeByScore(
			score,
			cutoffAPlus,
			cutoffA,
			cutoffBPlus,
			cutoffB,
			cutoffCPlus,
			cutoffC,
			cutoffDPlus,
			cutoffD
		));
	}
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
