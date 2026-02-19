<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자 피드백 첨부 목록을 별도 API로 제공하면, 업로드/삭제 직후 목록만 빠르게 다시 불러올 수 있습니다.

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
int courseUserId = m.ri("course_user_id");

if(0 == courseId || 0 == homeworkId || 0 == courseUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkDao homework = new HomeworkDao();
ClFileDao file = new ClFileDao();

m.log(
	"tutor_homework_feedback_file",
	"list_start course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin
);

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_file", "list_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 피드백 파일을 조회할 권한이 없습니다.");
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
	m.log("tutor_homework_feedback_file", "list_not_linked course_id=" + courseId + ", homework_id=" + homeworkId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet cuinfo = courseUser.find("id = " + courseUserId + " AND course_id = " + courseId + " AND site_id = " + siteId + " AND status IN (1,3)");
if(!cuinfo.next()) {
	m.log("tutor_homework_feedback_file", "list_course_user_not_found course_id=" + courseId + ", course_user_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 수강 정보가 없습니다.");
	result.print();
	return;
}

ArrayList<Hashtable<String, Object>> rows = new ArrayList<Hashtable<String, Object>>();
DataSet files = file.find("module = 'homework_feedback_" + homeworkId + "' AND module_id = " + courseUserId + " AND site_id = " + siteId + " AND status = 1", "*", "id ASC");
while(files.next()) {
	Hashtable<String, Object> row = new Hashtable<String, Object>();
	String fileId = files.s("id");
	String ek = m.encrypt(fileId);
	row.put("id", files.i("id"));
	row.put("filename", files.s("filename"));
	row.put("ek", ek);
	row.put("download_url", "/classroom/download_cl.jsp?id=" + fileId + "&ek=" + ek);
	rows.add(row);
}

m.log(
	"tutor_homework_feedback_file",
	"list_success course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", request_user_id=" + userId + ", site_id=" + siteId + ", file_count=" + rows.size()
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", rows);
result.print();

%>
