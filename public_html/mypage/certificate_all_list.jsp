<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
CourseUserDao courseUser = new CourseUserDao();
CourseDao course = new CourseDao();

//변수
String type = m.rs("type");
String today = m.time("yyyyMMdd");

//폼입력
f.addElement("idx", null, "hname:'수강과정', required:'Y'");
f.addElement("s_etc1", null, null);
f.addElement("s_etc2", null, null);

//목록
ListManager lm = new ListManager();
//lm.setDebug(out);
lm.setRequest(request);
lm.setListNum(f.getInt("s_listnum", 30));
lm.setTable(
	courseUser.table + " a "
	+ " INNER JOIN " + course.table + " c ON a.course_id = c.id "
);
lm.setFields("a.*, c.course_nm, c.course_type, c.onoff_type, c.cert_course_yn, c.cert_complete_yn, c.credit, c.lesson_time, c.etc1, c.etc2");
lm.addWhere("a.status = 1");
lm.addWhere("a.site_id = " + siteId);
lm.addWhere("a.user_id = " + userId);
lm.addWhere("a.complete_yn = 'Y'");
lm.addWhere("c.cert_complete_yn = 'Y'");
if(!"".equals(type)) lm.addWhere("c.onoff_type " + ("on".equals(type) ? "=" : "!=") + " 'N'");
lm.addSearch("c.etc1", f.get("s_etc1"));
lm.addSearch("c.etc2", f.get("s_etc2"));
lm.setOrderBy(!"".equals(m.rs("ord")) ? m.rs("ord") : "a.id DESC, a.start_date ASC, c.course_nm ASC");

//포맷팅
DataSet list = lm.getDataSet();
while(list.next()) {
	list.put("start_date_conv", m.time(_message.get("format.date.dot"), list.s("start_date")));
	list.put("end_date_conv", m.time(_message.get("format.date.dot"), list.s("end_date")));
	list.put("study_date_conv", list.s("start_date_conv") + " - " + list.s("end_date_conv"));
	list.put("course_nm_conv", m.cutString(list.s("course_nm"), 40));
	list.put("progress_ratio", m.nf(list.d("progress_ratio"), 1));
	list.put("total_score", m.nf(list.d("total_score"), 1));
	//list.put("type_conv", m.getValue(list.s("course_type"), course.typesMsg));
	list.put("type_conv", m.getValue(list.s("onoff_type"), course.onoffTypesMsg));
	list.put("ready_block", 0 > m.diffDate("D", list.s("start_date"), today));
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.certificate_all_list");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("id"));
p.setVar("form_script", f.getScript());

p.setLoop("list", list);
p.setVar("list_total", lm.getTotalString());
p.setVar("pagebar", lm.getPaging());

p.setVar("LNB_CERTIFICATE", "select");
p.display();

%>