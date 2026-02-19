<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과제 피드백 입력 시 자주 쓰는 문구를 빠르게 선택할 수 있도록 템플릿 목록을 조회합니다.

int courseId = m.ri("course_id");
int managerId = m.ri("manager_id");
int limit = m.ri("limit");

if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}
if(limit < 0) {
	result.put("rst_code", "1100");
	result.put("rst_message", "limit은 0 이상이어야 합니다.");
	result.print();
	return;
}

if(!isAdmin && 0 < managerId && managerId != userId) {
	result.put("rst_code", "4032");
	result.put("rst_message", "다른 교수자의 템플릿을 조회할 권한이 없습니다.");
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
	"list_start course_id=" + courseId + ", manager_id=" + managerId + ", request_user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin + ", limit=" + limit
);

if(0 >= course.findCount("id = " + courseId + " AND site_id = " + siteId + " AND status != -1")) {
	m.log("tutor_homework_feedback_template", "list_course_not_found course_id=" + courseId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_template", "list_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 피드백 템플릿을 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

String limitClause = 0 < limit ? " LIMIT " + limit : "";
DataSet list = homeworkFeedbackTemplate.query(
	" SELECT id, course_id, manager_id, sort, content, reg_date, mod_date "
	+ " FROM " + homeworkFeedbackTemplate.table + " "
	+ " WHERE site_id = " + siteId + " AND course_id = " + courseId + " AND manager_id = " + managerId + " AND status = 1 "
	+ " ORDER BY sort ASC, id ASC "
	+ limitClause
);

while(list.next()) {
	list.put("content_preview", m.cutString(m.stripTags(list.s("content")), 80));
	list.put("reg_date_conv", !"".equals(list.s("reg_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("reg_date")) : "-");
	list.put("mod_date_conv", !"".equals(list.s("mod_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("mod_date")) : "-");
}

m.log(
	"tutor_homework_feedback_template",
	"list_success course_id=" + courseId + ", manager_id=" + managerId + ", count=" + list.size() + ", request_user_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
