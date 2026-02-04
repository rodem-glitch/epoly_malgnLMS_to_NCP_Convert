<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//기본키
int id = m.ri("cuid");
String type = m.rs("type");
if(id == 0) { m.jsErrClose(_message.get("alert.common.required_key")); return; }

//증명서 발급 동의 체크
// 왜: 증명서/합격증에는 개인정보가 포함될 수 있으므로, 발급 시점에 별도 동의를 받습니다.
AgreementLogDao agreementLog = new AgreementLogDao(p, siteId);
String consentModule = "cert_20260120";
boolean certAgreed = "Y".equals(agreementLog.getOne(
	"SELECT agreement_yn FROM " + agreementLog.table
	+ " WHERE user_id = " + userId
	+ " AND type = 'cert'"
	+ " AND module = '" + consentModule + "'"
	+ " ORDER BY reg_date DESC"
));
if(!certAgreed) {
	String qs = m.qs("");
	String cur = request.getRequestURI() + ("".equals(qs) ? "" : "?" + qs);
	String pek = m.encrypt("PRIVACY_" + userId + "_AGREE_" + m.time("yyyyMMdd"));
	m.log("agreement_gate_" + siteId, "path=/mypage/certificate_course.jsp user_id=" + userId + " type=cert module=" + consentModule + " module_id=" + id);
	m.redirect("/member/privacy_agree.jsp?id=" + userId + "&ek=" + pek + "&ag=cert&mid=" + id + "&returl=" + m.urlencode(cur));
	return;
}

/*
if("pdf".equals(type)) {
	String url = "http://" + request.getServerName() + ":8080/mypage/certificate_course.jsp?cuid=" + id;
	if(-1 != request.getServerName().indexOf("ngv.malgn.co.kr")) url = "http://" + request.getServerName() + "/mypage/certificate_course.jsp?cuid=" + id;

	String path = dataDir + "/tmp/" + m.getUniqId() + ".pdf";
	String cmd = "/usr/local/bin/wkhtmltopdf -s A4 " + url + " " + path;
	m.exec(cmd);
	m.output(path, null);
	m.delFile(path);
	return;
}
*/

//객체
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
CourseLessonDao courseLesson = new CourseLessonDao();
LessonDao lesson = new LessonDao();
UserDeptDao userDept = new UserDeptDao();

TutorDao tutor = new TutorDao();
CourseTutorDao courseTutor = new CourseTutorDao();

OrderDao order = new OrderDao();
OrderItemDao orderItem = new OrderItemDao();

FileDao file = new FileDao();

//정보
DataSet info = courseUser.query(
	" SELECT a.*, b.course_nm, b.course_type, b.onoff_type, b.lesson_day, b.lesson_time, b.year, b.step, b.course_address, b.credit, b.etc1 course_etc1, b.etc2 course_etc2 "
	+ " , c.login_id, c.dept_id, c.user_nm, c.birthday, c.zipcode, c.new_addr, c.addr_dtl, c.email, c.gender, c.etc1, c.etc2, c.etc3, c.etc4, c.etc5 "
	+ " , d.dept_nm, o.pay_date, oi.pay_price, oi.refund_price "
	+ " , (SELECT COUNT(*) FROM " + courseLesson.table + " WHERE course_id = a.course_id AND status = 1) lesson_cnt "
	+ " FROM " + courseUser.table + " a "
	+ " INNER JOIN " + course.table + " b ON a.course_id = b.id "
	+ " LEFT JOIN " + orderItem.table + " oi ON a.order_item_id = oi.id AND oi.status IN (1, 3) "
	+ " LEFT JOIN " + order.table + " o ON a.order_id = o.id AND oi.order_id = o.id AND o.status IN (1, 3) "
	+ " INNER JOIN " + user.table + " c ON a.user_id = c.id AND c.status != -1 "
	+ " LEFT JOIN " + userDept.table + " d ON c.dept_id = d.id "
	+ " WHERE a.id = " + id + " "
	+ " AND a.user_id = " + userId + " AND a.status IN (1, 3) "
);
if(!info.next()) { m.jsErrClose(_message.get("alert.common.nodata")); return; }

//포맷팅
if(0 < info.i("dept_id")) {	
	info.put("dept_nm_conv", userDept.getNames(info.i("dept_id")));
} else {	
	info.put("dept_nm", "[미소속]");
	info.put("dept_nm_conv", "[미소속]");
}	

info.put("lesson_time_conv", m.nf((int)info.d("lesson_time")));

info.put("birthday_conv", m.time(_message.get("format.date.local"), info.s("birthday")));
info.put("birthday_conv2", m.time(_message.get("format.date.dot"), info.s("birthday")));
info.put("birthday_conv3", m.time(_message.get("format.dateshort.dot"), info.s("birthday")));

