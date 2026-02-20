<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 담당과목 교수자가 과목별 학생 문의를 "대화형(스레드형)"으로 조회/답변할 수 있어야 합니다.
//- 기존 Q&A(CL_POST/CL_BOARD) 데이터를 재사용해 DB 구조 변경 없이 채팅 API를 제공합니다.

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

String mode = m.rs("mode", "rooms");

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
ClBoardDao board = new ClBoardDao(siteId);
ClPostDao post = new ClPostDao();
UserDao user = new UserDao();

if(!isAdmin) {
	// 왜: 담당과목 기준과 동일하게 주/보조강사 + 과정담당자 + 개설자를 모두 권한으로 봐야 화면 간 권한 불일치가 줄어듭니다.
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
		m.log("tutor_course_chat", "permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId + ", mode=" + mode);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목 채팅을 조회할 권한이 없습니다.");
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

DataSet binfo = board.find("course_id = " + courseId + " AND site_id = " + siteId + " AND code = 'qna' AND status = 1");
if(!binfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "Q&A 게시판 정보가 없습니다.");
	result.print();
	return;
}

if("rooms".equals(mode)) {
	String keyword = m.rs("s_keyword");
	ArrayList<Object> params = new ArrayList<Object>();
	String where = "";
	if(!"".equals(keyword)) {
		where += " AND (q.subject LIKE ? OR q.content LIKE ? OR u.user_nm LIKE ? OR u.login_id LIKE ?) ";
		params.add("%" + keyword + "%");
		params.add("%" + keyword + "%");
		params.add("%" + keyword + "%");
		params.add("%" + keyword + "%");
	}

	DataSet list = post.query(
		" SELECT q.thread thread_id, q.id question_id, q.course_user_id, q.user_id student_user_id "
		+ " , q.subject, q.content question_content, q.proc_status, q.reg_date question_reg_date "
		+ " , u.user_nm student_user_nm, u.login_id student_login_id "
		+ " , lp.id last_post_id, lp.user_id last_user_id, lp.content last_content, lp.reg_date last_reg_date "
		+ " FROM " + post.table + " q "
		+ " INNER JOIN " + user.table + " u ON u.id = q.user_id "
		+ " LEFT JOIN " + post.table + " lp ON lp.id = ( "
			+ " SELECT MAX(x.id) "
			+ " FROM " + post.table + " x "
			+ " WHERE x.site_id = " + siteId
			+ " AND x.course_id = " + courseId
			+ " AND x.board_id = " + binfo.i("id")
			+ " AND x.thread = q.thread "
			+ " AND x.depth LIKE 'A%' "
			+ " AND x.display_yn = 'Y' "
			+ " AND x.status != -1 "
		+ " ) "
		+ " WHERE q.site_id = " + siteId
		+ " AND q.course_id = " + courseId
		+ " AND q.board_id = " + binfo.i("id")
		+ " AND q.depth = 'A' "
		+ " AND q.display_yn = 'Y' "
		+ " AND q.status != -1 "
		+ where
		+ " ORDER BY IFNULL(lp.id, q.id) DESC "
		, params.toArray()
	);

	while(list.next()) {
		String preview = !"".equals(list.s("last_content")) ? list.s("last_content") : list.s("question_content");
		list.put("preview_conv", m.cutString(m.htmlToText(preview), 200));
		list.put("question_reg_date_conv", !"".equals(list.s("question_reg_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("question_reg_date")) : "-");
		list.put("last_reg_date_conv", !"".equals(list.s("last_reg_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("last_reg_date")) : "-");
		list.put("waiting_answer", 1 != list.i("proc_status"));
		list.put("last_sender_role", list.i("student_user_id") == list.i("last_user_id") ? "student" : "professor");
	}

	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_count", list.size());
	result.put("rst_data", list);
	result.print();
	return;
}

if("messages".equals(mode)) {
	int threadId = m.ri("thread_id");
	if(0 == threadId) {
		result.put("rst_code", "1002");
		result.put("rst_message", "thread_id가 필요합니다.");
		result.print();
		return;
	}

	DataSet qinfo = post.find(
		"site_id = " + siteId
		+ " AND course_id = " + courseId
		+ " AND board_id = " + binfo.i("id")
		+ " AND thread = " + threadId
		+ " AND depth = 'A'"
		+ " AND display_yn = 'Y'"
		+ " AND status != -1"
	, "*", "id DESC", 1);
	if(!qinfo.next()) {
		result.put("rst_code", "4042");
		result.put("rst_message", "해당 채팅방이 없습니다.");
		result.print();
		return;
	}

	DataSet messages = post.query(
		" SELECT p.id, p.thread, p.depth, p.user_id, p.writer, p.subject, p.content, p.reg_date, p.mod_date "
		+ " , u.user_nm, u.login_id "
		+ " FROM " + post.table + " p "
		+ " LEFT JOIN " + user.table + " u ON u.id = p.user_id "
		+ " WHERE p.site_id = " + siteId
		+ " AND p.course_id = " + courseId
		+ " AND p.board_id = " + binfo.i("id")
		+ " AND p.thread = " + threadId
		+ " AND p.depth LIKE 'A%'"
		+ " AND p.display_yn = 'Y'"
		+ " AND p.status != -1 "
		+ " ORDER BY p.id ASC "
	);

	while(messages.next()) {
		boolean studentMessage = messages.i("user_id") == qinfo.i("user_id");
		messages.put("sender_role", studentMessage ? "student" : "professor");
		messages.put("mine", messages.i("user_id") == userId);
		messages.put("reg_date_conv", !"".equals(messages.s("reg_date")) ? m.time("yyyy.MM.dd HH:mm", messages.s("reg_date")) : "-");
	}

	qinfo.put("question_reg_date_conv", !"".equals(qinfo.s("reg_date")) ? m.time("yyyy.MM.dd HH:mm", qinfo.s("reg_date")) : "-");
	qinfo.put("answered", 1 == qinfo.i("proc_status"));

	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_data", messages);
	result.put("rst_thread", qinfo);
	result.print();
	return;
}

if("send".equals(mode)) {
	if(!m.isPost()) {
		result.put("rst_code", "4050");
		result.put("rst_message", "POST 방식만 허용됩니다.");
		result.print();
		return;
	}

	int threadId = m.ri("thread_id");
	if(0 == threadId) {
		result.put("rst_code", "1002");
		result.put("rst_message", "thread_id가 필요합니다.");
		result.print();
		return;
	}

	String content = m.rs("content");
	if("".equals(content)) {
		result.put("rst_code", "1003");
		result.put("rst_message", "content가 필요합니다.");
		result.print();
		return;
	}

	// 왜: data URI 이미지는 저장소 용량을 급격히 늘리고, 렌더 오류를 유발할 수 있어 차단합니다.
	if(-1 < content.indexOf("<img") && -1 < content.indexOf("data:image/") && -1 < content.indexOf("base64")) {
		result.put("rst_code", "1101");
		result.put("rst_message", "이미지는 첨부파일로 업로드해 주세요.");
		result.print();
		return;
	}

	int bytes = content.replace("\r\n", "\n").getBytes("UTF-8").length;
	if(60000 < bytes) {
		result.put("rst_code", "1102");
		result.put("rst_message", "내용은 60000바이트를 초과할 수 없습니다. (현재 " + bytes + "바이트)");
		result.print();
		return;
	}

	DataSet qinfo = post.find(
		"site_id = " + siteId
		+ " AND course_id = " + courseId
		+ " AND board_id = " + binfo.i("id")
		+ " AND thread = " + threadId
		+ " AND depth = 'A'"
		+ " AND display_yn = 'Y'"
		+ " AND status != -1"
	, "*", "id DESC", 1);
	if(!qinfo.next()) {
		result.put("rst_code", "4042");
		result.put("rst_message", "해당 채팅방이 없습니다.");
		result.print();
		return;
	}

	String now = m.time("yyyyMMddHHmmss");
	String nextDepth = post.getThreadDepth(qinfo.i("thread"), "A");
	int newId = post.getSequence();

	post.item("id", newId);
	post.item("site_id", siteId);
	post.item("course_id", courseId);
	post.item("course_user_id", qinfo.i("course_user_id"));
	post.item("board_cd", "qna");
	post.item("board_id", binfo.i("id"));
	post.item("thread", qinfo.i("thread"));
	post.item("depth", nextDepth);
	post.item("user_id", userId);
	post.item("writer", userName);
	post.item("subject", qinfo.s("subject"));
	post.item("content", content);
	post.item("hit_cnt", 0);
	post.item("comm_cnt", 0);
	post.item("file_cnt", 0);
	post.item("display_yn", "Y");
	post.item("notice_yn", "N");
	post.item("secret_yn", qinfo.s("secret_yn"));
	// 왜: 교수자가 답변을 남기면 학생 대기 상태를 즉시 해소해야 목록과 상세가 같은 상태를 보입니다.
	post.item("proc_status", 1);
	post.item("mod_date", now);
	post.item("reg_date", now);
	post.item("status", 1);
	if(!post.insert()) {
		m.log("tutor_course_chat", "send_insert_failed course_id=" + courseId + ", thread_id=" + threadId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "2000");
		result.put("rst_message", "메시지 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}

	post.execute(
		" UPDATE " + post.table
		+ " SET proc_status = 1, mod_date = '" + now + "' "
		+ " WHERE site_id = " + siteId + " AND id = " + qinfo.i("id")
	);

	m.log("tutor_course_chat", "send_success course_id=" + courseId + ", thread_id=" + threadId + ", post_id=" + newId + ", request_user_id=" + userId + ", site_id=" + siteId);

	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_data", newId);
	result.print();
	return;
}

result.put("rst_code", "1000");
result.put("rst_message", "mode가 올바르지 않습니다. (rooms/messages/send)");
result.print();

%>
