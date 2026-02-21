<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- "수강생/진도/성적/수료" 화면은 과목(LM_COURSE)에 등록된 수강생(LM_COURSE_USER) 목록이 필요합니다.

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
UserDao user = new UserDao();

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 수강생을 조회할 권한이 없습니다.");
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

//왜: 비정규(A) 과정은 승인대기 상태면 영상 재생/진도 처리 화면에서 제외되는 케이스가 있어,
//     수강생 목록 조회 시점에 대기건을 자동 승인해 학습 불가 상태를 즉시 해소합니다.
if("A".equals(cinfo.s("course_type"))) {
	int pendingCnt = courseUser.findCount(
		"course_id = " + courseId
		+ " AND site_id = " + siteId
		+ " AND status IN (0, 2)"
	);
	if(pendingCnt > 0) {
		courseUser.clear();
		courseUser.item("status", 1);
		courseUser.item("change_date", m.time("yyyyMMddHHmmss"));
		if(courseUser.update(
			"course_id = " + courseId
			+ " AND site_id = " + siteId
			+ " AND status IN (0, 2)"
		)) {
			m.log("course_students_list", "auto_approve_non_regular course_id=" + courseId + ", approved_cnt=" + pendingCnt + ", user_id=" + userId);
		} else {
			m.log("course_students_list", "auto_approve_non_regular_failed course_id=" + courseId + ", pending_cnt=" + pendingCnt + ", user_id=" + userId);
		}
	}
}

String keyword = m.rs("s_keyword");

ArrayList<Object> params = new ArrayList<Object>();
String where = "";
if(!"".equals(keyword)) {
	where += " AND (u.user_nm LIKE ? OR u.login_id LIKE ? OR u.email LIKE ?) ";
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
}

DataSet list = courseUser.query(
	" SELECT cu.id course_user_id, cu.user_id, cu.course_id, cu.progress_ratio, cu.total_score, cu.complete_yn, cu.complete_status, cu.complete_no, cu.start_date, cu.end_date, cu.status "
	+ " , u.login_id, u.user_nm, u.email "
	+ " FROM " + courseUser.table + " cu "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id "
	+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status NOT IN (-1, -4) "
	+ where
	+ " ORDER BY cu.id DESC "
	, params.toArray()
);

while(list.next()) {
	list.put("student_id", list.s("login_id"));
	list.put("name", list.s("user_nm"));
	list.put("email", list.s("email"));
	list.put("progress", list.d("progress_ratio"));
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
