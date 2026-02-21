<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목관리 > 과제 탭에서, 더 이상 사용하지 않는 과제를 과목에서 제거(배치 해제)해야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
if(0 == courseId || 0 == homeworkId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제를 삭제할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

DataSet hinfo = homework.find("id = " + homeworkId + " AND site_id = " + siteId + " AND status != -1");
if(!hinfo.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "과제 정보가 없습니다.");
	result.print();
	return;
}

DataSet minfo = courseModule.find("course_id = " + courseId + " AND module = 'homework' AND module_id = " + homeworkId + " AND status = 1");
if(!minfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

if(0 < homeworkUser.findCount("homework_id = " + homeworkId + " AND course_id = " + courseId + " AND status = 1")) {
	result.put("rst_code", "4090");
	result.put("rst_message", "제출/채점 내역이 있어 삭제할 수 없습니다.");
	result.print();
	return;
}

m.log("tutor_homework", "delete course_id=" + courseId + ", homework_id=" + homeworkId + ", user_id=" + userId);

if(!courseModule.delete("course_id = " + courseId + " AND module = 'homework' AND module_id = " + homeworkId + "")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

try {
	if(0 >= courseModule.findCount("module = 'homework' AND module_id = " + homeworkId + "")) {
		// 왜: 다른 과목에서도 더 이상 쓰지 않는 과제라면, 첨부파일도 함께 정리해 고아 파일을 남기지 않습니다.
		if(!"".equals(hinfo.s("homework_file"))) m.delFileRoot(m.getUploadPath(hinfo.s("homework_file")));
		homework.item("homework_file", "");
		homework.item("status", -1);
		homework.update("id = " + homeworkId + " AND site_id = " + siteId);
		m.log("tutor_homework", "delete_finalized course_id=" + courseId + ", homework_id=" + homeworkId + ", file_deleted=" + ("".equals(hinfo.s("homework_file")) ? "N" : "Y"));
	} else {
		m.log("tutor_homework", "delete_detached_only course_id=" + courseId + ", homework_id=" + homeworkId + ", user_id=" + userId);
	}
} catch(Exception ignore) {}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", homeworkId);
result.print();

%>

