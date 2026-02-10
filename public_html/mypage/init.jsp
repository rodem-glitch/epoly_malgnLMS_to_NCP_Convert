<%@ include file="../init.jsp" %><%

//로그인
if(0 == userId) {
	// 왜: 나의강의실 계열(/mypage/*) 비로그인 진입은 구 로그인 페이지가 아니라
	//     신규 메인의 로그인 모달 흐름으로 통일해야 헤더/진입경로가 달라도 동일한 UX를 보장할 수 있습니다.
	String currentQs = m.qs("");
	String currentUrl = request.getRequestURI() + ("".equals(currentQs) ? "" : "?" + currentQs);
	int requestUdid = m.ri("udid");
	String modalUrl = "/mypage/new_main/?login_required=Y&returl=" + m.urlencode(currentUrl);
	if(0 < requestUdid) modalUrl += "&udid=" + requestUdid;

	m.log(
		"login_modal_gate_" + siteId,
		"path=/mypage/init.jsp unauth_redirect=Y returl=" + currentUrl + " udid=" + requestUdid
	);
	m.redirect(modalUrl);
	return;
}

//정보-회원
UserDao user = new UserDao();

DataSet uinfo = user.find("id = " + userId + " AND status = 1");
if(!uinfo.next()) { m.jsError(_message.get("alert.member.nodata")); return; }
uinfo.put("mobile_conv", !"".equals(uinfo.s("mobile")) ? uinfo.s("mobile") : "");
//uinfo.put("mobile", !"".equals(uinfo.s("mobile")) ? SimpleAES.decrypt(uinfo.s("mobile")) : "");
String ch = !userB2BBlock ? m.rs("ch", "mypage") : "b2b";

%>
