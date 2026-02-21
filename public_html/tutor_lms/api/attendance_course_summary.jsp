<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자 출석 탭에서 "담당 과목 전체"를 한 번에 관리하려면,
//  과목별 결석 기준과 기준 초과 인원(자동 F/미수료 대상) 요약이 먼저 필요합니다.

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
SubjectDao subject = new SubjectDao();

String keyword = m.rs("s_keyword");
String year = m.rs("year");
int tutorId = m.ri("tutor_id"); //관리자용 필터(선택)

ArrayList<Object> params = new ArrayList<Object>();
String where = "";

if(!"".equals(year)) {
	where += " AND c.year = ? ";
	params.add(year);
}

if(!"".equals(keyword)) {
	where += " AND (c.course_nm LIKE ? OR s.course_nm LIKE ? OR CAST(c.id AS CHAR) LIKE ?) ";
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
}

String accessWhere = "";
if(!isAdmin) {
	accessWhere =
		" AND (c.manager_id = " + userId
		+ " OR EXISTS (SELECT 1 FROM " + courseTutor.table + " ct "
		+ " WHERE ct.course_id = c.id AND ct.user_id = " + userId + " AND ct.site_id = " + siteId
		+ " AND ct.type IN ('major', 'minor')) "
		+ " OR EXISTS (SELECT 1 FROM " + courseManager.table + " cm "
		+ " WHERE cm.course_id = c.id AND cm.user_id = " + userId + " AND cm.site_id = " + siteId + ")) ";
} else if(0 < tutorId) {
	accessWhere =
		" AND (c.manager_id = " + tutorId
		+ " OR EXISTS (SELECT 1 FROM " + courseTutor.table + " ct "
		+ " WHERE ct.course_id = c.id AND ct.user_id = " + tutorId + " AND ct.site_id = " + siteId
		+ " AND ct.type IN ('major', 'minor')) "
		+ " OR EXISTS (SELECT 1 FROM " + courseManager.table + " cm "
		+ " WHERE cm.course_id = c.id AND cm.user_id = " + tutorId + " AND cm.site_id = " + siteId + ")) ";
}

DataSet list = course.query(
	" SELECT c.id, c.course_cd, c.course_nm, c.year, c.step, c.course_type, c.onoff_type "
	+ " , c.study_sdate, c.study_edate, c.request_sdate, c.request_edate "
	+ " , c.limit_absence_yn, c.limit_absence_cnt "
	+ " , s.course_nm program_nm "
	+ " , (SELECT COUNT(*) FROM LM_COURSE_USER cu "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = c.id AND cu.status IN (1,3)) student_cnt "
	+ " , (SELECT COUNT(*) FROM LM_COURSE_LESSON cl "
		+ " WHERE cl.course_id = c.id AND cl.progress_yn = 'Y' AND cl.status = 1) lesson_cnt "
	+ " , (CASE WHEN c.limit_absence_yn = 'Y' AND c.limit_absence_cnt > 0 THEN ("
		+ " SELECT COUNT(*) FROM LM_COURSE_USER cu2 "
		+ " WHERE cu2.site_id = " + siteId + " AND cu2.course_id = c.id AND cu2.status IN (1,3) "
		+ " AND ("
			+ " (SELECT COUNT(*) FROM LM_COURSE_LESSON cl2 WHERE cl2.course_id = c.id AND cl2.progress_yn = 'Y' AND cl2.status = 1)"
			+ " - "
			+ " (SELECT COUNT(*) FROM LM_COURSE_PROGRESS cp2 WHERE cp2.course_user_id = cu2.id AND cp2.site_id = " + siteId + " AND cp2.complete_yn = 'Y' AND cp2.status = 1)"
		+ " ) >= c.limit_absence_cnt "
	+ " ) ELSE 0 END) at_risk_cnt "
	+ " FROM " + course.table + " c "
	+ " LEFT JOIN " + subject.table + " s ON s.id = c.subject_id AND s.site_id = " + siteId + " AND s.status != -1 "
	+ " WHERE c.site_id = " + siteId + " AND c.status != -1 AND c.onoff_type != 'P' "
	+ accessWhere
	+ where
	+ " ORDER BY c.id DESC "
	, params.toArray()
);

String today = m.time("yyyyMMdd");

while(list.next()) {
	String courseIdConv = !"".equals(list.s("course_cd")) ? list.s("course_cd") : (list.i("id") + "");
	list.put("course_id_conv", courseIdConv);
	list.put("course_type_conv", m.getItem(list.s("course_type"), course.types));
	list.put("onoff_type_conv", m.getItem(list.s("onoff_type"), course.onoffTypes));
	list.put("program_nm_conv", !"".equals(list.s("program_nm")) ? m.cutString(list.s("program_nm"), 100) : "-");

	String ss = list.s("study_sdate");
	String se = list.s("study_edate");
	String rs = list.s("request_sdate");
	String re = list.s("request_edate");

	String statusLabel = "대기";
	if(!"".equals(rs) && !"".equals(re) && 0 <= m.diffDate("D", rs, today) && 0 <= m.diffDate("D", today, re)) {
		statusLabel = "신청기간";
	} else if(!"".equals(ss) && !"".equals(se) && 0 <= m.diffDate("D", ss, today) && 0 <= m.diffDate("D", today, se)) {
		statusLabel = "학습기간";
	} else if(!"".equals(se) && 0 < m.diffDate("D", se, today)) {
		statusLabel = "종료";
	}
	list.put("status_label", statusLabel);
	list.put("absence_rule_yn", list.s("limit_absence_yn"));
	list.put("absence_limit_cnt", list.i("limit_absence_cnt"));
	list.put("at_risk_cnt_conv", m.nf(list.i("at_risk_cnt")));
	list.put("student_cnt_conv", m.nf(list.i("student_cnt")));
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
