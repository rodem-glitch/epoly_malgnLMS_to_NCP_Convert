<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목 세부 관리 화면 상단의 "삭제" 버튼에서 과목을 운영 DB 기준으로 안전하게 삭제(상태 -1)해야 합니다.
//- 학사 연동 과목은 외부 기준값이 있으므로, 비정규(프리즘) 과목만 삭제 허용합니다.

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

m.log(
	"course_delete",
	"delete_start course_id=" + courseId
	+ ", request_user_id=" + userId
	+ ", site_id=" + siteId
	+ ", is_admin=" + isAdmin
);

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();
CoursePrecedeDao coursePrecede = new CoursePrecedeDao();
CourseLessonDao courseLesson = new CourseLessonDao();

DataSet info = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!info.next()) {
	m.log(
		"course_delete",
		"delete_not_found course_id=" + courseId
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없거나 이미 삭제되었습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	// 왜: 담당과목 화면과 같은 권한 기준(주/보조강사 + 과정담당자 + 개설자)을 적용해
	//     목록/상세/삭제의 권한 불일치를 막습니다.
	int tutorAccessCount = courseTutor.findCount(
		"course_id = " + courseId
		+ " AND user_id = " + userId
		+ " AND type IN ('major', 'minor')"
		+ " AND site_id = " + siteId
	);
	int managerAccessCount = courseManager.findCount(
		"course_id = " + courseId
		+ " AND user_id = " + userId
		+ " AND site_id = " + siteId
	);
	int ownerAccessCount = course.findCount(
		"id = " + courseId
		+ " AND manager_id = " + userId
		+ " AND site_id = " + siteId
		+ " AND status != -1"
	);
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		m.log(
			"course_delete",
			"delete_permission_denied course_id=" + courseId
			+ ", request_user_id=" + userId
			+ ", site_id=" + siteId
		);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목을 삭제할 권한이 없습니다.");
		result.print();
		return;
	}
}

// 왜: 학사 연동 과목은 원천 데이터가 LMS 밖에서 관리되므로, LMS에서 삭제를 허용하면 데이터 기준이 깨질 수 있습니다.
if("HAKSA_MAPPED".equals(info.s("etc2"))) {
	m.log(
		"course_delete",
		"delete_denied_haksa course_id=" + courseId
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "4032");
	result.put("rst_message", "학사 연동 과목은 삭제할 수 없습니다.");
	result.print();
	return;
}

int enrolledCount = courseUser.findCount(
	"course_id = " + courseId
	+ " AND site_id = " + siteId
	+ " AND status NOT IN (-1, -4)"
);
if(0 < enrolledCount) {
	m.log(
		"course_delete",
		"delete_blocked_enrolled course_id=" + courseId
		+ ", enrolled_count=" + enrolledCount
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "4091");
	result.put("rst_message", "수강생 정보가 있는 과목은 삭제할 수 없습니다.");
	result.print();
	return;
}

int precedeRefCount = coursePrecede.findCount("precede_id = " + courseId);
if(0 < precedeRefCount) {
	m.log(
		"course_delete",
		"delete_blocked_precede course_id=" + courseId
		+ ", precede_ref_count=" + precedeRefCount
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "4092");
	result.put("rst_message", "선행과정으로 지정된 과목은 삭제할 수 없습니다.");
	result.print();
	return;
}

course.item("course_file", "");
course.item("status", -1);
if(!course.update("id = " + courseId + " AND site_id = " + siteId + " AND status != -1")) {
	m.log(
		"course_delete",
		"delete_course_update_failed course_id=" + courseId
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "2000");
	result.put("rst_message", "과목 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

if(!coursePrecede.delete("course_id = " + courseId)) {
	m.log(
		"course_delete",
		"delete_precede_cleanup_failed course_id=" + courseId
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "2001");
	result.put("rst_message", "선행과정 정리 중 오류가 발생했습니다.");
	result.print();
	return;
}

courseLesson.item("status", -1);
if(!courseLesson.update("course_id = " + courseId + " AND status != -1")) {
	m.log(
		"course_delete",
		"delete_lesson_cleanup_failed course_id=" + courseId
		+ ", request_user_id=" + userId
		+ ", site_id=" + siteId
	);
	result.put("rst_code", "2002");
	result.put("rst_message", "차시 정리 중 오류가 발생했습니다.");
	result.print();
	return;
}

if(!"".equals(info.s("course_file"))) {
	m.delFileRoot(m.getUploadPath(info.s("course_file")));
}

m.log(
	"course_delete",
	"delete_ok course_id=" + courseId
	+ ", request_user_id=" + userId
	+ ", site_id=" + siteId
	+ ", enrolled_count=" + enrolledCount
);
result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", courseId);
result.print();

%>
