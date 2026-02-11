<%@ page contentType="application/json; charset=utf-8" %>
<%@ page import="java.io.*" %>
<%@ page import="java.net.*" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.*" %>
<%@ page import="malgnsoft.json.*" %>
<%@ include file="../../init.jsp" %>
<%@ include file="/kollus/thumb_util.jspf" %><%

// 목적: 추천 동영상 목록을 JSON으로 반환 (설정 저장 후 즉시 갱신용)

StudentRecoPromptDao recoPrompt = new StudentRecoPromptDao();

String studentRecoPrompt = "";
if(userId > 0) {
	DataSet promptInfo = recoPrompt.find(
		"site_id = " + siteId + " AND user_id = " + userId + " AND status = 1",
		"prompt"
	);
	if(promptInfo.next()) {
		studentRecoPrompt = promptInfo.s("prompt");
	}
}

int topK = 4;
DataSet recoVideoList = new DataSet();

try {
	String apiBase = System.getenv("POLYTECH_LMS_API_BASE");
	if(apiBase == null || "".equals(apiBase.trim())) {
		// 왜: 컨테이너 환경에서 localhost를 기본값으로 쓰면 API 컨테이너가 아닌 자기 자신을 바라봐
		// 추천 목록이 조용히 빈 배열이 되는 문제가 발생합니다. 설정 누락은 즉시 로그로 드러냅니다.
		malgnsoft.util.Malgn.errorLog("학생 홈 추천 API 설정 누락(POLYTECH_LMS_API_BASE). site_id=" + siteId + ", user_id=" + userId);
		out.print("{\"ok\":true,\"items\":[]}");
		return;
	}
	apiBase = apiBase.replaceAll("/+$", "");

	String url = apiBase + "/student/content-recommend/home";

	JSONObject payload = new JSONObject();
	if(userId > 0) payload.put("userId", userId);
	payload.put("siteId", siteId);
	payload.put("topK", topK);
	if(!"".equals(studentRecoPrompt)) payload.put("extraQuery", studentRecoPrompt);

	String responseBody = "";
	int httpCode = 0;

	HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
	conn.setRequestMethod("POST");
	conn.setConnectTimeout(5000);
	conn.setReadTimeout(20000);
	conn.setDoOutput(true);
	conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
	conn.setRequestProperty("Accept", "application/json");

	byte[] bodyBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
	conn.setFixedLengthStreamingMode(bodyBytes.length);
	try(OutputStream os = conn.getOutputStream()) {
		os.write(bodyBytes);
	}

	httpCode = conn.getResponseCode();
	InputStream is = (httpCode >= 200 && httpCode < 300) ? conn.getInputStream() : conn.getErrorStream();
	if(is != null) {
		try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while((line = br.readLine()) != null) sb.append(line);
			responseBody = sb.toString();
		}
	}

	if(httpCode >= 200 && httpCode < 300) {
		DataSet recoRows = new DataSet();
		try {
			String trimmed = responseBody == null ? "" : responseBody.trim();
			if(trimmed.startsWith("[")) recoRows = malgnsoft.util.Json.decode(trimmed);
		} catch(Exception ignore) {}

		KollusMediaDao kollusMedia = new KollusMediaDao();
		KollusDao kollus = new KollusDao(siteId);
		HashMap<String, String> thumbCache = new HashMap<String, String>();

		// 왜: 추천 목록의 lessonId(콜러스 키)는 TB_KOLLUS_MEDIA에 없을 수 있습니다(찜/매핑 전).
		//     tutor_lms/api/kollus_list.jsp처럼 콜러스 API 목록에서 snapshot_url을 추가로 찾아 썸네일을 맞춥니다.
		HashSet<String> lessonIdSet = new HashSet<String>();
		recoRows.first();
		while(recoRows.next()) {
			String lid = recoRows.s("lessonId");
			if(!"".equals(lid)) lessonIdSet.add(lid);
		}
		recoRows.first();

		HashMap<String, String> kollusThumbMap = new HashMap<String, String>();
		boolean kollusThumbMapLoaded = false;

		String now = m.time("yyyyMMddHHmmss");

		while(recoRows.next()) {
			String lessonId = recoRows.s("lessonId");
			if("".equals(lessonId)) continue;

			recoVideoList.addRow();
			recoVideoList.put("lesson_id", lessonId);
			recoVideoList.put("title", recoRows.s("title"));
			recoVideoList.put("category_nm", recoRows.s("categoryNm"));

			String playUrl = "/kollus/preview.jsp?key=" + lessonId;
			recoVideoList.put("play_url", playUrl);

			String thumbnail = thumbCache.get(lessonId);
			if(thumbnail == null) {
				// 1차: DB만 조회(콜러스 API 호출 최소화)
				String found = kollusThumbResolveAndCache(siteId, lessonId, recoRows.s("title"), now, kollusMedia, kollusThumbMapLoaded ? kollusThumbMap : null);
				// 2차: DB에 없으면 그때 콜러스 API 목록을 불러와 재시도
				if("".equals(found) && !kollusThumbMapLoaded) {
					kollusThumbMap = kollusThumbBuildMap(kollus, siteinfo, siteId, lessonIdSet, 30);
					kollusThumbMapLoaded = true;
					found = kollusThumbResolveAndCache(siteId, lessonId, recoRows.s("title"), now, kollusMedia, kollusThumbMap);
				}
				if("".equals(found)) found = "/html/images/common/noimage_course.gif";
				thumbCache.put(lessonId, found);
				thumbnail = found;
			}
			recoVideoList.put("thumbnail", thumbnail);
		}
	} else {
		String responsePreview = responseBody == null ? "" : responseBody;
		if(responsePreview.length() > 400) responsePreview = responsePreview.substring(0, 400);
		responsePreview = m.replace(responsePreview, "\r", " ");
		responsePreview = m.replace(responsePreview, "\n", " ");
		malgnsoft.util.Malgn.errorLog(
			"학생 홈 추천 API 응답 실패(site_id=" + siteId
			+ ", user_id=" + userId
			+ ", code=" + httpCode
			+ ", url=" + url
			+ ", body=" + responsePreview + ")"
		);
	}
} catch(Exception e) {
	malgnsoft.util.Malgn.errorLog("학생 홈 추천 동영상(JSON) 조회 실패(site_id=" + siteId + ", user_id=" + userId + "): " + e.getMessage(), e);
}

