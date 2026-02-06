<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
UserOutDao userOut = new UserOutDao();
CategoryDao category = new CategoryDao();

//폼체크
f.addElement("passwd", null, "hname:'비밀번호', required:'Y'");
f.addElement("out_type", null, "hname:'불편사항'");
f.addElement("agree_yn", null, "hname:'정보삭제동의', required:'Y'");
f.addElement("memo", null, "hname:'메모'");

//등록
if(m.isPost() && f.validate()) {

	if(!uinfo.s("passwd").equals(m.encrypt(f.get("passwd"), "SHA-256"))) {
		m.jsAlert(_message.get("alert.member.reenter_password"));
		return;
	}

	if(0 < userOut.findCount("user_id = " + userId)) { m.jsAlert(_message.get("alert.member.wait_out")); return; }

	//수정
	user.item("passwd", "");
	user.item("status", -2);
	if(!user.update("id = " + userId + " AND site_id = " + siteId)) { m.jsAlert(_message.get("alert.common.error_modify")); return; }

	//등록
	userOut.item("user_id", userId);
	userOut.item("site_id", siteId);
	userOut.item("out_type", m.join(",", f.getArr("out_type")));
	userOut.item("memo", f.get("memo"));
	userOut.item("reg_date", m.time("yyyyMMddHHmmss"));
	userOut.item("status", 1);

	if(!userOut.insert()) { m.jsAlert(_message.get("alert.common.error_insert")); return; }

	m.jsAlert(_message.get("alert.member.outed"));
	//auth.delAuthInfo();
	m.jsReplace("../member/logout.jsp", "parent");
	return;
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.out");
p.setVar("form_script", f.getScript());

p.setLoop("categories", m.arr2loop(userOut.types));
p.display();

%>