<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목별로 과제 제출/피드백과 Q&A 질문/답변을 한 번에 출력해야 합니다.
//- 화면에서 여러 탭을 옮겨 다니지 않아도 되도록 통합 데이터를 제공합니다.

int courseId = m.ri("course_id");
String startDate = m.rs("start_date");
String endDate = m.rs("end_date");
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
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();
HomeworkTaskDao homeworkTask = new HomeworkTaskDao();
ClBoardDao board = new ClBoardDao(siteId);
ClPostDao post = new ClPostDao();
UserDao user = new UserDao();

//권한
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 통합 출력 권한이 없습니다.");
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

String dateWhereHomework = "";
if(!"".equals(startDate)) {
	dateWhereHomework += " AND (IFNULL(hu.reg_date, '') = '' OR hu.reg_date >= '" + m.time("yyyyMMdd", startDate) + "000000') ";
}
if(!"".equals(endDate)) {
	dateWhereHomework += " AND (IFNULL(hu.reg_date, '') = '' OR hu.reg_date <= '" + m.time("yyyyMMdd", endDate) + "235959') ";
}

DataSet homeworkList = courseModule.query(
	" SELECT cm.course_id, cm.module_id homework_id "
	+ " , cu.id course_user_id "
	+ " , hu.submit_yn, hu.reg_date submit_date, hu.confirm_yn, hu.confirm_date, hu.marking_score, hu.score, hu.subject, hu.feedback "
	+ " , u.user_nm, u.login_id "
	+ " , h.homework_nm "
	+ " , (SELECT COUNT(*) FROM " + homeworkTask.table + " ht "
		+ " WHERE ht.site_id = " + siteId + " AND ht.course_id = cm.course_id "
		+ " AND ht.homework_id = h.id AND ht.course_user_id = cu.id AND ht.status = 1) task_cnt "
	+ " , (SELECT MAX(IFNULL(ht.submit_date, ht.reg_date)) FROM " + homeworkTask.table + " ht "
		+ " WHERE ht.site_id = " + siteId + " AND ht.course_id = cm.course_id "
		+ " AND ht.homework_id = h.id AND ht.course_user_id = cu.id AND ht.status = 1) last_task_date "
	+ " FROM " + courseModule.table + " cm "
	+ " INNER JOIN " + homework.table + " h ON h.id = cm.module_id AND h.site_id = " + siteId + " AND h.status != -1 "
	+ " INNER JOIN " + courseUser.table + " cu ON cu.course_id = cm.course_id AND cu.status IN (1,3) AND cu.site_id = " + siteId + " "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id AND u.status != -1 "
	+ " LEFT JOIN " + homeworkUser.table + " hu ON hu.course_user_id = cu.id AND hu.homework_id = h.id AND hu.status = 1 "
	+ " WHERE cm.course_id = " + courseId + " AND cm.module = 'homework' AND cm.status = 1 "
	+ dateWhereHomework
	+ " ORDER BY h.id ASC, u.user_nm ASC, hu.reg_date DESC "
);

while(homeworkList.next()) {
	boolean submitted = "Y".equals(homeworkList.s("submit_yn"));
	homeworkList.put("submitted_at", submitted ? m.time("yyyy.MM.dd HH:mm", homeworkList.s("submit_date")) : "-");
	homeworkList.put("confirmed", "Y".equals(homeworkList.s("confirm_yn")));
	homeworkList.put("confirm_at", !"".equals(homeworkList.s("confirm_date")) ? m.time("yyyy.MM.dd HH:mm", homeworkList.s("confirm_date")) : "-");
	homeworkList.put("marking_score_conv", m.nf(homeworkList.d("marking_score"), 0));
	homeworkList.put("score_conv", m.nf(homeworkList.d("score"), 2));
	homeworkList.put("student_id", homeworkList.s("login_id"));
	homeworkList.put("submitted", submitted);
	homeworkList.put("task_cnt", homeworkList.i("task_cnt"));
	homeworkList.put("last_task_date_conv", !"".equals(homeworkList.s("last_task_date")) ? m.time("yyyy.MM.dd HH:mm", homeworkList.s("last_task_date")) : "-");
}

DataSet qnaList = new DataSet();
DataSet binfo = board.find("course_id = " + courseId + " AND site_id = " + siteId + " AND code = 'qna' AND status = 1");
if(binfo.next()) {
	String dateWhereQna = "";
	if(!"".equals(startDate)) {
		dateWhereQna += " AND q.reg_date >= '" + m.time("yyyyMMdd", startDate) + "000000' ";
	}
	if(!"".equals(endDate)) {
		dateWhereQna += " AND q.reg_date <= '" + m.time("yyyyMMdd", endDate) + "235959' ";
	}

	qnaList = post.query(
		" SELECT q.id question_id, q.subject, q.content question_content, q.proc_status, q.reg_date question_reg_date "
		+ " , qu.user_nm question_user_nm, qu.login_id question_login_id "
		+ " , a.id answer_id, a.content answer_content, a.reg_date answer_reg_date "
		+ " , au.user_nm answer_user_nm, au.login_id answer_login_id "
		+ " FROM " + post.table + " q "
		+ " INNER JOIN " + user.table + " qu ON qu.id = q.user_id "
		+ " LEFT JOIN " + post.table + " a ON a.id = (SELECT MAX(id) FROM " + post.table + " "
			+ " WHERE thread = q.thread AND depth = 'AA' AND status != -1) "
		+ " LEFT JOIN " + user.table + " au ON au.id = a.user_id "
		+ " WHERE q.site_id = " + siteId + " AND q.course_id = " + courseId + " AND q.board_id = " + binfo.i("id") + " "
		+ " AND q.depth = 'A' AND q.display_yn = 'Y' AND q.status != -1 "
		+ dateWhereQna
		+ " ORDER BY q.reg_date DESC "
	);

	while(qnaList.next()) {
		qnaList.put("question_reg_date_conv", !"".equals(qnaList.s("question_reg_date")) ? m.time("yyyy.MM.dd HH:mm", qnaList.s("question_reg_date")) : "-");
		qnaList.put("answered", 1 == qnaList.i("proc_status"));
		qnaList.put("answer_reg_date_conv", !"".equals(qnaList.s("answer_reg_date")) ? m.time("yyyy.MM.dd HH:mm", qnaList.s("answer_reg_date")) : "-");
	}
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_course", cinfo);
result.put("rst_homework", homeworkList);
result.put("rst_qna", qnaList);
result.print();

%>
