<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
OrderDao order = new OrderDao();
OrderItemDao orderItem = new OrderItemDao();
PaymentDao payment = new PaymentDao();
DeliveryDao delivery = new DeliveryDao();

//목록
ListManager lm = new ListManager();
//lm.d(out);
lm.setRequest(request);
lm.setListNum(10);
lm.setTable(
	order.table + " a "
	+ " LEFT JOIN " + delivery.table + " d ON d.id = a.delivery_id"
);
lm.setFields("a.*, d.link");
lm.addWhere("a.status IN (1,2,3,4,-2,-99)");
lm.addWhere("(a.status != -99 OR 0 < (SELECT COUNT(*) FROM " + orderItem.table + " WHERE order_id = a.id AND status IN (-99,1,2,3,-2)) )");
lm.addWhere("a.user_id = " + userId + "");
lm.addWhere("a.site_id = " + siteId + "");
lm.setOrderBy("a.reg_date DESC");

//정보
DataSet list = lm.getDataSet();
while(list.next()) {
	list.put("pay_price_conv", m.nf(list.i("pay_price")));
	list.put("order_date_conv", m.time(_message.get("format.date.dot"), list.s("order_date")));
	list.put("paymethod_conv", m.getValue(list.s("paymethod"), order.methodsMsg));
	list.put("complete_block", list.i("status") == 1);
	list.put("authdata", m.encrypt(list.s("mid") + list.s("tid") + Config.get("mkey")));
	list.put("order_nm_conv", m.cutString(list.s("order_nm"), 35));

	if(list.i("status") == 1 && list.i("delivery_status") >= 3) {
		list.put("status_conv", m.getValue(list.s("delivery_status"), order.deliveryStatusListMsg));
	} else {
		list.put("status_conv", m.getValue(list.s("status"), order.statusListMsg));
	}
	list.put("delivery_block", list.i("delivery_status") >= 3);
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.order_list");
p.setVar("p_title", "결제내역조회");

p.setLoop("list", list);
p.setVar("pagebar", lm.getPaging());
p.setVar("LNB_ORDER", "select");
p.display();

%>