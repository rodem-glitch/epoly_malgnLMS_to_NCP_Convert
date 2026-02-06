<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//기본키
int id = m.ri("id");
if(id == 0) { m.jsError(_message.get("alert.common.required_key")); return; }

//객체
MessageUserDao mu = new MessageUserDao();
MessageDao message = new MessageDao();

//정보
DataSet info = mu.query(
	"SELECT a.*, b.subject, b.content, b.user_id send_user_id, c.user_nm send_user_nm "
	+ " FROM " + mu.table + " a "
	+ " INNER JOIN " + message.table + " b ON a.message_id = b.id "
	+ " INNER JOIN " + user.table + " c ON b.user_id = c.id "
	+ " WHERE a.id = " + id + " AND a.status = 1 "
);
if(!info.next()) { m.jsError(_message.get("alert.common.nodata")); return; }

info.put("reg_date_conv", m.time(_message.get("format.date.dot"), info.s("reg_date")));
info.put("read_date_conv", m.time(_message.get("format.date.dot"), info.s("read_date")));

if("N".equals(info.s("read_yn"))) {
	mu.item("read_yn", "Y");
	mu.item("read_date", m.time("yyyyMMddHHmmss"));

	if(!mu.update("id = " + id)) {}

	info.put("read_date_conv", m.time("yyyy.MM.dd"));
}

//출력
p.setLayout("mypage_newmain");
p.setBody(ch + ".message_view");
p.setVar("p_title", "쪽지함");

p.setVar(info);
p.setVar("LNB_MESSAGE", "select");

p.display();

%>