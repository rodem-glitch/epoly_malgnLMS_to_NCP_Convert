<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//기본키
int id = m.ri("id");
if(id == 0) { m.jsError(_message.get("alert.common.required_key")); return; }

//객체
OrderDao order = new OrderDao();
OrderItemDao orderItem = new OrderItemDao();
PaymentDao payment = new PaymentDao();
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
RefundDao refund = new RefundDao();
DeliveryDao delivery = new DeliveryDao();

//변수
String today = m.time("yyyyMMdd");
//정보
DataSet info = order.query(
	" SELECT a.*, d.delivery_nm, d.link "
	+ " FROM " + order.table + " a "
	+ " LEFT JOIN " + delivery.table + " d ON d.id = a.delivery_id "
	+ " WHERE a.id = " + id + " AND a.user_id = " + userId + " AND a.status IN (-99,1,2,3,4,-2) "
);
if(!info.next()) { m.jsError(_message.get("alert.common.nodata")); return; }

info.put("price_conv", m.nf(info.i("price")));
info.put("disc_price_conv", m.nf(info.i("disc_price")));
info.put("disc_group_price_conv", m.nf(info.i("disc_group_price")));
info.put("pay_price_conv", m.nf(info.i("pay_price")));
info.put(info.s("paymethod") + "_block", true);
info.put("cancel_block", info.i("status") == 2); //취소가능
info.put("canceled", info.i("status") == -2); //취소됨
info.put("delete_block", info.i("status") == 2 && ("03".equals(info.s("paymethod")) || "90".equals(info.s("paymethod"))));

if(info.i("status") == 1 && info.i("delivery_status") >= 3) {
	info.put("status_conv", m.getValue(info.s("delivery_status"), order.deliveryStatusListMsg));
} else {
	info.put("status_conv", m.getValue(info.s("status"), order.statusListMsg));
}
info.put("delivery_block", info.i("delivery_status") >= 3);

