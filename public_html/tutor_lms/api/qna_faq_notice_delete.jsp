<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- FAQ 공지는 운영 중 내용이 바뀌거나 불필요해질 수 있어, 교수자가 안전하게 비활성화(삭제)할 수 있어야 합니다.
//- 실제 데이터 추적을 위해 물리삭제 대신 status=-1로 처리합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int faqId = m.ri("faq_id");
if(0 == courseId || 0 == faqId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, faq_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseDao course = new CourseDao();
ClBoardDao board = new ClBoardDao(siteId);
ClPostDao post = new ClPostDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 FAQ 공지를 삭제할 권한이 없습니다.");
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

DataSet info = post.find(
	"id = " + faqId + " AND site_id = " + siteId + " AND course_id = " + courseId
	+ " AND board_id = " + binfo.i("id")
	+ " AND board_cd = 'notice' AND depth = 'A' AND notice_yn = 'Y' AND status != -1"
);
if(!info.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "삭제할 FAQ 공지를 찾을 수 없습니다.");
	result.print();
	return;
}

String now = m.time("yyyyMMddHHmmss");
m.log(
	"qna_faq_notice_delete",
	"delete_request user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", faq_id=" + faqId
);

post.item("display_yn", "N");
post.item("status", -1);
post.item("mod_date", now);
if(!post.update("id = " + faqId + "")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "FAQ 공지 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"qna_faq_notice_delete",
	"delete_done user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", faq_id=" + faqId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", faqId);
result.print();

%>
