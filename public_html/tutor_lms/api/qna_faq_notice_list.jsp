<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 Q&A에서 자주 나오는 질문을 "FAQ 공지"로 묶어 보여주기 위해 목록 API가 필요합니다.
//- FAQ 공지는 강의실 공지 게시판(CL_POST.board_cd='notice') 중 notice_yn='Y' 데이터로 관리합니다.

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

String keyword = m.rs("s_keyword");

CourseTutorDao courseTutor = new CourseTutorDao();
CourseDao course = new CourseDao();
ClBoardDao board = new ClBoardDao(siteId);
ClPostDao post = new ClPostDao();
UserDao user = new UserDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 FAQ 공지를 조회할 권한이 없습니다.");
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

DataSet binfo = board.find("course_id = " + courseId + " AND site_id = " + siteId + " AND code = 'notice' AND status = 1");
if(!binfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "공지 게시판 정보가 없습니다.");
	result.print();
	return;
}

m.log(
	"qna_faq_notice_list",
	"list_request user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", keyword_len=" + keyword.length()
);

ArrayList<Object> params = new ArrayList<Object>();
String where = "";
if(!"".equals(keyword)) {
	where += " AND (a.subject LIKE ? OR a.content LIKE ? OR IFNULL(u.user_nm, '') LIKE ? OR IFNULL(u.login_id, '') LIKE ?) ";
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
	params.add("%" + keyword + "%");
}

DataSet list = post.query(
	" SELECT a.id faq_id, a.subject question, a.content answer, a.reg_date, a.mod_date "
	+ " , a.hit_cnt, a.display_yn, a.notice_yn "
	+ " , IFNULL(u.user_nm, '') user_nm, IFNULL(u.login_id, '') login_id "
	+ " FROM " + post.table + " a "
	+ " LEFT JOIN " + user.table + " u ON u.id = a.user_id "
	+ " WHERE a.site_id = " + siteId + " AND a.course_id = " + courseId + " "
	+ " AND a.board_id = " + binfo.i("id") + " AND a.board_cd = 'notice' "
	+ " AND a.depth = 'A' AND a.notice_yn = 'Y' AND a.display_yn = 'Y' AND a.status != -1 "
	+ where
	+ " ORDER BY a.thread DESC, a.id DESC "
	, params.toArray()
);

while(list.next()) {
	list.put("reg_date_conv", !"".equals(list.s("reg_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("reg_date")) : "-");
	list.put("mod_date_conv", !"".equals(list.s("mod_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("mod_date")) : "-");
	list.put("question_conv", m.cutString(m.htmlToText(list.s("question")), 120));
}

m.log(
	"qna_faq_notice_list",
	"list_done user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", count=" + list.size()
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
