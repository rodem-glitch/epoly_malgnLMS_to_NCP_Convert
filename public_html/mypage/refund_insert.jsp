<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//기본키
int oid = m.ri("oid");
int id = m.ri("id");
if(oid == 0 || id == 0) { m.jsError(_message.get("alert.common.required_key")); return; }

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
DataSet info = orderItem.query(
	"SELECT a.* "
	+ ", c.id course_id, c.request_sdate, c.request_edate, c.step, c.study_sdate, c.study_edate, c.auto_approve_yn "
	+ ", c.class_member, c.credit "
	+ ", cu.id cuid, cu.start_date cu_sdate, cu.end_date cu_edate, cu.status cu_status "
	+ ", r.id rid, r.status r_status"
	+ ", o.paymethod, o.delivery_status "
	+ " FROM " + orderItem.table + " a "
	+ " INNER JOIN " + order.table + " o ON a.order_id = o.id AND o.status IN (-99,1,2,3,4,-2) "
	+ " LEFT JOIN " + course.table + " c ON a.product_type = 'course' AND a.product_id = c.id "
	+ " LEFT JOIN " + courseUser.table + " cu ON "
	+ " a.product_type = 'course' AND a.id = cu.order_item_id AND cu.package_id = 0 AND cu.status IN (0,1,3) AND cu.close_yn = 'N' "
	+ " LEFT JOIN " + refund.table + " r ON a.product_type = 'course' AND a.id = r.order_item_id"
	+ " WHERE a.user_id = " + userId + " AND a.id = " + id + " AND a.order_id = " + oid + " AND a.status IN (-99,1,2,3,-2) "
);
if(!info.next()) { m.jsError(_message.get("alert.common.nodata")); return; }

//제한-기신청건
if(!"".equals(info.s("r_status"))) { m.jsError("이미 환불신청된 항목입니다."); return; }

//제한-환불가능여부
boolean refundBlock = false;
if("course".equals(info.s("product_type"))) {
	refundBlock = !info.b("pay_free_block")
					&& info.i("rid") == 0 && info.i("status") == 1 && info.i("cuid") > 0
					&& 0 >= m.diffDate("D", info.s("cu_edate"), today);
} else if("book".equals(info.s("product_type"))) {
	refundBlock = info.i("rid") == 0 && info.i("status") == 1
					&& (info.i("delivery_status") == 0 || info.i("delivery_status") == 4);
}
if(!refundBlock) { m.jsError("환불신청 대상 항목이 아닙니다."); return; }

//포맷팅
info.put("price_conv", m.nf(info.i("price")));
info.put("disc_price_conv", m.nf(info.i("disc_price")));
info.put("pay_price_conv", m.nf(info.i("pay_price")));
info.put("paymethod_conv", m.getValue(info.s("paymethod"), order.methodsMsg));
info.put("coupon_price_conv", m.nf(info.i("coupon_price")));
info.put("product_type_conv", m.getValue(info.s("product_type"), orderItem.ptypesMsg));
info.put("reg_date_conv", m.time(_message.get("format.date.dot"), info.s("reg_date")));
info.put("status_conv", m.getValue(info.s("status"), orderItem.statusListMsg));

//폼체크
f.addElement("req_memo", null, "hname:'환불사유 및 요청사항', maxbyte:'250', required:'Y'");
if(!"01".equals(info.s("paymethod"))) {
	f.addElement("bank_nm", null, "hname:'환불계좌 은행', maxbyte:'100', required:'Y'");
	f.addElement("account_no", null, "hname:'환불계좌 번호', maxbyte:'100', required:'Y'");
	f.addElement("depositor", null, "hname:'환불계좌 예금주', maxbyte:'100', required:'Y'");
}

//처리
if(m.isPost() && f.validate()) {
    //환불신청
    DataSet oiinfo = orderItem.query(
            "SELECT a.* "
                    + ", cu.id cuid, cu.start_date cu_sdate, cu.end_date cu_edate, cu.course_id, cu.status cu_status "
                    + ", r.id rid, r.status r_status "
                    + " FROM " + orderItem.table + " a "
                    + " LEFT JOIN " + courseUser.table + " cu ON cu.order_item_id = a.id AND cu.status IN (0,1,3) AND cu.close_yn = 'N' "
                    + " LEFT JOIN " + refund.table + " r ON r.order_item_id = a.id "
                    + " WHERE a.id = " + id + " AND a.order_id = " + oid + " "
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
    refund.item("req_memo", f.get("req_memo"));
    refund.item("bank_nm", f.get("bank_nm"));
    refund.item("account_no", f.get("account_no"));
    refund.item("depositor", f.get("depositor"));
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
    if(!orderItem.update("id = " + id + "")) { m.jsAlert(_message.get("alert.common.error_modify")); return; }

    m.jsAlert(_message.get("alert.refund.inserted"));

    m.jsReplace("order_view.jsp?id=" + oid + "&" + m.qs("id, oid"), "parent");
    return;
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.refund_insert");
p.setVar("p_title", "환불신청");
p.setVar("query", m.qs("mode, oid"));
p.setVar("list_query", m.qs("id, mode, oid"));
p.setVar("form_script", f.getScript());

p.setVar(info);

p.display();

%>