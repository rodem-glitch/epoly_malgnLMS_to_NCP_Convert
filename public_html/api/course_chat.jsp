<%@ page contentType="application/json; charset=utf-8" pageEncoding="utf-8" %><%@ include file="/init.jsp" %><%

//왜 필요한가:
//- 학생이 수강신청/수강 관련 문제를 과목 기준으로 교수자에게 대화형으로 문의할 수 있어야 합니다.
//- 기존 Q&A(CL_POST/CL_BOARD) 스레드를 재사용해 학생용 채팅 API를 제공합니다.

Json result = new Json(out);
result.put("rst_code", "9999");
result.put("rst_message", "올바른 접근이 아닙니다.");

if(0 == userId) {
	result.put("rst_code", "4010");
	result.put("rst_message", "로그인이 필요합니다.");
	result.print();
	return;
}

String mode = m.rs("mode", "rooms");
int courseId = m.ri("course_id");

CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
ClBoardDao board = new ClBoardDao(siteId);
ClPostDao post = new ClPostDao();
UserDao user = new UserDao();

if("rooms".equals(mode)) {
	String keyword = m.rs("s_keyword");
	ArrayList<Object> params = new ArrayList<Object>();
	String where = "";
	if(0 < courseId) {
		where += " AND q.course_id = " + courseId + " ";
	}
	if(!"".equals(keyword)) {
		where += " AND (q.subject LIKE ? OR q.content LIKE ? OR c.course_nm LIKE ?) ";
		params.add("%" + keyword + "%");
		params.add("%" + keyword + "%");
		params.add("%" + keyword + "%");
	}

	DataSet list = post.query(
		" SELECT q.thread thread_id, q.id question_id, q.course_id, q.course_user_id, q.subject, q.content question_content, q.proc_status, q.reg_date question_reg_date "
		+ " , c.course_nm "
		+ " , lp.id last_post_id, lp.user_id last_user_id, lp.content last_content, lp.reg_date last_reg_date "
		+ " FROM " + post.table + " q "
		+ " INNER JOIN " + course.table + " c ON c.id = q.course_id AND c.site_id = " + siteId + " AND c.status != -1 "
		+ " INNER JOIN " + board.table + " b ON b.id = q.board_id AND b.site_id = " + siteId + " AND b.code = 'qna' AND b.status = 1 "
		+ " LEFT JOIN " + post.table + " lp ON lp.id = ( "
			+ " SELECT MAX(x.id) "
			+ " FROM " + post.table + " x "
			+ " WHERE x.site_id = " + siteId
			+ " AND x.course_id = q.course_id "
			+ " AND x.board_id = q.board_id "
			+ " AND x.thread = q.thread "
			+ " AND x.depth LIKE 'A%' "
			+ " AND x.display_yn = 'Y' "
			+ " AND x.status != -1 "
		+ " ) "
		+ " WHERE q.site_id = " + siteId
		+ " AND q.user_id = " + userId
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
		list.put("answered", 1 == list.i("proc_status"));
		list.put("last_sender_role", list.i("last_user_id") == userId ? "student" : "professor");
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
		result.put("rst_code", "1001");
		result.put("rst_message", "thread_id가 필요합니다.");
		result.print();
		return;
	}

	DataSet qinfo = post.find(
		"site_id = " + siteId
		+ " AND user_id = " + userId
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

	if(0 >= board.findCount("id = " + qinfo.i("board_id") + " AND site_id = " + siteId + " AND code = 'qna' AND status = 1")) {
		result.put("rst_code", "4041");
		result.put("rst_message", "Q&A 게시판 정보가 없습니다.");
		result.print();
		return;
	}

	DataSet messages = post.query(
		" SELECT p.id, p.thread, p.depth, p.user_id, p.writer, p.subject, p.content, p.reg_date, p.mod_date "
		+ " , u.user_nm, u.login_id "
		+ " FROM " + post.table + " p "
		+ " LEFT JOIN " + user.table + " u ON u.id = p.user_id "
		+ " WHERE p.site_id = " + siteId
		+ " AND p.course_id = " + qinfo.i("course_id")
		+ " AND p.board_id = " + qinfo.i("board_id")
		+ " AND p.thread = " + threadId
		+ " AND p.depth LIKE 'A%' "
		+ " AND p.display_yn = 'Y' "
		+ " AND p.status != -1 "
		+ " ORDER BY p.id ASC "
	);

	while(messages.next()) {
		boolean studentMessage = messages.i("user_id") == userId;
		messages.put("sender_role", studentMessage ? "student" : "professor");
		messages.put("mine", studentMessage);
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

	String content = m.rs("content");
	if("".equals(content)) {
		result.put("rst_code", "1002");
		result.put("rst_message", "content가 필요합니다.");
		result.print();
		return;
	}

	// 왜: data URI 이미지는 본문 저장 시 DB 용량 급증/오류 원인이 되므로 차단합니다.
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

	int threadId = m.ri("thread_id");
	String now = m.time("yyyyMMddHHmmss");

	if(0 == threadId) {
		String subject = m.rs("subject");
		if(0 == courseId) {
			result.put("rst_code", "1003");
			result.put("rst_message", "신규 문의 생성 시 course_id가 필요합니다.");
			result.print();
			return;
		}
		if("".equals(subject)) {
			result.put("rst_code", "1004");
			result.put("rst_message", "신규 문의 생성 시 subject가 필요합니다.");
			result.print();
			return;
		}

		DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
		if(!cinfo.next()) {
			result.put("rst_code", "4040");
			result.put("rst_message", "해당 과목이 없습니다.");
			result.print();
			return;
		}

		DataSet cuinfo = courseUser.find(
			"site_id = " + siteId
			+ " AND course_id = " + courseId
			+ " AND user_id = " + userId
			+ " AND status != -1"
		, "id", "id DESC", 1);
		if(!cuinfo.next()) {
			m.log("student_course_chat", "create_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
			result.put("rst_code", "4031");
			result.put("rst_message", "해당 과목에 문의를 남길 권한이 없습니다.");
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

		int newId = post.getSequence();
		int newThread = post.getLastThread();
		post.item("id", newId);
		post.item("site_id", siteId);
		post.item("course_id", courseId);
		post.item("course_user_id", cuinfo.i("id"));
		post.item("board_cd", "qna");
		post.item("board_id", binfo.i("id"));
		post.item("thread", newThread);
		post.item("depth", "A");
		post.item("user_id", userId);
		post.item("writer", userName);
		post.item("subject", subject);
		post.item("content", content);
		post.item("hit_cnt", 0);
		post.item("comm_cnt", 0);
		post.item("file_cnt", 0);
		post.item("display_yn", "Y");
		post.item("notice_yn", "N");
		post.item("secret_yn", "Y");
		post.item("proc_status", 0);
		post.item("mod_date", now);
		post.item("reg_date", now);
		post.item("status", 1);
		if(!post.insert()) {
			m.log("student_course_chat", "create_insert_failed course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
			result.put("rst_code", "2000");
			result.put("rst_message", "문의 생성 중 오류가 발생했습니다.");
			result.print();
			return;
		}

		m.log("student_course_chat", "create_success course_id=" + courseId + ", thread_id=" + newThread + ", post_id=" + newId + ", request_user_id=" + userId + ", site_id=" + siteId);

		DataSet data = new DataSet();
		data.addRow();
		data.put("post_id", newId);
		data.put("thread_id", newThread);

		result.put("rst_code", "0000");
		result.put("rst_message", "성공");
		result.put("rst_data", data);
		result.print();
		return;
	}

	DataSet qinfo = post.find(
		"site_id = " + siteId
		+ " AND user_id = " + userId
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

	if(0 < courseId && courseId != qinfo.i("course_id")) {
		result.put("rst_code", "4032");
		result.put("rst_message", "thread_id와 course_id가 일치하지 않습니다.");
		result.print();
		return;
	}

	if(0 >= board.findCount("id = " + qinfo.i("board_id") + " AND site_id = " + siteId + " AND code = 'qna' AND status = 1")) {
		result.put("rst_code", "4041");
		result.put("rst_message", "Q&A 게시판 정보가 없습니다.");
		result.print();
		return;
	}

	String nextDepth = post.getThreadDepth(qinfo.i("thread"), "A");
	int newId = post.getSequence();
	post.item("id", newId);
	post.item("site_id", siteId);
	post.item("course_id", qinfo.i("course_id"));
	post.item("course_user_id", qinfo.i("course_user_id"));
	post.item("board_cd", "qna");
	post.item("board_id", qinfo.i("board_id"));
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
	// 왜: 학생이 추가 메시지를 남기면 "답변대기" 상태로 즉시 바꿔 교수자 목록과 상태를 맞춥니다.
	post.item("proc_status", 0);
	post.item("mod_date", now);
	post.item("reg_date", now);
	post.item("status", 1);
	if(!post.insert()) {
		m.log("student_course_chat", "send_insert_failed course_id=" + qinfo.i("course_id") + ", thread_id=" + threadId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "2001");
		result.put("rst_message", "메시지 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}

	post.execute(
		" UPDATE " + post.table
		+ " SET proc_status = 0, mod_date = '" + now + "' "
		+ " WHERE site_id = " + siteId + " AND id = " + qinfo.i("id")
	);

	m.log("student_course_chat", "send_success course_id=" + qinfo.i("course_id") + ", thread_id=" + threadId + ", post_id=" + newId + ", request_user_id=" + userId + ", site_id=" + siteId);

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
