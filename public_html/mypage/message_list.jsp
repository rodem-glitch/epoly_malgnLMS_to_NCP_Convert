<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
MessageDao message = new MessageDao();
MessageUserDao mu = new MessageUserDao();

//폼체크
f.addElement("s_field", null, null);
f.addElement("s_keyword", null, null);

//목록
ListManager lm = new ListManager();
//lm.d(out);
lm.setRequest(request);
lm.setTable(
	mu.table + " a "
	+ " INNER JOIN " + message.table + " b ON a.message_id = b.id "
	+ " INNER JOIN " + user.table + " c ON b.user_id = c.id "
);
lm.setFields("a.*, b.subject, b.content, b.user_id send_user_id, c.user_nm send_user_nm");
lm.setListNum(10);
lm.addWhere("a.user_id = " + userId);
lm.addWhere("a.status = 1");
if(!"".equals(f.get("s_field"))) lm.addSearch(f.get("s_field"), f.get("s_keyword"), "LIKE");
else if("".equals(f.get("s_field")) && !"".equals(f.get("s_keyword"))) {
	lm.addSearch("b.subject,b.content", f.get("s_keyword"), "LIKE");
}
lm.setOrderBy("a.reg_date DESC");

//포맷팅
DataSet list = lm.getDataSet();
while(list.next()) {
	list.put("subject_conv", m.cutString(list.s("subject"), 40));
	list.put("read_yn_conv", m.getValue(list.s("read_yn"), mu.readListMsg));
	list.put("reg_date_conv", m.time(_message.get("format.date.dot"), list.s("reg_date")));
	list.put("read_date_conv", !"".equals(list.s("read_date")) && "Y".equals(list.s("read_yn")) ? m.time(_message.get("format.date.dot"), list.s("read_date")) : "-");
}

//출력
p.setLayout("mypage_newmain");
p.setBody(ch + ".message_list");
p.setVar("p_title", "쪽지함");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("id"));
p.setVar("form_script", f.getScript());

p.setVar("LNB_MESSAGE", "select");
p.setLoop("list", list);
p.setVar("pagebar", lm.getPaging());

p.display();

%>