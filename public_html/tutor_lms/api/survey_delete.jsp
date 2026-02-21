<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목에서 더 이상 쓰지 않는 설문을 제거할 수 있어야 합니다.
//- 이미 응답이 존재하는 설문을 삭제하면 결과 추적이 끊기므로, 먼저 참여내역을 확인합니다.

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
SurveyUserDao surveyUser = new SurveyUserDao();
SurveyItemDao surveyItem = new SurveyItemDao();
SurveyQuestionDao surveyQuestion = new SurveyQuestionDao();

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
		result.put("rst_message", "해당 과목의 설문을 삭제할 권한이 없습니다.");
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

if(0 < surveyUser.findCount("survey_id = " + surveyId + " AND course_id = " + courseId + " AND site_id = " + siteId + " AND status = 1")) {
	result.put("rst_code", "4090");
	result.put("rst_message", "참여내역이 있어 삭제할 수 없습니다.");
	result.print();
	return;
}

m.log(
	"survey_delete",
	"delete_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
);

DataSet questionList = surveyItem.find("survey_id = " + surveyId + " AND site_id = " + siteId + " AND status = 1", "question_id");

if(!courseModule.delete("course_id = " + courseId + " AND site_id = " + siteId + " AND module = 'survey' AND module_id = " + surveyId + "")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "과목 설문 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

try {
	if(0 >= courseModule.findCount("module = 'survey' AND module_id = " + surveyId + " AND site_id = " + siteId + " AND status = 1")) {
		survey.item("status", -1);
		survey.update("id = " + surveyId + " AND site_id = " + siteId + " AND status != -1");

		surveyItem.item("status", -1);
		surveyItem.update("survey_id = " + surveyId + " AND site_id = " + siteId + " AND status != -1");

		while(questionList.next()) {
			int questionId = questionList.i("question_id");
			if(0 >= surveyItem.findCount("question_id = " + questionId + " AND site_id = " + siteId + " AND status = 1")) {
				surveyQuestion.item("status", -1);
				surveyQuestion.update("id = " + questionId + " AND site_id = " + siteId + " AND status != -1");
			}
		}
	}
} catch(Exception e) {
	m.log("survey_delete", "cleanup_warn survey_id=" + surveyId + ", msg=" + e.getMessage());
}

m.log(
	"survey_delete",
	"delete_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", surveyId);
result.print();

%>
