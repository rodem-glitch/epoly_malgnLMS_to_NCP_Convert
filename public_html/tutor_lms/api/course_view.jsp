<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목 상세 화면(과목 정보 관리)에서 "소속 과정(프로그램)" 정보를 즉시 보여주기 위한 단건 조회 API입니다.

int id = m.ri("id");
if(0 == id) {
	result.put("rst_code", "1001");
	result.put("rst_message", "id(과목ID)가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
SubjectDao subject = new SubjectDao();

//왜: 교수자는 내 과목(주강사)만, 관리자는 전체 과목을 조회할 수 있어야 합니다.
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + id + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + id + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + id + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목을 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet info = course.query(
	" SELECT c.id, c.course_cd, c.course_nm, c.year, c.step, c.course_type, c.onoff_type, c.study_sdate, c.study_edate, c.request_sdate, c.request_edate, c.status "
	+ " , c.subject_id program_id, s.course_nm program_nm, s.start_date program_start_date, s.end_date program_end_date "
	+ " , (SELECT COUNT(*) FROM " + new CourseUserDao().table + " cu "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = c.id AND cu.status != -1) student_cnt "
	+ " FROM " + course.table + " c "
	+ " LEFT JOIN " + subject.table + " s ON s.id = c.subject_id AND s.site_id = " + siteId + " AND s.status != -1 "
	+ " WHERE c.id = " + id + " AND c.site_id = " + siteId + " AND c.status != -1 "
);

if(!info.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

String courseId = !"".equals(info.s("course_cd")) ? info.s("course_cd") : (info.i("id") + "");
info.put("course_id_conv", courseId);
info.put("subject_nm_conv", m.cutString(info.s("course_nm"), 100));
info.put("program_nm_conv", !"".equals(info.s("program_nm")) ? m.cutString(info.s("program_nm"), 100) : "-");

String ss = info.s("study_sdate");
String se = info.s("study_edate");
String ssConv = !"".equals(ss) ? m.time("yyyy.MM.dd", ss) : "";
String seConv = !"".equals(se) ? m.time("yyyy.MM.dd", se) : "";
info.put("period_conv", (!"".equals(ssConv) && !"".equals(seConv)) ? (ssConv + " - " + seConv) : "");

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", info);
result.print();

%>
