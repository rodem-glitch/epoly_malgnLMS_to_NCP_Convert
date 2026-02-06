<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
CouponDao coupon = new CouponDao();
CouponUserDao couponUser = new CouponUserDao();
CourseDao course = new CourseDao();
OrderItemDao orderItem = new OrderItemDao();

//변수
String today = m.time("yyyyMMdd");

//폼입력
String mode = m.rs("mode");

//폼체크
f.addElement("coupon_no", null, "hname:'쿠폰번호', required:'Y', maxbyte:'50', pattern:'^[a-zA-Z0-9]{1,50}$', errmsg:'영숫자로 입력하세요.'");

//쿠폰등록
if(m.isPost() && f.validate()) {

	String couponNo = f.get("coupon_no").toUpperCase();

	//정보-쿠폰
	DataSet info = couponUser.query(
			"SELECT a.*, b.coupon_type, b.course_id, b.public_yn, b.start_date, b.end_date, b.status "
			+ " FROM " + couponUser.table + " a "
			+ " INNER JOIN " + coupon.table + " b ON a.coupon_id = b.id AND b.status = 1 AND b.site_id = " + siteId + " "
			+ " WHERE a.coupon_no = '" + couponNo + "' AND a.user_id IN (-99, 0, " + userId + ") "
			+ " ORDER BY a.user_id DESC "
			, 1
	);
	if(!info.next()) { m.jsAlert(_message.get("alert.coupon.unvalid_no")); return; }

	//제한
	if(0 > m.diffDate("D", today, info.s("end_date"))) { m.jsAlert(_message.get("alert.coupon.expired")); return;	}

	//보유여부
	if(info.i("user_id") > 0 && info.b("use_yn")) { m.jsAlert(_message.get("alert.coupon.used")); return; }
	else if(info.i("user_id") > 0 && !info.b("use_yn")) { m.jsAlert(_message.get("alert.coupon.owned")); return; }

	if(info.b("public_yn")) {
		couponUser.item("site_id", siteId);
		couponUser.item("coupon_no", couponNo);
		couponUser.item("coupon_id", info.i("coupon_id"));
		couponUser.item("user_id", userId);
		couponUser.item("use_yn", "N");
		couponUser.item("use_date", "");
		couponUser.item("reg_date", m.time("yyyyMMddHHmmss"));
		if(!couponUser.insert()) { m.jsAlert(_message.get("alert.coupon.error_insert")); return; }
	} else {
		couponUser.item("user_id", userId);
		couponUser.item("reg_date", m.time("yyyyMMddHHmmss"));
		if(!couponUser.update("id = " + info.i("id") + "")) { m.jsAlert(_message.get("alert.coupon.error_insert")); return; }
	}


	//이동
	m.jsAlert(_message.get("alert.coupon.success_insert"));
	if("C".equals(info.s("coupon_type")) && 0 < info.i("course_id") && -1 < m.diffDate("D", info.s("start_date"), today)) {
		m.jsReplace("../order/cart_common_insert.jsp?type=D&item=course," + info.i("course_id") + ",1", "parent");
	} else {
		m.jsReplace("coupon_list.jsp?" + m.qs(), "parent");
	}
	return;
}

//목록
ListManager lm = new ListManager();
//lm.d(out);
lm.setRequest(request);
lm.setListNum(10);
lm.setTable(
	couponUser.table + " a "
	+ " INNER JOIN " + coupon.table + " b ON "
	+ " b.id = a.coupon_id AND b.status = 1 AND b.site_id = " + siteId + " "
	+ " LEFT JOIN " + course.table + " c ON b.course_id = c.id "
);
lm.setFields("b.*, a.id coupon_user_id, a.use_yn, a.use_date, c.course_nm");
lm.addWhere("a.user_id = " + userId + "");
lm.addWhere("b.end_date >= '" + m.time("yyyyMMdd") + "'");
lm.setOrderBy("a.id DESC");

//정보
DataSet list = lm.getDataSet();
while(list.next()) {
	list.put("all_block", "A".equals(list.s("coupon_type")));
	list.put("course_nm_conv", !"B".equals(list.s("coupon_type")) ? (!"".equals(list.s("course_nm")) ? list.s("course_nm") : _message.get("list.coupon.etc.allcourses")) : "");
	list.put("course_block", !"B".equals(list.s("coupon_type")));
	list.put("coupon_type_conv", m.getValue(list.s("coupon_type"), coupon.ucouponTypesMsg));
	list.put("disc_value_conv", "P".equals(list.s("disc_type")) ? siteinfo.s("currency_prefix") + m.nf(list.i("disc_value")) + siteinfo.s("currency_suffix") : list.i("disc_value") + "%");
	list.put("min_price_block", list.i("min_price") > 0);
	list.put("min_price_conv", m.nf(list.i("min_price")));
	list.put("limit_price_block", "R".equals(list.s("disc_type")) && list.i("limit_price") > 0);
	list.put("limit_price_conv", m.nf(list.i("limit_price")));

	list.put("start_date_conv", m.time(_message.get("format.date.dot"), list.s("start_date")));
	list.put("end_date_conv", m.time(_message.get("format.date.dot"), list.s("end_date")));
	list.put("incomplete_block", false);
	list.put("complete_block", false);

	DataSet oiInfo = orderItem.query(
		" SELECT id, order_id FROM " + orderItem.table
		+ " WHERE coupon_user_id = " + list.i("coupon_user_id")  + " AND site_id = " + siteId + ""
		+ " ORDER BY id DESC", 1
	);

	if(!oiInfo.next()) { }

	String statusStr = "-";
	if(list.b("use_yn") && 1 > oiInfo.i("id")) {
		statusStr = _message.get("list.coupon.etc.used");
		list.put("complete_block", true);
	} else {
		if(0 > m.diffDate("D", list.s("start_date"), today)) {
			statusStr = _message.get("list.coupon.etc.period_before");
		} else if(0 > m.diffDate("D", today, list.s("end_date"))) {
			statusStr = _message.get("list.coupon.etc.period_expired");
		} else if(0 < oiInfo.i("id")) {
			statusStr = _message.get("list.coupon.etc.incomplete");
			list.put("incomplete_block", true);
		} else {
			statusStr = _message.get("list.coupon.etc.unused");
		}
	}
	list.put("order_id", oiInfo.i("order_id"));
	list.put("complete_order_id", oiInfo.i("order_id"));
	list.put("status_conv", statusStr);
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.coupon_list");
p.setVar("p_title", "쿠폰관리");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("id"));
p.setVar("form_script", f.getScript());

p.setLoop("list", list);
p.setVar("pagebar", lm.getPaging());

p.setVar("LNB_COUPON", "select");
p.display();

%>