<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

int id = m.reqInt("id");
if(id == 0) { m.jsError(_message.get("alert.common.required_key")); return; }

//객체
HomeworkDao homework = new HomeworkDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();
HomeworkTaskDao homeworkTask = new HomeworkTaskDao();
CourseProgressDao courseProgress = new CourseProgressDao();
ClFileDao file = new ClFileDao();
HomeworkSimilarityResultDao homeworkSimilarity = new HomeworkSimilarityResultDao();

//정보
DataSet info = courseModule.query(
	"SELECT a.*, h.homework_nm, h.homework_file, h.content homework_content, h.onoff_type "
	+ ", u.user_id, u.submit_yn, u.score, u.marking_score, u.confirm_yn, u.mod_date, u.feedback "
	+ ", u.subject, u.content "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + homework.table + " h ON a.module_id = h.id AND h.status != -1 AND h.id = " + id + " "
	+ " LEFT JOIN " + homeworkUser.table + " u ON "
		+ "u.homework_id = a.module_id AND u.course_user_id = " + cuid + " "
	+ " WHERE a.status = 1 AND a.module = 'homework' "
	+ " AND a.course_id = " + courseId + " AND h.site_id = " + siteId + ""
);
if(!info.next()) { m.jsError(_message.get("alert.common.nodata")); return; };

//포맷팅
boolean exists = info.i("user_id") > 0;
boolean isReady = false; //대기
boolean isEnd = false; //완료
boolean isPeriodApply = "1".equals(info.s("apply_type"));
if("1".equals(info.s("apply_type"))) { //기간
	info.put("start_date_conv", m.time(_message.get("format.datetime.dot"), info.s("start_date")));
	info.put("end_date_conv",
		info.s("start_date").substring(0, 8).equals(info.s("end_date").substring(0, 8))
		? m.time("HH:mm", info.s("end_date"))
		: m.time(_message.get("format.datetime.dot"), info.s("end_date"))
	);

	// 왜: end_date를 99991231235959 같은 "아주 먼 미래"로 두는 환경에서는,
	//     diffDate("I") 내부에서 int 오버플로우가 나 종료로 잘못 판정되는 경우가 있습니다.
	//     그래서 yyyyMMddHHmmss 값을 숫자로 비교해 안전하게 판단합니다.
	long nowDateTime = m.parseLong(now);
	long startDateTime = m.parseLong(info.s("start_date"));
	long endDateTime = m.parseLong(info.s("end_date"));
	isReady = startDateTime > 0 && startDateTime > nowDateTime;
	isEnd = endDateTime > 0 && endDateTime < nowDateTime;

	info.put("apply_type_1", true);
	info.put("apply_type_2", false);
} else if("2".equals(info.s("apply_type"))) { //차시
	info.put("apply_conv", info.i("chapter") == 0 ? _message.get("classroom.module.before_study") : _message.get("classroom.module.after_study", new String[] { "chapter=>" + info.i("chapter") }));
	if(info.i("chapter") > 0 && 0 == courseProgress.findCount("course_id = " + courseId + " AND chapter = " + info.i("chapter") + " AND course_user_id = " + cuid + " AND complete_yn = 'Y'")) isReady = true;

	info.put("apply_type_1", false);
	info.put("apply_type_2", true);
}

String status = "-";
if(info.b("submit_yn")) status = _message.get("classroom.module.status.submit");
// 왜: 기간형 과제(apply_type=1)는 "과제 기간"이 제출 가능 조건이므로,
//     수강상태(progress=W 대기)만으로 제출을 막으면 학사/매핑 과정에서 "항상 대기"가 되는 문제가 생길 수 있습니다.
//     따라서 기간형은 isReady(과제 시작 전)만 대기로 처리하고, 차시형(apply_type=2)만 수강 대기(progress=W)를 반영합니다.
else if(isReady || ("W".equals(progress) && !isPeriodApply)) status = _message.get("classroom.module.status.waiting");
// 왜: 학기(progress)가 종료(E)라도, 기간(apply_type=1)형 과제는 종료일이 지나기 전까지는 제출을 허용해야 합니다.
//     (단, 차시(apply_type=2)형은 학기 종료와 함께 종료로 처리합니다.)
else if(isEnd || ("E".equals(progress) && !isPeriodApply)) status = _message.get("classroom.module.status.end");
else if("I".equals(progress) && info.i("user_id") != 0) status = _message.get("classroom.module.status.writing");
else status = "-";
info.put("status_conv", status);

