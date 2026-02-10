<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목을 특정 과정(프로그램)에 소속시키거나(연계), 소속을 해제할 수 있어야 합니다.
//- 과목은 소속 과정이 없을 수도 있으므로 program_id=0을 허용합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int programId = m.ri("program_id"); //0이면 소속 해제
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}
if(programId < 0) programId = 0;

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
SubjectDao subject = new SubjectDao();

//1) 교수자는 내 과목(주강사)인지 확인, 관리자는 전체 과목 수정 가능
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목을 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

//2) 연결하려는 과정이 존재/권한이 있는지 확인(해제는 제외)
if(0 < programId) {
	String whereOwner = !isAdmin ? (" AND user_id = " + userId + " ") : "";
	DataSet pinfo = subject.find("id = " + programId + " AND site_id = " + siteId + " AND status != -1 " + whereOwner);
	if(!pinfo.next()) {
		result.put("rst_code", "4032");
		result.put("rst_message", "해당 과정이 없거나 접근 권한이 없습니다.");
		result.print();
		return;
	}
}

//3) 반영
course.item("subject_id", programId);
if(!course.update("id = " + courseId + " AND site_id = " + siteId + " AND status != -1")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "저장 중 오류가 발생했습니다.");
	result.print();
	return;
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", courseId);
result.put("rst_program_id", programId);
result.print();

%>
