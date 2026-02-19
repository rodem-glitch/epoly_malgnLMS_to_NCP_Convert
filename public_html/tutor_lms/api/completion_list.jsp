<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목관리 > 수료관리 탭에서, 학생별 수료/합격 상태(complete_status, close_yn 등)를 운영 DB 기준으로 보여주기 위함입니다.

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
UserDao user = new UserDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 수료정보를 조회할 권한이 없습니다.");
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

DataSet list = courseUser.query(
	" SELECT cu.id course_user_id, cu.user_id "
	+ " , u.login_id, u.user_nm "
	+ " , cu.start_date, cu.end_date, cu.progress_ratio, cu.total_score "
	+ " , cu.complete_status, cu.complete_yn, cu.complete_no, cu.complete_date "
	+ " , cu.close_yn, cu.close_date, cu.fail_reason "
	+ " FROM " + courseUser.table + " cu "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id AND u.status != -1 "
	+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status IN (1,3) "
	+ " ORDER BY u.user_nm ASC, cu.id ASC "
);

String today = m.time("yyyyMMdd");
while(list.next()) {
	list.put("progress_ratio_conv", m.nf(list.d("progress_ratio"), 1));
	list.put("total_score_conv", m.nf(list.d("total_score"), 2));
	list.put("complete_date_conv", !"".equals(list.s("complete_date")) ? m.time("yyyy.MM.dd", list.s("complete_date")) : "-");
	list.put("close_date_conv", !"".equals(list.s("close_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("close_date")) : "-");

	String status = "미판정";
	if("Y".equals(list.s("close_yn"))) status = "종료";
	else if("P".equals(list.s("complete_status"))) status = "합격";
	else if("C".equals(list.s("complete_status"))) status = "수료";
	else if("F".equals(list.s("complete_status"))) {
		// 왜: 정규과정에서 결석 기준으로 탈락한 경우는 운영 요청대로 "F"를 바로 보여줍니다.
		if("R".equals(cinfo.s("course_type")) && "absence_f".equals(list.s("fail_reason"))) status = "F";
		else status = "미수료";
	}
	else {
		if(!"".equals(list.s("start_date")) && 0 > m.diffDate("D", list.s("start_date"), today)) status = "대기";
		else if(!"".equals(list.s("end_date")) && 0 < m.diffDate("D", list.s("end_date"), today)) status = "학습종료";
		else status = "학습중";
	}
	list.put("status_label", status);
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.put("rst_course", cinfo);
result.print();

%>