info.put("mod_date_conv", info.b("submit_yn") ? m.time(_message.get("format.datetime.dot"), info.s("mod_date")) : _message.get("classroom.module.status.nosubmit"));
info.put("result_score", info.b("submit_yn")
		? (info.b("confirm_yn") ? info.d("marking_score") + _message.get("classroom.module.score") + " (" + _message.get("classroom.module.score_converted")
 + " : " + info.d("score") + _message.get("classroom.module.score") + ")" : _message.get("classroom.module.status.evaluating"))
		: "-"
);

// 왜: 제출 폼 노출은 "과제 기간"을 최우선으로 봐야 합니다.
// - 기간형(apply_type=1): 과제 기간 내면 수강 상태와 무관하게 제출 가능(학사/숨김 매핑 과정의 progress 이슈 방지)
// - 차시형(apply_type=2): 수강중(I) 또는 복습중(R)일 때만 제출 가능
boolean canOpenByProgress = isPeriodApply ? true : ("I".equals(progress) || "R".equals(progress));
boolean isOpen = !isReady && !isEnd && canOpenByProgress && !info.b("confirm_yn") && "N".equals(info.s("onoff_type"));
info.put("open_block", isOpen);


//폼객체
f.addElement("subject", info.s("subject"), "hname:'제목', required:'Y'");
f.addElement("content", null, "hname:'내용', allowhtml:'Y'");

if(m.isPost() && f.validate()) {

	if(!isOpen) { m.jsAlert(_message.get("alert.common.abnormal_access")); return; }

	//제한-이미지URI및용량
	String content = f.get("content");
	int bytes = content.replace("\r\n", "\n").getBytes("UTF-8").length;
	if(-1 < content.indexOf("<img") && -1 < content.indexOf("data:image/") && -1 < content.indexOf("base64")) {
		m.jsAlert(_message.get("alert.board.attach_image"));
		return;
	}
	if(60000 < bytes) { m.jsAlert(_message.get("alert.board.over_capacity", new String[] {"maximum=>60000", "bytes=>" + bytes})); return; }

	homeworkUser.item("subject", f.get("subject"));
	homeworkUser.item("content", content);
	homeworkUser.item("confirm_yn", "N");
	homeworkUser.item("marking_score", 0);
	homeworkUser.item("score", 0);
	homeworkUser.item("ip_addr", userIp);
	homeworkUser.item("mod_date", m.time("yyyyMMddHHmmss"));

	if(!exists) {
		homeworkUser.item("homework_id", id);
		homeworkUser.item("course_user_id", cuid);
		homeworkUser.item("course_id", courseId);
		homeworkUser.item("user_id", userId);
		homeworkUser.item("site_id", siteId);
		homeworkUser.item("submit_yn", "Y");
		homeworkUser.item("reg_date", m.time("yyyyMMddHHmmss"));
		homeworkUser.item("status", 1);
		if(!homeworkUser.insert()) { m.jsAlert(_message.get("alert.common.error_insert")); return; }

	} else {
		if(!homeworkUser.update("homework_id = " + id + " AND course_user_id = " + cuid + ""))  {
			m.jsAlert(_message.get("alert.common.error_modify")); return;
		}
		courseUser.updateTotalScore(cuid);					//총점 업데이트
	}

	// 왜: 학생 제출/수정 직후 유사도 결과를 자동 갱신해야 교수자가 최신 의심쌍을 확인할 수 있습니다.
	Hashtable<String, Object> similarityOut = homeworkSimilarity.runIncrementalAnalysis(
		siteId,
		courseId,
		id,
		cuid,
		userId,
		70.0,
		"AUTO_SUBMIT"
	);
	if(!"Y".equals(similarityOut.get("success"))) {
		m.log(
			"homework_similarity",
			"auto_submit_failed course_id=" + courseId + ", homework_id=" + id + ", course_user_id=" + cuid + ", run_id=" + similarityOut.get("run_id")
			+ ", pair_total=" + similarityOut.get("pair_total") + ", pair_saved=" + similarityOut.get("pair_saved")
			+ ", user_id=" + userId + ", site_id=" + siteId + ", message=" + similarityOut.get("message")
		);
	}

	m.jsAlert("제출이 완료되었습니다.");
	m.jsReplace("homework.jsp?" + m.qs("id"), "parent");
	return;
}

