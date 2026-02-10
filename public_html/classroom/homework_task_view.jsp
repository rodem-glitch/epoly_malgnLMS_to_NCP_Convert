<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

int hid = m.reqInt("hid");
int tid = m.reqInt("tid");
if(hid == 0 || tid == 0) { m.jsError(_message.get("alert.common.required_key")); return; }

//객체
HomeworkDao homework = new HomeworkDao();
HomeworkTaskDao homeworkTask = new HomeworkTaskDao();
CourseProgressDao courseProgress = new CourseProgressDao();
ClFileDao file = new ClFileDao();

//정보-과제(제출 가능 기간 계산용)
DataSet hinfo = courseModule.query(
	"SELECT a.*, h.homework_nm, h.onoff_type "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + homework.table + " h ON a.module_id = h.id AND h.status != -1 AND h.id = " + hid + " "
	+ " WHERE a.status = 1 AND a.module = 'homework' "
	+ " AND a.course_id = " + courseId + " AND h.site_id = " + siteId + ""
);
if(!hinfo.next()) { m.jsError(_message.get("alert.common.nodata")); return; }

//정보-추가과제
DataSet info = homeworkTask.find(
	"id = " + tid + " AND site_id = " + siteId + " AND course_id = " + courseId + " AND homework_id = " + hid + " AND course_user_id = " + cuid + " AND status = 1"
);
if(!info.next()) { m.jsError(_message.get("alert.common.nodata")); return; }

//포맷팅(기본 과제의 기간/차시 규칙을 그대로 따라갑니다)
boolean isReady = false; //대기
boolean isEnd = false; //완료
boolean isPeriodApply = "1".equals(hinfo.s("apply_type"));
boolean isHaksaCourse = cuinfo.b("is_haksa");
boolean hasSubmitted = info.b("submit_yn");
if("1".equals(hinfo.s("apply_type"))) { //기간
	hinfo.put("start_date_conv", m.time(_message.get("format.datetime.dot"), hinfo.s("start_date")));
	hinfo.put("end_date_conv",
		hinfo.s("start_date").substring(0, 8).equals(hinfo.s("end_date").substring(0, 8))
		? m.time("HH:mm", hinfo.s("end_date"))
		: m.time(_message.get("format.datetime.dot"), hinfo.s("end_date"))
	);

	// 왜: end_date를 99991231235959 같은 "아주 먼 미래"로 두는 환경에서는,
	//     diffDate("I") 내부에서 int 오버플로우가 나 종료로 잘못 판정되는 경우가 있습니다.
	//     그래서 yyyyMMddHHmmss 값을 숫자로 비교해 안전하게 판단합니다.
	long nowDateTime = m.parseLong(now);
	long startDateTime = m.parseLong(hinfo.s("start_date"));
	long endDateTime = m.parseLong(hinfo.s("end_date"));
	isReady = startDateTime > 0 && startDateTime > nowDateTime;
	isEnd = endDateTime > 0 && endDateTime < nowDateTime;

	hinfo.put("apply_type_1", true);
	hinfo.put("apply_type_2", false);
} else if("2".equals(hinfo.s("apply_type"))) { //차시
	hinfo.put("apply_conv", hinfo.i("chapter") == 0 ? _message.get("classroom.module.before_study") : _message.get("classroom.module.after_study", new String[] { "chapter=>" + hinfo.i("chapter") }));
	if(hinfo.i("chapter") > 0 && 0 == courseProgress.findCount("course_id = " + courseId + " AND chapter = " + hinfo.i("chapter") + " AND course_user_id = " + cuid + " AND complete_yn = 'Y'")) isReady = true;

	hinfo.put("apply_type_1", false);
	hinfo.put("apply_type_2", true);
}

// 왜: 학기(progress)가 종료(E)라도, 기간(apply_type=1)형 과제는 종료일 전까지 제출/재제출을 허용합니다.
boolean canOpenByProgress = "I".equals(progress) || ("E".equals(progress) && isPeriodApply);

boolean isOpen = false;
boolean isResubmit = false;
if(isHaksaCourse) {
	// 왜: 학사(정규) 추가 과제는 "제출 완료 후 입력란 숨김"이 원칙입니다.
	//     제출 완료(submit_yn=Y) 상태에서는 교수 피드백(confirm_yn=Y)이 오기 전까지 입력란을 다시 열지 않습니다.
	isOpen = !hasSubmitted && !isReady && !isEnd && canOpenByProgress && !info.b("confirm_yn") && "N".equals(hinfo.s("onoff_type"));
	// 왜: 학사 과정에서는 교수 피드백이 온 건(confirm_yn=Y)에 대해서만 재제출 입력란을 노출합니다.
	isResubmit = hasSubmitted && info.b("confirm_yn") && canOpenByProgress && "N".equals(hinfo.s("onoff_type"));
} else {
	//왜: 기본 과제는 '평가완료(confirm_yn)' 이후 수정이 막히는데, 추가 과제도 같은 기준으로 제출/수정을 막아야 혼선이 없습니다.
	isOpen = !isReady && !isEnd && canOpenByProgress && !info.b("confirm_yn") && "N".equals(hinfo.s("onoff_type"));
	//왜: 피드백을 받은 후에는 기간이 종료되었더라도 재제출을 허용합니다.
	isResubmit = info.b("confirm_yn") && canOpenByProgress && "N".equals(hinfo.s("onoff_type"));
}