//처리
if("del".equals(m.rs("mode"))) {
	//결제취소
	if(2 == info.i("status") && ("03".equals(info.s("paymethod")) || "90".equals(info.s("paymethod")))) {
		order.item("status", -2);
		order.item("ord_memo", info.s("ord_memo") + "<br>[" + m.time("yyyy.MM.dd HH:mm") + "] 사용자 요청에 의한 무통장입금 취소");
		order.update("id = " + id);

		orderItem.item("status", -2);
		orderItem.update("order_id = " + id);

		courseUser.item("change_date", m.time("yyyyMMddHHmmss"));
		courseUser.item("status", -4);
		courseUser.update("order_id = " + id);

		//order.execute("DELETE FROM " + order.table + " WHERE id = " + id);
		//orderItem.execute("DELETE FROM " + orderItem.table + " WHERE order_id = " + id);
		//courseUser.execute("DELETE FROM " + courseUser.table + " WHERE order_id = " + id);

		m.jsAlert(_message.get("alert.payment.canceled_deposit"));
		m.jsReplace("order_list.jsp", "parent");
		return;
	}
} else if("cancel".equals(m.rs("mode"))) {
	//신청취소
	DataSet oiinfo = orderItem.query(
		"SELECT a.* "
		+ ", cu.id cuid, cu.start_date cu_sdate, cu.end_date cu_edate, cu.course_id "
		+ " FROM " + orderItem.table + " a "
		+ " LEFT JOIN " + courseUser.table + " cu ON cu.order_item_id = a.id AND cu.status IN (0,1,3) AND cu.close_yn = 'N' "
		+ " WHERE a.id = " + m.ri("iid") + " AND a.order_id = " + id + " AND a.product_type = 'course'"
		+ " AND a.user_id = " + userId + " AND a.status = 1 "
	);
	if(!oiinfo.next()) { m.jsError(_message.get("alert.payment.nodata")); return; }

	if(0 < oiinfo.i("price") || 1 > oiinfo.i("cuid")
			|| (0 != oiinfo.i("cu_status") || (1 == oiinfo.i("cu_status") && -1 < m.diffDate("D", oiinfo.s("cu_sdate"), today)))) {
		m.jsError(_message.get("alert.common.abnormal_access"));
		return;
	}

	//수강생취소
	courseUser.item("status", -4);
	if(!courseUser.update("id = " + oiinfo.i("cuid"))) { m.jsAlert(_message.get("alert.course_user.error_status")); return; }

	//아이템상태변경
	orderItem.item("status", -2);
	if(!orderItem.update("id = " + m.ri("iid") + "")) { m.jsAlert(_message.get("alert.common.error_modify")); return; }

	m.jsAlert(_message.get("alert.order_item.canceled"));

	m.jsReplace("order_view.jsp?" + m.qs("iid, mode"));
	return;
} else if("refund".equals(m.rs("mode"))) {
	//환불신청
	DataSet oiinfo = orderItem.query(
		"SELECT a.* "
		+ ", cu.id cuid, cu.start_date cu_sdate, cu.end_date cu_edate, cu.course_id, cu.status cu_status "
		+ ", r.id rid, r.status r_status "
		+ " FROM " + orderItem.table + " a "
		+ " LEFT JOIN " + courseUser.table + " cu ON cu.order_item_id = a.id AND cu.status IN (0,1,3) AND cu.close_yn = 'N' "
		+ " LEFT JOIN " + refund.table + " r ON r.order_item_id = a.id "
		+ " WHERE a.id = " + m.ri("iid") + " AND a.order_id = " + id + " "
		+ " AND a.user_id = " + userId + " AND a.status = 1 "
	);
	if(!oiinfo.next()) { m.jsError(_message.get("alert.payment.nodata")); return; }
	//if(oiinfo.i("rid") != 0 || oiinfo.i("cuid") == 0 || 0 < m.diffDate("D", oiinfo.s("cu_edate"), today)) {

	if(0 < oiinfo.i("cuid") && oiinfo.i("cu_status") == 0) {
		m.jsError(_message.get("alert.course_user.wait_approve"));
		return;
	}

	if(oiinfo.i("rid") != 0 || oiinfo.i("product_id") == 0
		|| ("course".equals(oiinfo.s("product_type")) && 0 < m.diffDate("D", oiinfo.s("cu_edate"), today))) {
		m.jsError(_message.get("alert.common.abnormal_access"));
		return;
	}

	//환불 등록
	refund.item("site_id", siteId);
	refund.item("user_id", userId);
	refund.item("order_id", oiinfo.s("order_id"));
	refund.item("order_item_id", oiinfo.i("id"));
	refund.item("refund_type", 3);
	refund.item("req_memo", "");
	refund.item("paymethod", info.s("paymethod"));
	refund.item("reg_date", m.time("yyyyMMddHHmmss"));
	refund.item("status", 1);

	if("course".equals(oiinfo.s("product_type"))) {
		refund.item("course_id", oiinfo.s("course_id"));
		refund.item("course_user_id", oiinfo.i("cuid"));
	} else {
		refund.item("course_id", 0);
		refund.item("course_user_id", 0);
	}
	if(!refund.insert()) { m.jsAlert(_message.get("alert.common.error_insert")); return; }

	//아이템상태변경
	orderItem.item("status", 3);
	if(!orderItem.update("id = " + m.ri("iid") + "")) { m.jsAlert(_message.get("alert.common.error_modify")); return; }

	m.jsAlert(_message.get("alert.refund.inserted"));

	m.jsReplace("order_view.jsp?" + m.qs("iid, mode"));
	return;
} else if("order".equals(m.rs("mode"))) {
	//주문하기
	if(-99 < info.i("status")) { m.jsAlert(_message.get("alert.common.abnormal_access")); return; }

	//세션
	mSession.put("last_order_id", id);
	mSession.save();

	m.jsReplace("../order/payment.jsp?oek=" + order.getOrderEk(id, userId), "parent");
	return;
} else if("order_cancel".equals(m.rs("mode"))) {
	//주문하기
	if(-99 < info.i("status")) { m.jsAlert(_message.get("alert.common.abnormal_access")); return; }

	//주문상태변경
	order.item("status", -1);
	if(!order.update("id = " + id + " AND status = -99")) { m.jsAlert(_message.get("alert.common.error_modify")); return; }

	//쿠폰취소
	DataSet oilist = orderItem.find("order_id = " + id + " AND status = -99");
	while(oilist.next()) {
		if(!orderItem.cancelDiscount(oilist.i("id"), oilist.i("coupon_user_id"))) { m.jsAlert(_message.get("alert.common.error_modify")); return; }
	}

	//아이템상태변경
	orderItem.item("status", -1);
	if(!orderItem.update("order_id = " + id + " AND status = -99")) { m.jsAlert(_message.get("alert.common.error_modify")); return; }

	m.jsAlert(_message.get("alert.order_item.canceled"));

	m.jsReplace("order_list.jsp?" + m.qs("id, mode"));
	return;
}

