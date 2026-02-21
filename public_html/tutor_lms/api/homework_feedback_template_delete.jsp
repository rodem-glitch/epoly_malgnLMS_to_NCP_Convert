<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 더 이상 쓰지 않는 피드백 템플릿을 정리할 수 있어야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int id = m.ri("id");
int courseId = m.ri("course_id");
int managerId = m.ri("manager_id");

if(0 == id || 0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "id, course_id가 필요합니다.");
	result.print();
	return;
}

if(!isAdmin && 0 < managerId && managerId != userId) {
	result.put("rst_code", "4032");
	result.put("rst_message", "다른 교수자의 템플릿을 삭제할 권한이 없습니다.");
	result.print();
	return;
}
if(0 == managerId) managerId = userId;
if(!isAdmin) managerId = userId;

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
HomeworkFeedbackTemplateDao homeworkFeedbackTemplate = new HomeworkFeedbackTemplateDao();

m.log(
	"tutor_homework_feedback_template",
	"delete_start id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", request_user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin
);

if(0 >= course.findCount("id = " + courseId + " AND site_id = " + siteId + " AND status != -1")) {
	m.log("tutor_homework_feedback_template", "delete_course_not_found course_id=" + courseId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_template", "delete_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 피드백 템플릿을 삭제할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet info = homeworkFeedbackTemplate.find(
	"id = " + id + " AND site_id = " + siteId + " AND course_id = " + courseId + " AND manager_id = " + managerId + " AND status = 1"
);
if(!info.next()) {
	m.log("tutor_homework_feedback_template", "delete_not_found id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", site_id=" + siteId);
	result.put("rst_code", "4041");
	result.put("rst_message", "삭제할 피드백 템플릿이 없습니다.");
	result.print();
	return;
}

homeworkFeedbackTemplate.item("status", -1);
homeworkFeedbackTemplate.item("mod_date", m.time("yyyyMMddHHmmss"));

if(!homeworkFeedbackTemplate.update("id = " + id + " AND site_id = " + siteId)) {
	m.log("tutor_homework_feedback_template", "delete_failed id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", site_id=" + siteId);
	result.put("rst_code", "2000");
	result.put("rst_message", "피드백 템플릿 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"tutor_homework_feedback_template",
	"delete_success id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", request_user_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", id);
result.print();

%>
