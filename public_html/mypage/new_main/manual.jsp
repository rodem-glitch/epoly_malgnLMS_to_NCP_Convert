<%@ page contentType="text/html; charset=utf-8" %>
<%@ include file="../../init.jsp" %>
<%
// -------------------------------------------------------------------
// 목적: 매뉴얼 페이지 (학생/교직원 매뉴얼 안내)
// -------------------------------------------------------------------

// 레이아웃: blank (전역 네비게이션 제외)
p.setLayout("blank");
p.setBody("mypage.new_main_manual");

p.setVar("p_title", "매뉴얼");
p.setVar("query", m.qs());

// 로그인 상태 및 사용자 정보
UserDao user = new UserDao();
DataSet uinfo = null;
if(userId > 0) {
	uinfo = user.find("id = " + userId + " AND status = 1");
	if(!uinfo.next()) {
		uinfo = null;
	}
}

p.setVar("login_block", userId > 0 && uinfo != null);
if(userId > 0 && uinfo != null) {
	p.setVar("user", uinfo);
	p.setVar("SYS_USERNAME", uinfo.s("user_nm"));
	String userNameForHeader = uinfo.s("user_nm");
	p.setVar("SYS_USERNAME_INITIAL", userNameForHeader.length() > 0 ? userNameForHeader.substring(0, 1) : "?");
} else {
	p.setVar("SYS_USERNAME", "");
	p.setVar("SYS_USERNAME_INITIAL", "");
}

p.display();
%>