// JSON 출력 (간단하게 문자열 구성)
StringBuilder outJson = new StringBuilder();
outJson.append("{\"ok\":true,\"items\":[");
for(int i = 0; i < recoVideoList.size(); i++) {
	recoVideoList.next();
	if(i > 0) outJson.append(",");
	String title = recoVideoList.s("title");
	String category = recoVideoList.s("category_nm");
	String playUrl = recoVideoList.s("play_url");
	String thumbnail = recoVideoList.s("thumbnail");

	String safeTitle = m.replace(title, "\\", "\\\\");
	safeTitle = m.replace(safeTitle, "\"", "\\\"");
	safeTitle = m.replace(safeTitle, "\r", "");
	safeTitle = m.replace(safeTitle, "\n", "\\n");

	String safeCategory = m.replace(category, "\\", "\\\\");
	safeCategory = m.replace(safeCategory, "\"", "\\\"");
	safeCategory = m.replace(safeCategory, "\r", "");
	safeCategory = m.replace(safeCategory, "\n", "\\n");

	outJson.append("{");
	outJson.append("\"title\":\"").append(safeTitle).append("\",");
	outJson.append("\"category_nm\":\"").append(safeCategory).append("\",");
	String safePlayUrl = m.replace(playUrl, "\\", "\\\\");
	safePlayUrl = m.replace(safePlayUrl, "\"", "\\\"");
	safePlayUrl = m.replace(safePlayUrl, "\r", "");
	safePlayUrl = m.replace(safePlayUrl, "\n", "\\n");

	String safeThumbnail = m.replace(thumbnail, "\\", "\\\\");
	safeThumbnail = m.replace(safeThumbnail, "\"", "\\\"");
	safeThumbnail = m.replace(safeThumbnail, "\r", "");
	safeThumbnail = m.replace(safeThumbnail, "\n", "\\n");

	outJson.append("\"play_url\":\"").append(safePlayUrl).append("\",");
	outJson.append("\"thumbnail\":\"").append(safeThumbnail).append("\"");
	outJson.append("}");
}
outJson.append("]}");
out.print(outJson.toString());
%>
