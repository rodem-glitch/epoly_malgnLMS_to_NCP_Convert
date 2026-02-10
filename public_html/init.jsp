<%@ page import="java.util.*,java.io.*,dao.*,malgnsoft.db.*,malgnsoft.util.*" %><%

// 왜: JSP에서 한글 파라미터가 깨지지 않게 하기 위해 UTF-8로 고정합니다.
request.setCharacterEncoding("UTF-8");

// 왜: 로컬 실행 시에도 Config/config.xml 값을 읽어 공통 경로/설정을 쓰도록 합니다.
String docRoot = Config.getDocRoot();
String jndi = Config.getJndi();

Malgn m = new Malgn(request, response, out);

Form f = new Form("form1");
try { f.setRequest(request); }
catch(RuntimeException re) {
	m.errorLog("파일 업로드 용량 초과 - " + re.getMessage(), re);
	return;
}
catch(Exception ex) {
	m.errorLog("요청 파싱 오류 - " + ex.getMessage(), ex);
	return;
}

// 왜: 멀티사이트 구조라서 도메인 기준으로 siteinfo를 먼저 잡아야 이후 페이지들이 정상 동작합니다.
SiteDao Site = new SiteDao();
DataSet siteinfo = Site.getSiteInfo(request.getServerName());
SiteConfigDao SiteConfig = new SiteConfigDao(siteinfo.i("id"));
if(1 != siteinfo.i("status") || "".equals(siteinfo.s("doc_root"))) {
	// 왜: siteinfo/status/doc_root가 비정상일 때는 화면이 흰색으로 끝날 수 있어,
	//     로컬/운영에서 원인을 즉시 추적할 수 있도록 최소 진단 로그를 남깁니다.
	m.log(
		"siteinfo_invalid",
		"host=" + request.getServerName()
		+ " site_id=" + siteinfo.i("id")
		+ " status=" + siteinfo.i("status")
		+ " doc_root_empty=" + "".equals(siteinfo.s("doc_root"))
	);
	return;
}

// 왜: DB에 저장된 doc_root는 운영 서버 경로(c:\\home\\lms\\...)일 수 있어서,
//     로컬에서는 config.xml의 docRoot로 강제로 맞춰야 템플릿/layout 파일을 읽을 수 있습니다.
String localDocRoot = Config.getDocRoot();
if(!"".equals(localDocRoot)) {
	siteinfo.put("doc_root", localDocRoot);
}

boolean isDevServer = true;
String webUrl = request.getScheme() + "://" + request.getServerName();
int port = request.getServerPort();
if(port != 80 && port != 443) webUrl += ":" + port;

String dataDir = siteinfo.s("doc_root") + "/data";
String tplRoot = siteinfo.s("doc_root") + "/html";
f.dataDir = dataDir;
m.dataDir = dataDir;
m.dataUrl = Config.getDataUrl();

// 왜: Page 렌더링이 템플릿(html) 기반이라 기본 루트를 지정합니다.
Page p = new Page(tplRoot);
p.setRequest(request);
p.setPageContext(pageContext);
p.setWriter(out);
p.setBaseRoot(tplRoot);

String sysLocale = "".equals(siteinfo.s("locale")) ? "default" : siteinfo.s("locale");
Message _message = new Message(sysLocale);
_message.reloadAll();
m.setMessage(_message);

int siteId = siteinfo.i("id");
int userId = 0;
String loginId = "";
String loginMethod = "";
String userName = "";
String userEmail = "";
String userKind = "";
int userDeptId = 0;
String userGroups = "";
int userGroupDisc = 0;
String userSessionId = "";

boolean userB2BBlock = false;
String userB2BName = "";
String userB2BFile = "";

String sysToday = m.time("yyyyMMdd");
String sysNow = m.time("yyyyMMddHHmmss");

