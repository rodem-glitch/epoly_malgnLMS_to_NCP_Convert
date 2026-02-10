<%@ page contentType="text/html; charset=utf-8" %><%@ include file="/init.jsp" %><%

//왜 필요한가:
//- `project`(React) 화면을 기존 MalgnLMS 안에서 URL로 접근할 수 있게 합니다.
//- 세션 로그인/교수자 권한을 통과한 사용자만 화면을 보도록 1차로 막습니다.

//로그인 확인(미로그인: 로그인 페이지로 이동)
if(0 == userId) {
	// 왜: tutor_lms를 직접 열었을 때 로그인 모달 완료 후 다시 같은 URL로 돌아오게 returl을 명시합니다.
	String returl = m.getThisURI();
	String loginUrl = auth.loginURL + (!auth.loginURL.contains("?") ? "?" : "&") + "returl=" + m.urlencode(returl);
	m.log("tutor_lms_login_gate_" + siteId, "path=/tutor_lms/index.jsp user_id=0 returl=" + returl);
	m.jsReplace(loginUrl, "top");
	return;
}

//관리자 권한 여부(왜: 운영자/최고관리자는 전체 데이터 관리가 가능해야 합니다)
boolean isAdmin = "S".equals(userKind) || "A".equals(userKind);

//교수자 권한 확인(TB_USER.tutor_yn) - 관리자가 아니면 교수자만 허용
UserDao user = new UserDao();
DataSet uinfo = user.find("id = " + userId + " AND site_id = " + siteId + " AND status = 1");
if(!uinfo.next()) {
	m.jsError("사용자 정보가 없습니다.");
	return;
}
if(!isAdmin && !"Y".equals(uinfo.s("tutor_yn"))) {
	m.jsError("교수자 권한이 없습니다.");
	return;
}

//정적 빌드 파일 존재 여부 확인
String appIndexPath = application.getRealPath("/tutor_lms/app/index.html");
if(null == appIndexPath || !(new File(appIndexPath)).exists()) {
%>
<!doctype html>
<html lang="ko">
<head>
	<meta charset="UTF-8" />
	<meta name="viewport" content="width=device-width, initial-scale=1.0" />
	<title>교수자 LMS</title>
	<style>
		body { font-family: system-ui,-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif; padding: 24px; }
		code { background: #f3f4f6; padding: 2px 6px; border-radius: 6px; }
	</style>
</head>
<body>
	<h1>교수자 LMS 화면 빌드가 필요합니다.</h1>
	<p>현재 서버 경로에 <code>/tutor_lms/app/index.html</code>이 없습니다.</p>
	<p>로컬에서 <code>cd project</code> 후 <code>npm install</code>, <code>npm run build</code>를 실행해 주세요.</p>
</body>
</html>
<%
	return;
}

//정상: React 빌드 결과로 이동
response.sendRedirect("app/index.html");

%>
