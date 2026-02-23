<%@ page import="java.io.*" %><%@ page import="java.net.*" %><%@ page import="java.nio.charset.StandardCharsets" %><%@ page import="java.util.*" %><%@ page import="malgnsoft.db.*" %><%@ page import="malgnsoft.util.*" %><%@ include file="/init.jsp" %><%

// 왜 필요한가:
// - 채용 화면(job-test.html)과 /job/* API는 polytech-lms-api에서 제공됩니다.
// - 현재는 다른 포트/도메인으로 열려 혼합 콘텐츠/로컬 네트워크 차단 문제가 생깁니다.
// - 이 JSP가 같은 도메인에서 프록시 역할을 하면, 외부 PC에서도 안정적으로 열립니다.

response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
response.setHeader("Pragma", "no-cache");
response.setHeader("Expires", "0");

// -------------------- 프록시 대상 경로 결정 --------------------
String targetPath = request.getPathInfo();
if(targetPath == null || "".equals(targetPath.trim())) {
	targetPath = m.rs("path").trim();
}
if(targetPath == null || "".equals(targetPath.trim())) {
	response.setStatus(400);
	response.setContentType("application/json; charset=utf-8");
	out.print("{\"message\":\"path(파라미터 또는 pathInfo)가 필요합니다.\"}");
	return;
}
targetPath = targetPath.trim();

// 왜: 오픈 프록시가 되지 않도록 채용 경로만 허용합니다.
// 왜: job-dept-occupation-map.json도 Spring Boot 정적 리소스이므로 프록시 허용
if(!("/job-test.html".equals(targetPath) || "/job-dept-occupation-map.json".equals(targetPath) || targetPath.startsWith("/job/"))) {
	response.setStatus(400);
	response.setContentType("application/json; charset=utf-8");
	out.print("{\"message\":\"허용되지 않은 path 입니다.\"}");
	return;
}

String apiBase = System.getenv("POLYTECH_LMS_API_BASE");
if(apiBase == null || "".equals(apiBase.trim())) apiBase = "http://localhost:8081";
apiBase = apiBase.replaceAll("/+$", "");

String method = request.getMethod();
if(method == null) method = "GET";
method = method.toUpperCase(Locale.ROOT);

if(!("GET".equals(method) || "POST".equals(method))) {
	response.setStatus(405);
	response.setContentType("application/json; charset=utf-8");
	out.print("{\"message\":\"GET/POST만 허용됩니다.\"}");
	return;
}

String targetUrl = "";
String qs = request.getQueryString();

if(request.getPathInfo() != null && !"".equals(request.getPathInfo().trim())) {
	// 왜: pathInfo로 들어오면 queryString을 그대로 전달해도 안전합니다.
	targetUrl = apiBase + targetPath + (qs != null && !"".equals(qs) ? "?" + qs : "");
} else {
	// 왜: path 파라미터 방식은 path=...를 제외하고 query를 재구성해서 전달합니다.
	StringBuilder rebuilt = new StringBuilder();
	Enumeration<String> paramNames = request.getParameterNames();
	while(paramNames.hasMoreElements()) {
		String name = paramNames.nextElement();
		if("path".equals(name)) continue;
		String[] values = request.getParameterValues(name);
		if(values == null) continue;
		for(int i = 0; i < values.length; i++) {
			String v = values[i];
			if(rebuilt.length() > 0) rebuilt.append("&");
			rebuilt.append(URLEncoder.encode(name, "UTF-8"));
			rebuilt.append("=");
			rebuilt.append(URLEncoder.encode(v == null ? "" : v, "UTF-8"));
		}
	}
	targetUrl = apiBase + targetPath + (rebuilt.length() > 0 ? "?" + rebuilt.toString() : "");
}

HttpURLConnection conn = null;
int httpCode = 0;
try {
	conn = (HttpURLConnection) new URL(targetUrl).openConnection();
	conn.setRequestMethod(method);
	conn.setConnectTimeout(5000);
	conn.setReadTimeout(60000);

	String contentType = request.getContentType();
	if(contentType != null && !"".equals(contentType)) {
		conn.setRequestProperty("Content-Type", contentType);
	}
	String accept = request.getHeader("Accept");
	if(accept != null && !"".equals(accept)) {
		conn.setRequestProperty("Accept", accept);
	}

	// 왜: POST 요청 바디를 그대로 전달합니다.
	if("POST".equals(method)) {
		conn.setDoOutput(true);
		try(InputStream is = request.getInputStream(); OutputStream os = conn.getOutputStream()) {
			byte[] buf = new byte[8192];
			int len;
			while((len = is.read(buf)) != -1) os.write(buf, 0, len);
		}
	}

	httpCode = conn.getResponseCode();
	response.setStatus(httpCode);

	String upstreamContentType = conn.getContentType();
	if(upstreamContentType != null && !"".equals(upstreamContentType)) {
		response.setContentType(upstreamContentType);
	} else {
		response.setContentType("application/octet-stream");
	}

	InputStream upstreamStream = (httpCode >= 200 && httpCode < 300) ? conn.getInputStream() : conn.getErrorStream();
	if(upstreamStream == null) return;

	boolean isHtml = false;
	if(upstreamContentType != null) {
		String ct = upstreamContentType.toLowerCase(Locale.ROOT);
		isHtml = ct.contains("text/html");
	}
	if(!isHtml && targetPath.toLowerCase(Locale.ROOT).endsWith(".html")) {
		isHtml = true;
	}

	if(isHtml) {
		// 왜: job-test.html 내부에서 `/job/*`를 호출하므로 프록시 경로로 치환해야 합니다.
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try(InputStream is = upstreamStream) {
			byte[] buf = new byte[8192];
			int len;
			while((len = is.read(buf)) != -1) baos.write(buf, 0, len);
		}

		String html = new String(baos.toByteArray(), StandardCharsets.UTF_8);
		String proxyPrefix = "/api/job_proxy.jsp/job/";
		String rewritten = html.replace("/job/", proxyPrefix);

		byte[] outBytes = rewritten.getBytes(StandardCharsets.UTF_8);
		response.setContentType("text/html; charset=utf-8");
		try(OutputStream os = response.getOutputStream()) {
			os.write(outBytes);
		}
	} else {
		try(InputStream is = upstreamStream; OutputStream os = response.getOutputStream()) {
			byte[] buf = new byte[8192];
			int len;
			while((len = is.read(buf)) != -1) os.write(buf, 0, len);
		}
	}
} catch(Exception e) {
	response.reset();
	response.setStatus(502);
	response.setContentType("application/json; charset=utf-8");
	out.print("{\"message\":\"채용 서버 호출 중 오류가 발생했습니다.\"}");
} finally {
	if(conn != null) conn.disconnect();
}

%>
