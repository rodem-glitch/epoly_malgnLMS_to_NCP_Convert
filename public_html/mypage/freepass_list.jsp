<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
FreepassDao freepass = new FreepassDao();
FreepassUserDao freepassUser = new FreepassUserDao();

CouponDao coupon = new CouponDao();
CouponUserDao couponUser = new CouponUserDao();
CourseDao course = new CourseDao();

//변수
String today = m.time("yyyyMMdd");

//목록
ListManager lm = new ListManager();
lm.setRequest(request);
lm.setListNum(10);
lm.setTable(
	freepassUser.table + " a "
	+ " INNER JOIN " + freepass.table + " f ON a.freepass_id = f.id "
	//+ " INNER JOIN " + user.table + " u ON u.id = a.user_id AND u.status != -1 "
);
lm.setFields("a.*, f.freepass_nm");
lm.addWhere("a.status = 1");
lm.addWhere("a.user_id = " + userId + "");
lm.setOrderBy("a.start_date ASC, a.id DESC");

//정보
DataSet list = lm.getDataSet();
while(list.next()) {
	list.put("start_date_conv", m.time(_message.get("format.date.dot"), list.s("start_date")));
	list.put("end_date_conv", m.time(_message.get("format.date.dot"), list.s("end_date")));
	list.put("freepass_nm_conv", m.cutString(list.s("freepass_nm"), 50));
	list.put("use_cnt_conv", m.nf(list.i("use_cnt")));
	list.put("limit_cnt_conv", (list.i("limit_cnt") > 0 ? m.nf(list.i("limit_cnt")) : "무제한"));

	String statusStr = "-";
	if(list.b("use_yn")) statusStr = "사용";
	else {
		if(0 > m.diffDate("D", list.s("start_date"), today)) statusStr = "기간전";
		else if(0 > m.diffDate("D", today, list.s("end_date"))) statusStr = "기간만료";
		else statusStr = "사용중";
	}
	list.put("status_conv", statusStr);
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.freepass_list");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("id"));
p.setVar("form_script", f.getScript());

p.setLoop("list", list);
p.setVar("pagebar", lm.getPaging());

p.setVar("LNB_COUPON", "select");
p.display();

%>