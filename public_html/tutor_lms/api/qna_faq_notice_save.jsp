<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 자주 나오는 질문을 FAQ 공지로 신규 등록/수정할 수 있어야 같은 질문 반복을 줄일 수 있습니다.
//- FAQ 공지는 강의실 공지 게시판(CL_POST.board_cd='notice') + notice_yn='Y'로 구분합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

f.addElement("course_id", null, "hname:'과목ID', required:'Y'");
f.addElement("subject", null, "hname:'질문', required:'Y'");
f.addElement("content", null, "hname:'답변내용', required:'Y', allowhtml:'Y'");
f.addElement("faq_id", "0", "hname:'FAQ아이디'");
f.addElement("display_yn", "Y", "hname:'노출여부'");

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

int courseId = f.getInt("course_id");
int faqId = f.getInt("faq_id");
String subject = f.get("subject");
String content = f.get("content");
String displayYn = "N".equals(f.get("display_yn")) ? "N" : "Y";

if(courseId <= 0) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
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
		result.put("rst_message", "해당 과목의 FAQ 공지를 저장할 권한이 없습니다.");
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

//왜: base64 이미지는 DB에 누적되면 용량 폭증/오류가 나기 쉽습니다.
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

String now = m.time("yyyyMMddHHmmss");
m.log(
	"qna_faq_notice_save",
	"save_start user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", faq_id=" + faqId
	+ ", subject_len=" + subject.length()
	+ ", content_bytes=" + bytes
);

int savedId = faqId;
if(0 < faqId) {
	DataSet info = post.find(
		"id = " + faqId + " AND site_id = " + siteId + " AND course_id = " + courseId
		+ " AND board_id = " + binfo.i("id")
		+ " AND board_cd = 'notice' AND depth = 'A' AND notice_yn = 'Y' AND status != -1"
	);
	if(!info.next()) {
		result.put("rst_code", "4042");
		result.put("rst_message", "수정할 FAQ 공지를 찾을 수 없습니다.");
		result.print();
		return;
	}

	post.item("writer", userName);
	post.item("subject", subject);
	post.item("content", content);
	post.item("public_yn", "Y");
	post.item("notice_yn", "Y");
	post.item("secret_yn", "N");
	post.item("display_yn", displayYn);
	// 왜: FAQ 공지는 "답변이 준비된 공지"여야 하므로 진행상태를 완료(1)로 고정합니다.
	post.item("proc_status", 1);
	post.item("mod_date", now);
	post.item("status", 1);
	if(!post.update("id = " + faqId + "")) {
		result.put("rst_code", "2000");
		result.put("rst_message", "FAQ 공지 수정 중 오류가 발생했습니다.");
		result.print();
		return;
	}
} else {
	savedId = post.getSequence();
	post.item("id", savedId);
	post.item("site_id", siteId);
	post.item("course_id", courseId);
	post.item("board_cd", "notice");
	post.item("board_id", binfo.i("id"));
	post.item("course_user_id", 0);
	post.item("thread", post.getLastThread());
	post.item("depth", "A");
	post.item("user_id", userId);
	post.item("writer", userName);
	post.item("subject", subject);
	post.item("content", content);
	post.item("point", 0);
	post.item("public_yn", "Y");
	post.item("notice_yn", "Y");
	post.item("secret_yn", "N");
	post.item("hit_cnt", 0);
	post.item("comm_cnt", 0);
	post.item("file_cnt", 0);
	post.item("display_yn", displayYn);
	post.item("proc_status", 1);
	post.item("mod_date", now);
	post.item("reg_date", now);
	post.item("status", 1);
	post.item("upload_file_key", "");
	if(!post.insert()) {
		result.put("rst_code", "2000");
		result.put("rst_message", "FAQ 공지 등록 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

m.log(
	"qna_faq_notice_save",
	"save_done user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", faq_id=" + savedId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", savedId);
result.print();

%>