//포맷팅
info.put("homework_content_conv", m.htt(info.s("homework_content")));
info.put("homework_file_conv", m.encode(info.s("homework_file")));
info.put("homework_file_ek", m.encrypt(info.s("homework_file") + m.time("yyyyMMdd")));
info.put("homework_file_ext", m.replace(file.getFileIcon(info.s("homework_file")), "../html/images/admin/ext/unknown.gif", "/common/images/ext/unknown.gif"));

info.put("content_conv", m.htt(info.s("content")));

info.put("feedback", info.b("confirm_yn") ? info.s("feedback") : "-");

//목록-파일
DataSet files = file.find("module = 'homework_" + id + "' AND module_id = " + cuid + " AND status = 1");
while(files.next()) {
	files.put("ext", m.replace(file.getFileIcon(files.s("filename")), "../html/images/admin/ext/unknown.gif", "/common/images/ext/unknown.gif"));
	files.put("ek", m.encrypt(files.s("id")));
	files.put("filename_conv", m.urlencode(Base64Coder.encode(files.s("filename"))));
}

//목록-평가의견파일
DataSet feedbackFiles = file.find("module = 'homework_feedback_" + id + "' AND module_id = " + cuid + " AND status = 1");
while(feedbackFiles.next()) {
	feedbackFiles.put("ext", m.replace(file.getFileIcon(feedbackFiles.s("filename")), "../html/images/admin/ext/unknown.gif", "/common/images/ext/unknown.gif"));
	feedbackFiles.put("ek", m.encrypt(feedbackFiles.s("id")));
	feedbackFiles.put("filename_conv", m.urlencode(Base64Coder.encode(feedbackFiles.s("filename"))));
}

//추가과제 목록
//왜: 기본 과제(LM_HOMEWORK_USER)는 1회 제출/피드백 구조라서, 추가 과제는 별도 테이블에서 반복 이력을 보여줍니다.
DataSet taskList = homeworkTask.find(
	"site_id = " + siteId + " AND course_id = " + courseId + " AND homework_id = " + id + " AND course_user_id = " + cuid + " AND status = 1"
	, "*"
	, "id ASC"
);
while(taskList.next()) {
	taskList.put("reg_date_conv", !"".equals(taskList.s("reg_date")) ? m.time(_message.get("format.datetime.dot"), taskList.s("reg_date")) : "-");
	taskList.put("task_conv", m.cutString(m.stripTags(taskList.s("task")), 60));
	taskList.put("submit_conv", taskList.b("submit_yn") ? _message.get("classroom.module.status.submit") : _message.get("classroom.module.status.nosubmit"));
	taskList.put("confirm_conv", taskList.b("confirm_yn") ? "피드백완료" : "피드백대기");
}

//출력
p.setLayout(ch);
p.setBody("classroom.homework_view");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("id"));
p.setVar("form_script", f.getScript());

p.setVar("modify", exists);
p.setVar(info);

p.setLoop("files", files);
p.setLoop("feedback_files", feedbackFiles);
p.setLoop("task_list", taskList);
p.display();

%>
