<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 운영 중 설문명/설명/익명설정/적용시점을 바꿔도 과목 배치와 결과 집계가 끊기지 않아야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int surveyId = m.ri("survey_id");
if(0 == courseId || 0 == surveyId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, survey_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseModuleDao courseModule = new CourseModuleDao();
SurveyDao survey = new SurveyDao();

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 설문을 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet minfo = courseModule.find("course_id = " + courseId + " AND site_id = " + siteId + " AND module = 'survey' AND module_id = " + surveyId + " AND status = 1");
if(!minfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 설문이 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet sinfo = survey.find("id = " + surveyId + " AND site_id = " + siteId + " AND status != -1");
if(!sinfo.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "설문 정보가 없습니다.");
	result.print();
	return;
}

f.addElement("survey_nm", sinfo.s("survey_nm"), "hname:'설문명', required:'Y'");
f.addElement("content", sinfo.s("content"), "hname:'설문설명', allowhtml:'Y'");
f.addElement("anonymous_yn", minfo.s("result_yn"), "hname:'익명여부'");
f.addElement("start_date", m.time("yyyy-MM-dd", minfo.s("start_date")), "hname:'설문시작일'");
f.addElement("end_date", m.time("yyyy-MM-dd", minfo.s("end_date")), "hname:'설문종료일'");
f.addElement("chapter", minfo.i("chapter"), "hname:'차시', option:'number'");

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

String anonymousYn = "Y".equals(f.get("anonymous_yn")) ? "Y" : "N";
String applyType = "R".equals(cinfo.s("course_type")) ? "1" : "2";
String startDateTime = "";
String endDateTime = "";
int chapter = 0;

if("1".equals(applyType)) {
	String startYmd = m.time("yyyyMMdd", f.get("start_date"));
	String endYmd = m.time("yyyyMMdd", f.get("end_date"));
	if("".equals(startYmd) || "".equals(endYmd)) {
		result.put("rst_code", "1004");
		result.put("rst_message", "정규과정 설문은 시작일/종료일을 올바르게 입력해야 합니다.");
		result.print();
		return;
	}

	startDateTime = !"".equals(startYmd) ? (startYmd + "000000") : "";
	endDateTime = !"".equals(endYmd) ? (endYmd + "235959") : "";
	chapter = 0;
} else {
	chapter = Math.max(0, f.getInt("chapter"));
}

m.log(
	"survey_modify",
	"modify_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
	+ ", anonymous_yn=" + anonymousYn
	+ ", apply_type=" + applyType
);

survey.item("survey_nm", f.get("survey_nm"));
survey.item("content", f.get("content"));
if(!survey.update("id = " + surveyId + " AND site_id = " + siteId + " AND status != -1")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "설문 수정 중 오류가 발생했습니다.");
	result.print();
	return;
}

courseModule.item("module_nm", f.get("survey_nm"));
courseModule.item("assign_score", 0);
courseModule.item("apply_type", applyType);
courseModule.item("start_day", 0);
courseModule.item("period", 0);
courseModule.item("start_date", startDateTime);
courseModule.item("end_date", endDateTime);
courseModule.item("chapter", chapter);
courseModule.item("result_yn", anonymousYn);
if(!courseModule.update("course_id = " + courseId + " AND site_id = " + siteId + " AND module = 'survey' AND module_id = " + surveyId + " AND status = 1")) {
	result.put("rst_code", "2001");
	result.put("rst_message", "과목 설문 설정 수정 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"survey_modify",
	"modify_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
	+ ", anonymous_yn=" + anonymousYn
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", surveyId);
result.print();

%>
