<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 잘못 올린 첨부파일을 서버에서 안전하게 삭제할 수 있어야 피드백 수정 흐름이 완성됩니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
int courseUserId = m.ri("course_user_id");
int fileId = m.ri("file_id");

if(0 == courseId || 0 == homeworkId || 0 == courseUserId || 0 == fileId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id, course_user_id, file_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkDao homework = new HomeworkDao();
ClFileDao file = new ClFileDao();

String module = "homework_feedback_" + homeworkId;

m.log(
	"tutor_homework_feedback_file",
	"delete_start file_id=" + fileId + ", course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin
);

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_file", "delete_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 피드백 파일을 삭제할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet minfo = courseModule.query(
	" SELECT a.module_id "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + homework.table + " h ON a.module_id = h.id AND h.site_id = " + siteId + " AND h.status != -1 "
	+ " WHERE a.course_id = " + courseId + " AND a.module = 'homework' AND a.module_id = " + homeworkId + " AND a.status = 1 "
);
if(!minfo.next()) {
	m.log("tutor_homework_feedback_file", "delete_not_linked course_id=" + courseId + ", homework_id=" + homeworkId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet cuinfo = courseUser.find("id = " + courseUserId + " AND course_id = " + courseId + " AND site_id = " + siteId + " AND status IN (1,3)");
if(!cuinfo.next()) {
	m.log("tutor_homework_feedback_file", "delete_course_user_not_found course_id=" + courseId + ", course_user_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 수강 정보가 없습니다.");
	result.print();
	return;
}

DataSet finfo = file.find("id = " + fileId + " AND module = '" + module + "' AND module_id = " + courseUserId + " AND site_id = " + siteId + " AND status = 1");
if(!finfo.next()) {
	m.log("tutor_homework_feedback_file", "delete_file_not_found file_id=" + fileId + ", module=" + module + ", module_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4042");
	result.put("rst_message", "삭제할 피드백 파일이 없습니다.");
	result.print();
	return;
}

if(-1 == file.execute("DELETE FROM " + file.table + " WHERE id = " + fileId + " AND module = '" + module + "' AND module_id = " + courseUserId + " AND site_id = " + siteId)) {
	m.log("tutor_homework_feedback_file", "delete_failed file_id=" + fileId + ", module=" + module + ", module_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "2000");
	result.put("rst_message", "피드백 파일 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"tutor_homework_feedback_file",
	"delete_success file_id=" + fileId + ", course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", request_user_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", fileId);
result.print();

%>
