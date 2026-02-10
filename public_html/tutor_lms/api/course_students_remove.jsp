<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 수강생을 과목에서 제외(수강취소)할 수 있어야 합니다.
//- 레거시 관례상 삭제 대신 status=-4(수강취소)로 처리하는 경우가 많습니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int targetUserId = m.ri("user_id");
if(0 == courseId || 0 == targetUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id와 user_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 수강생을 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

courseUser.item("status", -4);
courseUser.item("mod_date", m.time("yyyyMMddHHmmss"));
boolean ok = courseUser.update(
	"course_id = " + courseId + " AND user_id = " + targetUserId + " AND site_id = " + siteId + " AND status NOT IN (-1, -4)"
);

if(!ok) {
	result.put("rst_code", "2000");
	result.put("rst_message", "수강생 제외 중 오류가 발생했습니다.");
	result.print();
	return;
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", targetUserId);
result.print();

%>