info.put("gender_conv", m.getValue(info.s("gender"), user.gendersMsg));

info.put("onoff_type_conv", m.getValue(info.s("onoff_type"), course.onoffTypesMsg));
info.put("start_date_conv", m.time(_message.get("format.date.local"), info.s("start_date")));
info.put("start_date_conv2", m.time(_message.get("format.date.dot"), info.s("start_date")));
info.put("start_date_conv3", m.time(_message.get("format.dateshort.dot"), info.s("start_date")));
info.put("end_date_year", m.time("yyyy", info.s("end_date")));
info.put("end_date_conv", m.time(_message.get("format.date.local"), info.s("end_date")));
info.put("end_date_conv2", m.time(_message.get("format.date.dot"), info.s("end_date")));
info.put("end_date_conv3", m.time(_message.get("format.dateshort.dot"), info.s("end_date")));
info.put("course_nm_conv", m.cutString(m.htmlToText(info.s("course_nm")), 48));

info.put("progress_ratio_conv", m.nf(info.d("progress_ratio"), 1));
info.put("total_score", m.nf(info.d("total_score"), 0));

if(!"".equals(info.s("pay_date"))) {
	info.put("pay_date_conv", m.time(_message.get("format.date.local"), info.s("pay_date")));
	info.put("pay_date_conv2", m.time(_message.get("format.date.dot"), info.s("pay_date")));
	info.put("pay_date_conv3", m.time(_message.get("format.dateshort.dot"), info.s("pay_date")));
} else {
	info.put("pay_date_conv", "-");
	info.put("pay_date_conv2", "-");
	info.put("pay_date_conv3", "-");
}

info.put("pay_price_conv", m.nf(info.i("pay_price") - info.i("refund_price")));
info.put("course_file_url", m.getUploadUrl(siteinfo.s("course_file")));

//강사
DataSet tutors = courseTutor.query(
	"SELECT t.*, u.display_yn "
	+ " FROM " + courseTutor.table + " a "
	+ " LEFT JOIN " + tutor.table + " t ON a.user_id = t.user_id "
	+ " LEFT JOIN " + user.table + " u ON t.user_id = u.id "
	+ " WHERE a.course_id = " + info.i("course_id") + " "
	+ " ORDER BY t.tutor_nm ASC "
);

//강의
DataSet lessons = courseLesson.query(
	" SELECT a.*, b.content_id, b.onoff_type, b.lesson_nm, b.lesson_type, b.total_time, b.complete_time "
	+ " FROM " + courseLesson.table + " a "
	+ " INNER JOIN " + lesson.table + " b ON a.lesson_id = b.id "
	+ " WHERE a.status = 1 AND a.course_id = " + info.i("course_id") + " "
	+ " ORDER BY a.chapter ASC "
);
while(lessons.next()) {
	lessons.put("start_date_conv", m.time(_message.get("format.date.local"), lessons.s("start_date")));
	lessons.put("start_date_conv2", m.time(_message.get("format.date.dot"), lessons.s("start_date")));
	lessons.put("start_date_conv3", m.time(_message.get("format.dateshort.dot"), lessons.s("start_date")));
	lessons.put("end_date_conv", m.time(_message.get("format.date.local"), lessons.s("end_date")));
	lessons.put("end_date_conv2", m.time(_message.get("format.date.dot"), lessons.s("end_date")));
	lessons.put("end_date_conv3", m.time(_message.get("format.dateshort.dot"), lessons.s("end_date")));
	lessons.put("lesson_hour", lessons.s("lesson_hour").replace(".00", ""));
	if(!"N".equals(info.s("onoff_type")) && "F".equals(lessons.s("onoff_type"))) {
		lessons.put("start_time_hour", lessons.s("start_time").substring(0,2));
		lessons.put("start_time_min", lessons.s("start_time").substring(2,4));
		lessons.put("end_time_hour", lessons.s("end_time").substring(0,2));
		lessons.put("end_time_min", lessons.s("end_time").substring(2,4));
	}
}

//정보-파일
DataSet files = file.getFileList(userId, "user", true);
while(files.next()) {
	files.put("image_block", -1 < files.s("filetype").indexOf("image/"));
	files.put("file_url", m.getUploadUrl(files.s("filename")));
}

//출력
p.setLayout(null);
p.setBody("page.certificate_course");

p.setVar(info);
p.setLoop("list", info);
p.setLoop("lessons", lessons);
p.setLoop("tutors", tutors);
p.setLoop("files", files);

p.setVar("certificate_no", m.time(_message.get("format.date.dot"), info.s("start_date")) + "-" + m.strrpad(id+"", 5, "0"));
p.setVar("today", m.time(_message.get("format.date.local")));
p.setVar("today2", m.time(_message.get("format.date.dot")));
p.setVar("today3", m.time(_message.get("format.dateshort.dot")));

p.display();

%>
