<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//기본키
int id = m.ri("cuid");
String startDate = m.rs("start_date");
String endDate = m.rs("end_date");
if(id == 0 && ("".equals(startDate) || "".equals(endDate))) { m.jsErrClose(_message.get("alert.common.required_key")); return; }

String type = m.rs("type");

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
	m.log("agreement_gate_" + siteId, "path=/mypage/certificate_template.jsp user_id=" + userId + " type=cert module=" + consentModule + " module_id=" + id);
	m.redirect("/member/privacy_agree.jsp?id=" + userId + "&ek=" + pek + "&ag=cert&mid=" + id + "&returl=" + m.urlencode(cur));
	return;
}

if("pdf".equals(type)) {
    String url = webUrl + "/mypage/certificate.jsp?cuid=" + id;
    String path = dataDir + "/tmp/" + m.getUniqId() + ".pdf";
    String cmd = "/usr/local/bin/wkhtmltopdf -s A4 " + url + " " + path;

    m.exec(cmd);
    m.output(path, null);
    m.delFile(path);
    return;
}

//변수
boolean isSingle = id > 0;

//객체
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
CourseLessonDao courseLesson = new CourseLessonDao();
UserDeptDao userDept = new UserDeptDao();

TutorDao tutor = new TutorDao();
CourseTutorDao courseTutor = new CourseTutorDao();

OrderDao order = new OrderDao();
OrderItemDao orderItem = new OrderItemDao();

CertificateTemplateDao certificateTemplate = new CertificateTemplateDao();

FileDao file = new FileDao();

//정보
//courseUser.d(out);
DataSet list = courseUser.query(
    " SELECT a.*, b.id cid, b.course_nm, b.course_type, b.onoff_type, b.lesson_day, b.lesson_time, b.year, b.step, b.course_address, b.credit, b.etc1 course_etc1, b.etc2 course_etc2, b.cert_template_id, b.pass_cert_template_id "
    + " , c.login_id, c.dept_id, c.user_nm, c.birthday, c.zipcode, c.new_addr, c.addr_dtl, c.email, c.gender, c.etc1, c.etc2, c.etc3, c.etc4, c.etc5 "
    + " , d.dept_nm, o.pay_date, oi.pay_price, oi.refund_price "
    + " , (SELECT COUNT(*) FROM " + courseLesson.table + " WHERE course_id = a.course_id AND status = 1) lesson_cnt "
    + " FROM " + courseUser.table + " a "
    + " INNER JOIN " + course.table + " b ON a.course_id = b.id "
    + " LEFT JOIN " + orderItem.table + " oi ON a.order_item_id = oi.id AND oi.status IN (1, 3) "
    + " LEFT JOIN " + order.table + " o ON a.order_id = o.id AND oi.order_id = o.id AND o.status IN (1, 3) "
    + " INNER JOIN " + user.table + " c ON a.user_id = c.id AND c.status != -1  "
    + " LEFT JOIN " + userDept.table + " d ON c.dept_id = d.id "
    + " WHERE " + (isSingle ? "a.id = " + id : "a.start_date <= '" + endDate + "' AND a.end_date >= '" + startDate + "'") + " AND a.complete_yn = 'Y' "
    + " AND a.user_id = " + userId + " AND a.status IN (1, 3) "
);
if(!list.next()) { m.jsErrClose(_message.get("alert.course_user.nodata_complete")); return; }

DataSet cinfo = course.find(" id = ? AND site_id = ? AND status != ? ", new Object[] { list.i("cid"), siteId, -1 });
if(!cinfo.next()) { m.jsErrClose("해당 과정 정보가 없습니다."); return; }

//이동
int targetTemplateId = "P".equals(type) ? cinfo.i("pass_cert_template_id") : cinfo.i("cert_template_id");
if(0 == targetTemplateId) { m.jsReplace("certificate.jsp?" + m.qs()); return; }

//정보
String templateTypeFilter = "P".equals(type) ? "P" : "C";
DataSet ctinfo = certificateTemplate.find("id = " + targetTemplateId + " AND template_type = '" + templateTypeFilter + "' AND site_id = " + siteId + " AND status != -1");
if(!ctinfo.next()) { m.jsErrClose("해당 수료증템플릿 정보가 없습니다."); return; }

