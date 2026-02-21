<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 과제 피드백(첨삭본) 파일을 업로드할 수 있어야, 텍스트 피드백 외에 실제 수정본 전달이 가능합니다.
//- 정규/비정규 모두 같은 과제-수강키(course_id/homework_id/course_user_id)로 저장하면 공통으로 처리할 수 있습니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

// 왜: multipart/form-data에서는 m.ri()로 값이 비는 경우가 있어 Form(f) 값을 우선 사용합니다.
int courseId = f.getInt("course_id");
if(0 == courseId) courseId = m.ri("course_id");
int homeworkId = f.getInt("homework_id");
if(0 == homeworkId) homeworkId = m.ri("homework_id");
int courseUserId = f.getInt("course_user_id");
if(0 == courseUserId) courseUserId = m.ri("course_user_id");

if(0 == courseId || 0 == homeworkId || 0 == courseUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
	result.print();
	return;
}

String module = "homework_feedback_" + homeworkId;
String now = m.time("yyyyMMddHHmmss");
int maxPostSize = 100; //MB
String allowExt = "jpg|jpeg|gif|png|pdf|hwp|hwpx|txt|doc|docx|xls|xlsx|ppt|pptx|zip|alz|7z|rar|egg|mp3|mp4|avi|mov";

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkDao homework = new HomeworkDao();
ClFileDao file = new ClFileDao();

m.log(
	"tutor_homework_feedback_file",
	"upload_start course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin
);

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_feedback_file", "upload_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 피드백 파일을 업로드할 권한이 없습니다.");
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
	m.log("tutor_homework_feedback_file", "upload_not_linked course_id=" + courseId + ", homework_id=" + homeworkId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet cuinfo = courseUser.find("id = " + courseUserId + " AND course_id = " + courseId + " AND site_id = " + siteId + " AND status IN (1,3)");
if(!cuinfo.next()) {
	m.log("tutor_homework_feedback_file", "upload_course_user_not_found course_id=" + courseId + ", course_user_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 수강 정보가 없습니다.");
	result.print();
	return;
}

f.addElement("feedback_file", "", "hname:'피드백 파일', required:'Y', allow:'" + allowExt + "'");
if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", f.errMsg);
	result.print();
	return;
}

if((maxPostSize * 1024L * 1024L) < f.getLong("filesize")) {
	result.put("rst_code", "1100");
	result.put("rst_message", "100MB를 초과하여 업로드할 수 없습니다.");
	result.print();
	return;
}

File uploaded = f.saveFile("feedback_file");
if(uploaded == null || null == f.getFileName("feedback_file")) {
	m.log("tutor_homework_feedback_file", "upload_save_failed module=" + module + ", module_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "2000");
	result.put("rst_message", "파일 업로드 중 오류가 발생했습니다.");
	result.print();
	return;
}

file.item("module", module);
file.item("module_id", courseUserId);
file.item("site_id", siteId);
file.item("file_nm", f.getFileName("feedback_file"));
file.item("filename", f.getFileName("feedback_file"));
file.item("filetype", f.getFileType("feedback_file"));
file.item("filesize", uploaded.length());
file.item("realname", uploaded.getName());
file.item("main_yn", "N");
file.item("reg_date", now);
file.item("status", 1);
if(!file.insert()) {
	m.log("tutor_homework_feedback_file", "upload_insert_failed module=" + module + ", module_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "2001");
	result.put("rst_message", "파일 정보 저장 중 오류가 발생했습니다.");
	result.print();
	return;
}

String safeRealname = m.replace(uploaded.getName(), "'", "''");
DataSet finfo = file.find(
	"module = '" + module + "' AND module_id = " + courseUserId + " AND site_id = " + siteId
	+ " AND realname = '" + safeRealname + "' AND status = 1",
	"*",
	"id DESC"
);
if(!finfo.next()) {
	m.log("tutor_homework_feedback_file", "upload_inserted_but_not_found module=" + module + ", module_id=" + courseUserId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "2002");
	result.put("rst_message", "업로드 결과 조회 중 오류가 발생했습니다.");
	result.print();
	return;
}

String fileId = finfo.s("id");
String ek = m.encrypt(fileId);
Hashtable<String, Object> data = new Hashtable<String, Object>();
data.put("id", finfo.i("id"));
data.put("filename", finfo.s("filename"));
data.put("ek", ek);
data.put("download_url", "/classroom/download_cl.jsp?id=" + fileId + "&ek=" + ek);

m.log(
	"tutor_homework_feedback_file",
	"upload_success file_id=" + finfo.i("id") + ", course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", request_user_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", data);
result.print();

%>