// 왜: 접속 IP는 학습로그/결제/부정행위 방지 등 여러 기능에서 공통으로 필요합니다.
//     LB(로드밸런서) 환경에서는 쉼표로 여러 IP가 들어올 수 있어, 첫 번째 IP를 실제 사용자 IP로 사용합니다.
String userIp = m.getRemoteAddr();
if(userIp == null) userIp = "";
if(userIp != null && userIp.contains(",")) {
	String[] userIpArr = m.split(",", userIp);
	if(userIpArr != null && 0 < userIpArr.length) userIp = userIpArr[0].trim();
}

// 왜: 신형/구형 뷰어 분기 등 프론트 화면 로직에서 공통으로 쓰는 설정값입니다.
int sysViewerVersion = Math.max(SiteConfig.i("sys_viewer_version"), 1);

// 왜: 로컬에서 바로 화면 확인이 가능하도록, 과한 강제 정책(2차인증/필수정보 강제 등)은 최소화합니다.
SessionDao mSession = new SessionDao(request, response);
Auth auth = new Auth(request, response);
auth.loginURL = "/member/login.jsp";
auth.keyName = "MLMS14" + siteId + "7";
if(0 < siteinfo.i("session_hour")) auth.setValidTime(siteinfo.i("session_hour") * 3600);
if(auth.isValid()) {
	userId = auth.getInt("ID");
	loginId = auth.getString("LOGINID");
	loginMethod = auth.getString("LOGINMETHOD");
	userName = auth.getString("NAME");
	userEmail = auth.getString("EMAIL");
	userKind = auth.getString("KIND");
	userDeptId = auth.getInt("DEPT");
	userGroups = auth.getString("GROUPS");
	userGroupDisc = !"null".equals(auth.getString("GROUPS_DISC")) ? m.parseInt(auth.getString("GROUPS_DISC")) : 0;
	userSessionId = auth.getString("SESSIONID");
	userB2BName = auth.getString("B2BNAME");
	userB2BFile = auth.getString("B2BFILE");

	if(userGroups != null) {
		if(-1 < userGroups.indexOf(",")) for(String userGroupId : m.split(",", userGroups)) p.setVar("SYS_USERGROUP_" + userGroupId, true);
		else p.setVar("SYS_USERGROUP_" + userGroups, true);
	}

	mSession.put("id", userSessionId);
	mSession.save();
	p.setVar("login_block", true);
} else {
	p.setVar("login_block", false);
}

userB2BBlock = !"".equals(userB2BName) && null != userB2BName;

// 왜: 템플릿에서 자주 쓰는 공통 변수를 한 번에 세팅합니다.
p.setVar("SYS_HTTPHOST", request.getServerName());
p.setVar("SYS_LOGINID", loginId);
p.setVar("SYS_USERID", userId);
p.setVar("SYS_USERNAME", userName);
p.setVar("SYS_USEREMAIL", userEmail);
p.setVar("SYS_USERKIND", userKind);
p.setVar("SYS_DEPTID", userDeptId);
p.setVar("SYS_GROUP_DISC", userGroupDisc);
p.setVar("SYS_B2BBLOCK", userB2BBlock);
p.setVar("SYS_B2BNAME", userB2BName);
p.setVar("SYS_B2BFILE", userB2BFile);
p.setVar("SYS_PAGE_URL", request.getRequestURL() + (!"".equals(m.qs()) ? "?" + m.qs() : ""));
p.setVar("SYS_TITLE", siteinfo.s("site_nm"));
p.setVar("webUrl", webUrl);
p.setVar("SITE_INFO", siteinfo);
p.setVar("CURR_DATE", m.time("yyyyMMdd"));
p.setVar("SYS_EK", m.encrypt(loginId + siteinfo.s("sso_key") + m.time("yyyyMMdd"), "SHA-256"));
p.setVar("SYS_LOCALE", sysLocale);
p.setVar("SYS_TODAY", sysToday);
p.setVar("SYS_NOW", sysNow);
p.setVar("SYS_VIEWER_VERSION", sysViewerVersion);

MenuDao Menu = new MenuDao(p, siteId, "default");
%>