//포맷팅
list.first();
while(list.next()) {
    if(0 < list.i("dept_id")) {
        list.put("dept_nm_conv", userDept.getNames(list.i("dept_id")));
    } else {
        list.put("dept_nm", "[미소속]");
        list.put("dept_nm_conv", "[미소속]");
    }

    list.put("lesson_time_conv", m.nf((int)list.d("lesson_time")));

    list.put("birthday_conv", m.time(_message.get("format.date.local"), list.s("birthday")));
    list.put("birthday_conv2", m.time(_message.get("format.date.dot"), list.s("birthday")));
    list.put("birthday_conv3", m.time(_message.get("format.dateshort.dot"), list.s("birthday")));

    list.put("gender_conv", m.getValue(list.s("gender"), user.gendersMsg));

    list.put("onoff_type_conv", m.getValue(list.s("onoff_type"), course.onoffTypesMsg));
    list.put("start_date_conv", m.time(_message.get("format.date.local"), list.s("start_date")));
    list.put("start_date_conv2", m.time(_message.get("format.date.dot"), list.s("start_date")));
    list.put("start_date_conv3", m.time(_message.get("format.dateshort.dot"), list.s("start_date")));
    list.put("end_date_year", m.time("yyyy", list.s("end_date")));
    list.put("end_date_conv", m.time(_message.get("format.date.local"), list.s("end_date")));
    list.put("end_date_conv2", m.time(_message.get("format.date.dot"), list.s("end_date")));
    list.put("end_date_conv3", m.time(_message.get("format.dateshort.dot"), list.s("end_date")));
    list.put("course_nm_conv", m.cutString(m.htmlToText(list.s("course_nm")), 48));

    list.put("progress_ratio_conv", m.nf(list.d("progress_ratio"), 1));
    list.put("total_score", m.nf(list.d("total_score"), 0));
    list.put("complete_year", m.time("yyyy", list.s("complete_date")));
    list.put("complete_date_conv", m.time(_message.get("format.date.local"), list.s("complete_date")));
    list.put("complete_date_conv2", m.time(_message.get("format.date.dot"), list.s("complete_date")));
    list.put("complete_date_conv3", m.time(_message.get("format.dateshort.dot"), list.s("complete_date")));

    if(!"".equals(list.s("pay_date"))) {
        list.put("pay_date_conv", m.time(_message.get("format.date.local"), list.s("pay_date")));
        list.put("pay_date_conv2", m.time(_message.get("format.date.dot"), list.s("pay_date")));
        list.put("pay_date_conv3", m.time(_message.get("format.dateshort.dot"), list.s("pay_date")));
    } else {
        list.put("pay_date_conv", "-");
        list.put("pay_date_conv2", "-");
        list.put("pay_date_conv3", "-");
    }

    list.put("pay_price_conv", m.nf(list.i("pay_price") - list.i("refund_price")));
    list.put("certificate_no", m.time(_message.get("format.date.dot"), list.s("start_date")) + "-" + m.strrpad(id + "", 5, "0"));
    list.put("today", m.time(_message.get("format.date.local"), sysToday));
    list.put("today2", m.time(_message.get("format.date.dot"), sysToday));
    list.put("today3", m.time(_message.get("format.dateshort.dot"), sysToday));
    list.put("certificate_file_url", m.getUploadUrl(list.s("background_file")));

    //강사
    DataSet tutors = courseTutor.query(
            "SELECT t.*, u.display_yn "
                    + " FROM " + courseTutor.table + " a "
                    + " LEFT JOIN " + tutor.table + " t ON a.user_id = t.user_id "
                    + " LEFT JOIN " + user.table + " u ON t.user_id = u.id "
                    + " WHERE a.course_id = " + list.i("course_id") + " "
                    + " ORDER BY t.tutor_nm ASC "
    );
    list.put(".tutors", tutors);

    //정보-파일
    DataSet files = file.getFileList(list.i("user_id"), "user", true);
    while(files.next()) {
        files.put("image_block", -1 < files.s("filetype").indexOf("image/"));
        files.put("file_url", m.getUploadUrl(files.s("filename")));
    }
    list.put(".files", files);
}

uinfo.put("birthday_conv", m.time(_message.get("format.date.local"), uinfo.s("birthday")));
uinfo.put("birthday_conv2", m.time(_message.get("format.date.dot"), uinfo.s("birthday")));
uinfo.put("birthday_conv3", m.time(_message.get("format.dateshort.dot"), uinfo.s("birthday")));

//출력
p.setLayout(null);
p.setBody("page.certificate_template");
p.setVar("user", uinfo);

p.setVar(list);
p.setLoop("list", list);

p.setVar("today", m.time(_message.get("format.date.local")));
p.setVar("today2", m.time(_message.get("format.date.dot")));
p.setVar("today3", m.time(_message.get("format.dateshort.dot")));
p.setVar("single_block", isSingle);
p.setVar("certificate_file_url", m.getUploadUrl(ctinfo.s("background_file")));
String tbody = certificateTemplate.fetchTemplate(siteId, ctinfo.s("template_cd"), p);

out.print(tbody);
%>
<script>
    window.onload = function() {
        try {
            window.print();
        } catch (e) {
            console.log(e.message);
            alert("인쇄할 수 없습니다.");
        }
    }
</script>