info.put("open_block", isOpen);
info.put("resubmit_block", isResubmit);

//폼객체
f.addElement("subject", info.s("subject"), "hname:'제목', required:'Y'");
f.addElement("content", null, "hname:'내용', allowhtml:'Y'");

//제출/수정
if(m.isPost() && f.validate()) {

	m.log("homework_task_view", "submit_attempt hid=" + hid + ", tid=" + tid + ", cuid=" + cuid + ", is_haksa=" + (isHaksaCourse ? "Y" : "N") + ", submit_yn=" + info.s("submit_yn") + ", confirm_yn=" + info.s("confirm_yn") + ", open=" + (isOpen ? "Y" : "N") + ", resubmit=" + (isResubmit ? "Y" : "N"));

	if(!isOpen && !isResubmit) { m.jsAlert(_message.get("alert.common.abnormal_access")); return; }

	String content = f.get("content");
	//제한-이미지URI및용량
	int bytes = content.replace("\r\n", "\n").getBytes("UTF-8").length;
	if(-1 < content.indexOf("<img") && -1 < content.indexOf("data:image/") && -1 < content.indexOf("base64")) {
		m.jsAlert(_message.get("alert.board.attach_image"));
		return;
	}
	if(60000 < bytes) { m.jsAlert(_message.get("alert.board.over_capacity", new String[] {"maximum=>60000", "bytes=>" + bytes})); return; }

	homeworkTask.item("subject", f.get("subject"));
	homeworkTask.item("content", content);
	homeworkTask.item("submit_yn", "Y");
	homeworkTask.item("submit_date", m.time("yyyyMMddHHmmss"));
	homeworkTask.item("ip_addr", userIp);
	homeworkTask.item("mod_date", m.time("yyyyMMddHHmmss"));
	
	//왜: 재제출 시 confirm_yn을 N으로 변경하여 교수자가 다시 평가할 수 있도록 합니다.
	if(isResubmit) {
		homeworkTask.item("confirm_yn", "N");
		homeworkTask.item("feedback", "");
		homeworkTask.item("confirm_date", "");
		homeworkTask.item("confirm_user_id", 0);
	}

	if(!homeworkTask.update("id = " + tid + " AND homework_id = " + hid + " AND course_user_id = " + cuid + "")) {
		m.jsAlert(_message.get("alert.common.error_modify")); return;
	}
	m.log("homework_task_view", "submit_done hid=" + hid + ", tid=" + tid + ", cuid=" + cuid + ", is_haksa=" + (isHaksaCourse ? "Y" : "N") + ", resubmit_submit=" + (isResubmit ? "Y" : "N"));

	m.jsAlert(isResubmit ? "재제출이 완료되었습니다." : "제출이 완료되었습니다.");
	m.jsReplace("homework_task_view.jsp?cuid=" + cuid + "&hid=" + hid + "&tid=" + tid, "parent");
	return;
}

//포맷팅
info.put("reg_date_conv", !"".equals(info.s("reg_date")) ? m.time(_message.get("format.datetime.dot"), info.s("reg_date")) : "-");
info.put("submit_date_conv", !"".equals(info.s("submit_date")) ? m.time(_message.get("format.datetime.dot"), info.s("submit_date")) : "-");
info.put("content_conv", m.htt(info.s("content")));
info.put("task_conv", m.htt(info.s("task")));
info.put("feedback", info.b("confirm_yn") ? info.s("feedback") : "-");

//목록-추가과제 첨부파일(부여)
DataSet assignFiles = file.find("module = 'homework_task_assign_" + tid + "' AND module_id = " + cuid + " AND status = 1");
while(assignFiles.next()) {
	assignFiles.put("ext", m.replace(file.getFileIcon(assignFiles.s("filename")), "../html/images/admin/ext/unknown.gif", "/common/images/ext/unknown.gif"));
	assignFiles.put("ek", m.encrypt(assignFiles.s("id")));
}

//목록-제출 첨부파일
DataSet submitFiles = file.find("module = 'homework_task_" + tid + "' AND module_id = " + cuid + " AND status = 1");
while(submitFiles.next()) {
	submitFiles.put("ext", m.replace(file.getFileIcon(submitFiles.s("filename")), "../html/images/admin/ext/unknown.gif", "/common/images/ext/unknown.gif"));
	submitFiles.put("ek", m.encrypt(submitFiles.s("id")));
}

//목록-피드백 첨부파일
DataSet feedbackFiles = file.find("module = 'homework_task_feedback_" + tid + "' AND module_id = " + cuid + " AND status = 1");
while(feedbackFiles.next()) {
	feedbackFiles.put("ext", m.replace(file.getFileIcon(feedbackFiles.s("filename")), "../html/images/admin/ext/unknown.gif", "/common/images/ext/unknown.gif"));
	feedbackFiles.put("ek", m.encrypt(feedbackFiles.s("id")));
}

//출력
p.setLayout(ch);
p.setBody("classroom.homework_task_view");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("tid,hid"));
p.setVar("form_script", f.getScript());

p.setVar("hid", hid);
p.setVar("tid", tid);
p.setVar("cuid", cuid);

p.setVar("homework", hinfo);
p.setVar("task", info);

p.setLoop("assign_files", assignFiles);
p.setLoop("submit_files", submitFiles);
p.setLoop("feedback_files", feedbackFiles);
p.display();

%>