//목록-항목
DataSet list = orderItem.query(
	"SELECT a.* "
	+ ", c.id course_id, c.request_sdate, c.request_edate, c.step, c.study_sdate, c.study_edate, c.auto_approve_yn "
	+ ", c.class_member, c.credit "
	+ ", cu.id cuid, cu.start_date cu_sdate, cu.end_date cu_edate, cu.status cu_status "
	+ ", r.id rid, r.status r_status"
	+ ", o.delivery_status"
	+ " FROM " + orderItem.table + " a "
	+ " LEFT JOIN " + course.table + " c ON a.product_type = 'course' AND a.product_id = c.id "
	+ " LEFT JOIN " + courseUser.table + " cu ON "
		+ " a.product_type = 'course' AND a.id = cu.order_item_id AND cu.package_id = 0 AND cu.status IN (0,1,3) AND cu.close_yn = 'N' "
	+ " LEFT JOIN " + refund.table + " r ON a.product_type = 'course' AND a.id = r.order_item_id"
	+ " LEFT JOIN " + order.table + " o ON a.order_id = o.id"
	+ " WHERE a.user_id = " + userId + " AND a.order_id = " + id + " AND a.status IN (-99,1,2,3,-2) "
	+ " ORDER BY a.id ASC "
);
while(list.next()) {
	list.put("course_block", false);
	list.put("use_block", true);
	list.put("cancel_block", false);
	list.put("refund_block", false);
	list.put("free_block", 1 > list.i("price"));
	list.put("pay_free_block", 1 > list.i("pay_price"));
	list.put("discount_block", 0 < list.i("disc_group_price") || 0 < list.i("coupon_price"));
	list.put("price_conv", m.nf(list.i("price")));
	list.put("disc_price_conv", m.nf(list.i("disc_price")));
	list.put("disc_group_price_conv", m.nf(list.i("disc_group_price")));
	list.put("coupon_price_conv", m.nf(list.i("coupon_price")));
	list.put("pay_price_conv", m.nf(list.i("pay_price")));
	list.put("product_type_conv", m.getValue(list.s("product_type"), orderItem.ptypesMsg));
	list.put("reg_date_conv", m.time(_message.get("format.date.dot"), list.s("reg_date")));
	list.put("status_conv", m.getValue(list.s("status"), orderItem.statusListMsg));
	list.put("refund_conv", list.i("r_status") == 0 || list.i("r_status") == 1 ? "-" : m.getValue(list.s("r_status"), refund.statusListMsg));
	list.put("refund_price_conv", list.i("status") == -2 && list.i("refund_price") > 0 ? " (" + siteinfo.s("currency_prefix") + m.nf(list.i("refund_price")) + siteinfo.s("currency_suffix") + ")" :"");

	if("course".equals(list.s("product_type"))) {
		list.put("course_block", true);
		list.put("cancel_block",
			list.b("free_block") && 0 < list.i("cuid")
			&& (0 == list.i("cu_status") || (1 == list.i("cu_status") && 0 > m.diffDate("D", list.s("cu_sdate"), today)))
		);
		list.put("refund_block",
			!list.b("pay_free_block")
			&& list.i("rid") == 0 && list.i("status") == 1 && list.i("cuid") > 0
			&& 0 >= m.diffDate("D", list.s("cu_edate"), today)
		);
	} else if("book".equals(list.s("product_type"))) {
		list.put("course_block", false);
		list.put("refund_block",
			list.i("rid") == 0 && list.i("status") == 1
			&& (list.i("delivery_status") == 0 || list.i("delivery_status") == 4)
		);
	} else if("c_renew".equals(list.s("product_type"))) {
		list.put("course_block", false);
		list.put("cancel_block", false);
	} else {
		list.put("course_block", false);
		list.put("refund_block", false);
	}
}

//결제정보
DataSet pinfo = new DataSet();
if(-99 == info.i("status") || "90".equals(info.s("paymethod")) || "99".equals(info.s("paymethod"))) {
	p.setVar("pg_block", false);
	p.setVar("receipt_block", false);
} else {
	p.setVar("pg_block", true);
	p.setVar(
		"receipt_block"
		, (
			"01".equals(info.s("paymethod")) && (1 == info.i("status") || 3 == info.i("status") || 4 == info.i("status"))
			|| ("02".equals(info.s("paymethod")) || "03".equals(info.s("paymethod"))) && 1 == info.i("status")
		)
	);

	pinfo = payment.find("oid = " + id + " AND respcode IN ('0000', '0001', '00')");
	if(!pinfo.next()) { m.jsError(_message.get("alert.common.nodata")); return; }
	pinfo.put("paydate_conv", m.time(_message.get("format.datetime.dot"), pinfo.s("paydate")));
	pinfo.put("cardinstallmonth_conv", pinfo.i("cardinstallmonth") == 0 ? _message.get("payment.unit.month_single") : pinfo.i("cardinstallmonth") + _message.get("payment.unit.month_multi"));
	pinfo.put("amount_conv", m.nf(pinfo.i("amount")));

	//authdata는 LGU+에서만 사용됨. LGU+에서 올앳으로 변경한 경우 pg_key가 변경되어 오류 발생. lgu_pg_key 추가함. by hopegiver [2019-06-05]
	String pgKey = "lgu".equals(siteinfo.s("pg_nm")) ? siteinfo.s("pg_key") : SiteConfig.s("lgu_pg_key");
	pinfo.put("authdata", m.encrypt(pinfo.s("mid") + pinfo.s("tid") + pgKey));
	pinfo.put("caslimitdate_conv", (8 != pinfo.s("caslimitdate").length() ? "-" : m.time("yyyy.MM.dd", pinfo.s("caslimitdate"))));
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.order_view");
p.setVar("p_title", "결제내역조회");
p.setVar("query", m.qs("mode, iid"));
p.setVar("list_query", m.qs("id, mode, iid"));

p.setLoop("list", list);
p.setVar("quantity", list.size());
p.setVar("order", info);
p.setVar("payment", pinfo);
p.setVar("ek", m.encrypt(id + userId + "__LMS2014"));
p.setVar("free_block", "99".equals(info.s("paymethod")));
p.setVar("order_waiting_block", 0 < list.size() && -99 == info.i("status"));

p.display();

%>