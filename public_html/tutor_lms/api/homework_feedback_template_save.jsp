<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 과목별 피드백 템플릿을 생성/수정해 반복 입력 시간을 줄이기 위함입니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int id = m.ri("id");
int courseId = m.ri("course_id");
int managerId = m.ri("manager_id");
int sort = m.ri("sort");
String content = m.rs("content");

if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}
if(sort < 0) {
	result.put("rst_code", "1100");
	result.put("rst_message", "sort는 0 이상이어야 합니다.");
	result.print();
	return;
}
if("".equals(content)) {
	result.put("rst_code", "1101");
	result.put("rst_message", "content는 필수입니다.");
	result.print();
	return;
}
if(content.length() > 2000) {
	result.put("rst_code", "1102");
	result.put("rst_message", "content는 2000자 이하로 입력해 주세요.");
	result.print();
	return;
}
//왜: base64 이미지를 템플릿에 넣으면 DB 용량이 급격히 커질 수 있어 저장 전에 차단합니다.
if(-1 < content.indexOf("<img") && -1 < content.indexOf("data:image/") && -1 < content.indexOf("base64")) {
	result.put("rst_code", "1103");
	result.put("rst_message", "이미지는 템플릿에 저장할 수 없습니다.");
	result.print();
	return;
}

if(!isAdmin && 0 < managerId && managerId != userId) {
	result.put("rst_code", "4032");
	result.put("rst_message", "다른 교수자의 템플릿을 저장할 권한이 없습니다.");
	result.print();
	return;
}
if(0 == managerId) managerId = userId;
if(!isAdmin) managerId = userId;

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
HomeworkFeedbackTemplateDao homeworkFeedbackTemplate = new HomeworkFeedbackTemplateDao();

if(!homeworkFeedbackTemplate.isValidSort(sort)) {
	result.put("rst_code", "1104");
	result.put("rst_message", "sort는 9999 이하여야 합니다.");
	result.print();
	return;
}

int normalizedSort = sort;
String now = m.time("yyyyMMddHHmmss");

m.log(
	"tutor_homework_feedback_template",
	"save_start id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", request_user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin + ", content_len=" + content.length() + ", sort=" + normalizedSort
);

if(0 >= course.findCount("id = " + courseId + " AND site_id = " + siteId + " AND status != -1")) {
	m.log("tutor_homework_feedback_template", "save_course_not_found course_id=" + courseId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_template", "save_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 피드백 템플릿을 저장할 권한이 없습니다.");
		result.print();
		return;
	}
}

int templateId = id;

if(0 < id) {
	DataSet info = homeworkFeedbackTemplate.find(
		"id = " + id + " AND site_id = " + siteId + " AND course_id = " + courseId + " AND manager_id = " + managerId + " AND status = 1"
	);
	if(!info.next()) {
		m.log("tutor_homework_feedback_template", "save_not_found id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", site_id=" + siteId);
		result.put("rst_code", "4041");
		result.put("rst_message", "수정할 피드백 템플릿이 없습니다.");
		result.print();
		return;
	}

	homeworkFeedbackTemplate.item("content", content);
	homeworkFeedbackTemplate.item("sort", normalizedSort);
	homeworkFeedbackTemplate.item("mod_date", now);

	if(!homeworkFeedbackTemplate.update("id = " + id + " AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_template", "save_update_failed id=" + id + ", course_id=" + courseId + ", manager_id=" + managerId + ", site_id=" + siteId);
		result.put("rst_code", "2000");
		result.put("rst_message", "피드백 템플릿 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
} else {
	templateId = homeworkFeedbackTemplate.getSequence();

	homeworkFeedbackTemplate.item("id", templateId);
	homeworkFeedbackTemplate.item("site_id", siteId);
	homeworkFeedbackTemplate.item("course_id", courseId);
	homeworkFeedbackTemplate.item("manager_id", managerId);
	homeworkFeedbackTemplate.item("sort", normalizedSort);
	homeworkFeedbackTemplate.item("content", content);
	homeworkFeedbackTemplate.item("reg_date", now);
	homeworkFeedbackTemplate.item("mod_date", now);
	homeworkFeedbackTemplate.item("status", 1);

	if(!homeworkFeedbackTemplate.insert()) {
		m.log("tutor_homework_feedback_template", "save_insert_failed id=" + templateId + ", course_id=" + courseId + ", manager_id=" + managerId + ", site_id=" + siteId);
		result.put("rst_code", "2000");
		result.put("rst_message", "피드백 템플릿 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

m.log(
	"tutor_homework_feedback_template",
	"save_success id=" + templateId + ", course_id=" + courseId + ", manager_id=" + managerId + ", request_user_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", templateId);
result.print();

%>
