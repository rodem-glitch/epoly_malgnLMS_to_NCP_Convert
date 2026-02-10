<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- `project` 화면의 "담당과목" 목록에서, 내 과목과 소속 과정(프로그램) 정보를 같이 보여주기 위함입니다.

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
SubjectDao subject = new SubjectDao();

String keyword = m.rs("s_keyword");
String year = m.rs("year");
int tutorId = m.ri("tutor_id"); //관리자용 필터(선택)

ArrayList<Object> params = new ArrayList<Object>();
String where = "";

//년도 필터(빈 값이면 전체)
if(!"".equals(year)) {
	where += " AND c.year = ? ";
	params.add(year);
}

//검색(과목명/과정명/과목ID)
if(!"".equals(keyword)) {
	where += " AND (c.course_nm LIKE ? OR s.course_nm LIKE ? OR CAST(c.id AS CHAR) LIKE ?) ";
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
}

//왜: 교수자는 본인 과목만, 관리자는 전체(또는 특정 교수자) 과목을 조회할 수 있어야 합니다.
String accessWhere = "";
if(!isAdmin) {
	// 왜: 과정담당자만 지정된 과목도 담당과목 화면에서 누락되지 않게
	//     주강사/보조강사 + 과정담당자 + LM_COURSE.manager_id를 모두 권한 기준으로 봅니다.
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
	" SELECT c.id, c.course_cd, c.course_nm, c.year, c.step, c.course_type, c.onoff_type, c.study_sdate, c.study_edate, c.request_sdate, c.request_edate, c.status "
	+ " , c.subject_id program_id, s.course_nm program_nm, s.start_date program_start_date, s.end_date program_end_date "
	+ " , (SELECT COUNT(*) FROM " + new CourseUserDao().table + " cu "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = c.id AND cu.status != -1) student_cnt "
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
	//화면에 바로 쓰기 편하게 가공
	String courseId = !"".equals(list.s("course_cd")) ? list.s("course_cd") : (list.i("id") + "");
	list.put("course_id_conv", courseId);
	list.put("subject_nm_conv", m.cutString(list.s("course_nm"), 100));
	list.put("program_nm_conv", !"".equals(list.s("program_nm")) ? m.cutString(list.s("program_nm"), 100) : "-");

	//왜: 프론트 화면에서 "정규/상시", "온라인/집합/혼합"을 사람이 읽을 수 있게 보여주기 위함입니다.
	list.put("course_type_conv", m.getItem(list.s("course_type"), course.types));
	list.put("onoff_type_conv", m.getItem(list.s("onoff_type"), course.onoffTypes));

	String ss = list.s("study_sdate");
	String se = list.s("study_edate");
	String rs = list.s("request_sdate");
	String re = list.s("request_edate");

	String ssConv = !"".equals(ss) ? m.time("yyyy.MM.dd", ss) : "";
	String seConv = !"".equals(se) ? m.time("yyyy.MM.dd", se) : "";
	list.put("period_conv", (!"".equals(ssConv) && !"".equals(seConv)) ? (ssConv + " - " + seConv) : "");

	//상태(대기/신청기간/학습기간/종료) 계산
	String statusLabel = "대기";
	if(!"".equals(rs) && !"".equals(re) && 0 <= m.diffDate("D", rs, today) && 0 <= m.diffDate("D", today, re)) {
		statusLabel = "신청기간";
	} else if(!"".equals(ss) && !"".equals(se) && 0 <= m.diffDate("D", ss, today) && 0 <= m.diffDate("D", today, se)) {
		statusLabel = "학습기간";
	} else if(!"".equals(se) && 0 < m.diffDate("D", se, today)) {
		statusLabel = "종료";
	}
	list.put("status_label", statusLabel);
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
