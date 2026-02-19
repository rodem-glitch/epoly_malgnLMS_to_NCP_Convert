<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목관리 > 과제 탭에서 과제 정보를 수정해야, 마감일/배점이 실제 DB에 반영됩니다.

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
f.addElement("homework_id", null, "hname:'homework_id', required:'Y'");
f.addElement("title", null, "hname:'과제 제목', required:'Y'");
f.addElement("description", null, "hname:'과제 설명', allowhtml:'Y'");
f.addElement("startDate", null, "hname:'제출 시작 날짜'");
f.addElement("startTime", null, "hname:'제출 시작 시간'");
f.addElement("dueDate", null, "hname:'마감 날짜', required:'Y'");
f.addElement("dueTime", null, "hname:'마감 시간', required:'Y'");
f.addElement("totalScore", 100, "hname:'배점', required:'Y', option:'number'");
f.addElement("onoff_type", "N", "hname:'온오프라인구분'");
f.addElement("homework_file", null, "hname:'첨부파일'");
f.addElement("delete_homework_file_yn", "N", "hname:'첨부파일삭제여부'");

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

int courseId = f.getInt("course_id");
int homeworkId = f.getInt("homework_id");
if(0 == courseId || 0 == homeworkId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id가 필요합니다.");
	result.print();
	return;
}

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 정보를 수정할 권한이 없습니다.");
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

DataSet minfo = courseModule.find("course_id = " + courseId + " AND module = 'homework' AND module_id = " + homeworkId + " AND status = 1");
if(!minfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet hinfo = homework.find("id = " + homeworkId + " AND site_id = " + siteId + " AND status != -1");
if(!hinfo.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "과제 정보가 없습니다.");
	result.print();
	return;
}

String title = f.get("title").trim();
String content = f.get("description");
int assignScore = Math.max(0, f.getInt("totalScore"));
String onoffType = !"".equals(f.get("onoff_type")) ? f.get("onoff_type") : hinfo.s("onoff_type");
boolean deleteHomeworkFile = "Y".equals(f.get("delete_homework_file_yn"));
boolean hasNewHomeworkFile = null != f.getFileName("homework_file");
String oldHomeworkFile = hinfo.s("homework_file");

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

String startDateTime = minfo.s("start_date");
if("".equals(startDateTime)) startDateTime = m.time("yyyyMMddHHmmss");
String startDate = f.get("startDate");
String startTime = f.get("startTime");
// 왜: 시작일시 입력값이 있으면 course_module.start_date를 함께 갱신해,
//      학생 화면의 제출 가능 기간 판단(대기/진행/종료)이 교수자 설정과 동일하게 맞춰집니다.
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

m.log("tutor_homework", "modify course_id=" + courseId + ", homework_id=" + homeworkId + ", start=" + startDateTime + ", end=" + endDateTime + ", user_id=" + userId);
m.log(
	"tutor_homework",
	"modify_file course_id=" + courseId + ", homework_id=" + homeworkId + ", delete_file=" + (deleteHomeworkFile ? "Y" : "N")
	+ ", has_new_file=" + (hasNewHomeworkFile ? "Y" : "N") + ", user_id=" + userId
);

//과제 수정
homework.item("homework_nm", title);
homework.item("onoff_type", onoffType);
homework.item("content", content);
boolean oldFileDeleted = false;
if(deleteHomeworkFile) {
	// 왜: "파일만 삭제" 요구가 있어, 수정 요청에서 명시적으로 첨부를 비울 수 있어야 합니다.
	homework.item("homework_file", "");
	if(!"".equals(oldHomeworkFile)) {
		m.delFileRoot(m.getUploadPath(oldHomeworkFile));
		oldFileDeleted = true;
	}
}
if(hasNewHomeworkFile) {
	File f1 = f.saveFile("homework_file");
	if(f1 != null) {
		homework.item("homework_file", f.getFileName("homework_file"));
		// 왜: 새 파일로 교체되면 기존 파일은 정리해 저장소를 지킵니다.
		if(!oldFileDeleted && !"".equals(oldHomeworkFile)) m.delFileRoot(m.getUploadPath(oldHomeworkFile));
	}
}
// 왜: 일부 환경(DB 스키마)에는 LM_HOMEWORK에 mod_date 컬럼이 없어 UPDATE가 통째로 실패합니다.
//     DB를 변경하지 않고 우선 저장이 되도록 mod_date 업데이트는 생략합니다.
if(!homework.update("id = " + homeworkId + " AND site_id = " + siteId + " AND status != -1")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "과제 수정 중 오류가 발생했습니다.");
	result.print();
	return;
}

//과목 배치 수정
courseModule.item("module_nm", title);
courseModule.item("assign_score", assignScore);
courseModule.item("apply_type", "1");
courseModule.item("start_date", startDateTime);
courseModule.item("end_date", endDateTime);
if(!courseModule.update("course_id = " + courseId + " AND module = 'homework' AND module_id = " + homeworkId + " AND status = 1")) {
	result.put("rst_code", "2001");
	result.put("rst_message", "과목 배치 수정 중 오류가 발생했습니다.");
	result.print();
	return;
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", homeworkId);
result.print();

%>

