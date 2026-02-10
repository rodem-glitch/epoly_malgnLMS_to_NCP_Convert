<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목관리 > 과제 등록(모달)에서, 과제(LM_HOMEWORK)와 과목 배치(LM_COURSE_MODULE)를 함께 생성해야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();

//필수값
f.addElement("course_id", null, "hname:'course_id', required:'Y'");
f.addElement("title", null, "hname:'과제 제목', required:'Y'");
f.addElement("description", null, "hname:'과제 설명', required:'Y', allowhtml:'Y'");
f.addElement("startDate", null, "hname:'제출 시작 날짜'");
f.addElement("startTime", null, "hname:'제출 시작 시간'");
f.addElement("dueDate", null, "hname:'마감 날짜', required:'Y'");
f.addElement("dueTime", null, "hname:'마감 시간', required:'Y'");
f.addElement("totalScore", 100, "hname:'배점', required:'Y', option:'number'");
f.addElement("homework_file", null, "hname:'첨부파일'");

//선택값
f.addElement("onoff_type", "N", "hname:'온오프라인구분'"); //왜: 과제는 기본적으로 온라인 제출을 가정합니다.

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

int courseId = f.getInt("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목에 과제를 등록할 권한이 없습니다.");
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

String title = f.get("title").trim();
String content = f.get("description");
int assignScore = Math.max(0, f.getInt("totalScore"));
String onoffType = !"".equals(f.get("onoff_type")) ? f.get("onoff_type") : "N";

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

String endYmd = m.time("yyyyMMdd", f.get("dueDate"));
String endHm = f.get("dueTime");
String endH = (endHm != null && 5 <= endHm.length()) ? endHm.substring(0, 2) : "23";
String endM = (endHm != null && 5 <= endHm.length()) ? endHm.substring(3, 5) : "59";
String endDateTime = endYmd + endH + endM + "59";

String startDateTime = m.time("yyyyMMddHHmmss");
String startDate = f.get("startDate");
String startTime = f.get("startTime");
// 왜: 교수자 화면에서 시작일시를 입력하면 그 값을 우선 적용하고, 값이 없으면 기존처럼 "지금"을 사용합니다.
if(!"".equals(startDate)) {
	String startYmd = m.time("yyyyMMdd", startDate);
	String startH = (startTime != null && 5 <= startTime.length()) ? startTime.substring(0, 2) : "00";
	String startM = (startTime != null && 5 <= startTime.length()) ? startTime.substring(3, 5) : "00";
	startDateTime = startYmd + startH + startM + "00";
}

if(m.parseLong(startDateTime) > m.parseLong(endDateTime)) {
	result.put("rst_code", "1103");
	result.put("rst_message", "제출 시작일시는 마감일시보다 늦을 수 없습니다.");
	result.print();
	return;
}

m.log("tutor_homework", "insert course_id=" + courseId + ", start=" + startDateTime + ", end=" + endDateTime + ", user_id=" + userId);

//과제(LM_HOMEWORK) 생성
int newId = homework.getSequence();
homework.item("id", newId);
homework.item("site_id", siteId);
homework.item("onoff_type", onoffType);
homework.item("category_id", 0);
homework.item("homework_nm", title);
homework.item("content", content);
homework.item("manager_id", userId);
homework.item("reg_date", m.time("yyyyMMddHHmmss"));
homework.item("status", 1);
if(null != f.getFileName("homework_file")) {
	File f1 = f.saveFile("homework_file");
	if(f1 != null) homework.item("homework_file", f.getFileName("homework_file"));
}
if(!homework.insert()) {
	result.put("rst_code", "2000");
	result.put("rst_message", "과제 저장 중 오류가 발생했습니다.");
	result.print();
	return;
}

//과목 배치(LM_COURSE_MODULE) 생성
courseModule.item("course_id", courseId);
courseModule.item("site_id", siteId);
courseModule.item("module", "homework");
courseModule.item("module_id", newId);
courseModule.item("module_nm", title);
courseModule.item("parent_id", 0);
courseModule.item("item_type", "R");
courseModule.item("assign_score", assignScore);
courseModule.item("apply_type", "1");
courseModule.item("start_day", 0);
courseModule.item("period", 0);
courseModule.item("start_date", startDateTime);
courseModule.item("end_date", endDateTime);
courseModule.item("chapter", 0);
courseModule.item("retry_yn", "N");
courseModule.item("retry_score", 0);
courseModule.item("retry_cnt", 0);
courseModule.item("review_yn", "N");
courseModule.item("result_yn", "Y");
courseModule.item("status", 1);

if(!courseModule.insert()) {
	homework.item("status", -1);
	homework.update("id = " + newId);
	result.put("rst_code", "2001");
	result.put("rst_message", "과목 배치 저장 중 오류가 발생했습니다.");
	result.print();
	return;
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", newId);
result.print();

%>
