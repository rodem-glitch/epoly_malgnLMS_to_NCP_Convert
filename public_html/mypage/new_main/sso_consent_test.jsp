<%@ page contentType="text/html; charset=utf-8" %>
<%@ include file="../../init.jsp" %><%

// -------------------------------------------------------------------
// 목적: SSO "첫 방문 동의" 화면을 로컬에서 빠르게 확인하기 위한 테스트 페이지
// 주의: 운영 노출을 막기 위해 localhost(127.0.0.1/::1)에서만 접근 가능하게 제한합니다.
// -------------------------------------------------------------------

String ip = request.getRemoteAddr();
boolean isLocal = "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
if(!isLocal) {
	m.jsError("로컬에서만 사용할 수 있는 테스트 페이지입니다.");
	return;
}

if(userId == 0) {
	m.jsAlert("로그인 후 테스트해 주세요.");
	m.redirect("/member/login.jsp?returl=" + m.urlencode(request.getRequestURI()));
	return;
}

AgreementLogDao agreementLog = new AgreementLogDao(p, siteId);
String consentModule = "sso_20260120";
boolean agreed = "Y".equals(agreementLog.getOne(
	"SELECT agreement_yn FROM " + agreementLog.table
	+ " WHERE user_id = " + userId
	+ " AND type = 'sso'"
	+ " AND module = '" + consentModule + "'"
	+ " ORDER BY reg_date DESC"
));

// force=Y면 동의 완료 여부와 무관하게 동의 화면을 다시 띄웁니다(문구/화면 확인 목적).
boolean force = "Y".equalsIgnoreCase(m.rs("force"));

// returl은 "force" 파라미터를 제거한 상태로 돌려보내 무한 리다이렉트를 막습니다.
String returl = request.getRequestURI() + "?done=Y";

if(force || !agreed) {
	String pek = m.encrypt("PRIVACY_" + userId + "_AGREE_" + m.time("yyyyMMdd"));
	m.log("agreement_gate_" + siteId,
		"path=/mypage/new_main/sso_consent_test.jsp user_id=" + userId
		+ " type=sso module=" + consentModule + " force=" + (force ? "Y" : "N")
	);
	m.redirect("/member/privacy_agree.jsp?id=" + userId + "&ek=" + pek + "&ag=sso&returl=" + m.urlencode(returl));
	return;
}

%>
<!doctype html>
<html lang="ko">
<head>
	<meta charset="utf-8" />
	<meta name="viewport" content="width=device-width, initial-scale=1" />
	<title>SSO 첫 방문 동의 테스트</title>
	<style>
		body { font-family: system-ui, -apple-system, "Segoe UI", Roboto, "Noto Sans KR", Arial, sans-serif; margin: 24px; line-height: 1.6; }
		.box { max-width: 760px; border: 1px solid #ddd; border-radius: 10px; padding: 16px; }
		.kv { margin: 0; }
		.kv dt { font-weight: 700; }
		.kv dd { margin: 0 0 10px 0; color: #333; }
		a.btn { display: inline-block; padding: 10px 14px; border-radius: 8px; background: #111; color: #fff; text-decoration: none; }
	</style>
</head>
<body>
	<div class="box">
		<h1 style="margin-top:0;">SSO 첫 방문 동의 테스트(로컬 전용)</h1>
		<dl class="kv">
			<dt>사용자 ID</dt><dd><%=userId%></dd>
			<dt>동의 여부(TB_AGREEMENT_LOG)</dt><dd><%=agreed ? "Y" : "N"%></dd>
			<dt>동의 버전(module)</dt><dd><%=consentModule%></dd>
		</dl>
		<p style="margin: 0 0 12px 0;">
			동의 화면(문구/체크박스)이 정상인지 확인하려면 아래 버튼을 눌러 주세요.
			(<code>/common/images/consent/consent_sso_1.png</code> 또는 <code>consent_sso_2.png</code>가 1개도 없으면 동의 화면에서 차단됩니다.)
		</p>
		<p style="margin:0;">
			<a class="btn" href="sso_consent_test.jsp?force=Y">동의 화면 다시 보기</a>
		</p>
	</div>
</body>
</html>
