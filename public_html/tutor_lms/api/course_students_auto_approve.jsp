<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 비정규(A) 과정의 승인대기 수강생(status=0/2)을 즉시 승인해 영상 재생 불가 상태를 해소합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 수강생을 승인할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1", "id, course_type");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}
if(!"A".equals(cinfo.s("course_type"))) {
	result.put("rst_code", "1002");
	result.put("rst_message", "비정규 과정에서만 자동승인을 수행할 수 있습니다.");
	result.print();
	return;
}

int pendingCnt = courseUser.findCount(
	"course_id = " + courseId
	+ " AND site_id = " + siteId
	+ " AND status IN (0, 2)"
);
if(pendingCnt <= 0) {
	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_data", 0);
	result.put("rst_approved", 0);
	result.print();
	return;
}

courseUser.item("status", 1);
courseUser.item("change_date", m.time("yyyyMMddHHmmss"));
if(!courseUser.update(
	"course_id = " + courseId
	+ " AND site_id = " + siteId
	+ " AND status IN (0, 2)"
)) {
	m.log("course_students_auto_approve", "approve_failed course_id=" + courseId + ", pending_cnt=" + pendingCnt + ", user_id=" + userId);
	result.put("rst_code", "2000");
	result.put("rst_message", "자동승인 처리 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log("course_students_auto_approve", "approve_ok course_id=" + courseId + ", approved_cnt=" + pendingCnt + ", user_id=" + userId);
result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", pendingCnt);
result.put("rst_approved", pendingCnt);
result.print();

%>
