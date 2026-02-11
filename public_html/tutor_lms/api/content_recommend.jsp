<%@ page pageEncoding="utf-8" %><%@ page import="java.io.*" %><%@ page import="java.net.*" %><%@ page import="java.nio.charset.StandardCharsets" %><%@ page import="java.util.*" %><%@ page import="malgnsoft.db.*" %><%@ page import="malgnsoft.util.*" %><%@ page import="malgnsoft.json.*" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자 화면(과목/차시 구성)에서 "콘텐츠 검색"을 눌렀을 때, 입력된 과목/차시 정보를 기반으로 추천 목록을 내려줘야 합니다.
//- 프론트는 같은 도메인의 `/tutor_lms/api/*`만 호출하므로, Spring Boot(polytech-lms-api) 추천 엔드포인트를 프록시해 줍니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

String courseName = m.rs("course_name").trim();
String courseIntro = m.rs("course_intro").trim();
String courseDetail = m.rs("course_detail").trim();
String lessonTitle = m.rs("lesson_title").trim();
String lessonDescription = m.rs("lesson_description").trim();
String keywords = m.rs("keywords").trim();

// 왜: "추천 결과가 과목마다 동일" 이슈는 입력 컨텍스트가 비어 들어오는 경우가 많아,
//     운영 로그에서 파라미터 유입 여부를 길이/유무로 즉시 확인할 수 있어야 합니다.
int contextFieldCount = 0;
if(!"".equals(courseName)) contextFieldCount++;
if(!"".equals(courseIntro)) contextFieldCount++;
if(!"".equals(courseDetail)) contextFieldCount++;
if(!"".equals(lessonTitle)) contextFieldCount++;
if(!"".equals(lessonDescription)) contextFieldCount++;
if(!"".equals(keywords)) contextFieldCount++;

int topK = m.ri("top_k");
if(topK <= 0) topK = 50;
if(topK > 50) topK = 50;

double similarityThreshold = 0.2;
try {
	String thresholdParam = m.rs("similarity_threshold").trim();
	if(!"".equals(thresholdParam)) similarityThreshold = Double.parseDouble(thresholdParam);
} catch(Exception ignore) {}
if(similarityThreshold <= 0) similarityThreshold = 0.2;
if(similarityThreshold > 1.0) similarityThreshold = 1.0;


// 왜: 운영/개발 환경마다 Spring API 주소가 다를 수 있어서, 환경변수로 우선 제어할 수 있게 합니다.
String apiBase = System.getenv("POLYTECH_LMS_API_BASE");
if(apiBase == null || "".equals(apiBase.trim())) apiBase = "http://localhost:8081";
apiBase = apiBase.replaceAll("/+$", "");

String url = apiBase + "/tutor/content-recommend/lessons";

JSONObject payload = new JSONObject();
payload.put("courseName", courseName);
payload.put("courseIntro", courseIntro);
payload.put("courseDetail", courseDetail);
payload.put("lessonTitle", lessonTitle);
payload.put("lessonDescription", lessonDescription);
payload.put("keywords", keywords);
payload.put("topK", topK);
payload.put("similarityThreshold", similarityThreshold);

m.log(
	"content_recommend",
	"request_context course_name_len=" + courseName.length()
	+ ", lesson_title_len=" + lessonTitle.length()
	+ ", lesson_desc_len=" + lessonDescription.length()
	+ ", keyword_len=" + keywords.length()
	+ ", context_fields=" + contextFieldCount
	+ ", top_k=" + topK
	+ ", threshold=" + similarityThreshold
);

String responseBody = "";
int httpCode = 0;

try {
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
} catch(Exception e) {
	m.log(
		"content_recommend",
		"proxy_call_failed message=" + e.getMessage()
		+ ", course_name_len=" + courseName.length()
		+ ", lesson_title_len=" + lessonTitle.length()
		+ ", keyword_len=" + keywords.length()
	);
	result.put("rst_code", "5001");
	result.put("rst_message", "추천 서버 호출 중 오류가 발생했습니다.");
	result.print();
	return;
}

if(httpCode < 200 || httpCode >= 300) {
	m.log(
		"content_recommend",
		"proxy_http_error code=" + httpCode
		+ ", response_len=" + (responseBody == null ? 0 : responseBody.length())
		+ ", course_name_len=" + courseName.length()
		+ ", lesson_title_len=" + lessonTitle.length()
	);
	result.put("rst_code", "5002");
	result.put("rst_message", "추천 서버 응답이 올바르지 않습니다. (" + httpCode + ")");
	result.print();
	return;
}

DataSet recoRows = new DataSet();
try {
	String trimmed = responseBody == null ? "" : responseBody.trim();
	if(trimmed.startsWith("[")) {
		recoRows = malgnsoft.util.Json.decode(trimmed);
	}
} catch(Exception ignore) {
	// 왜: 추천 서버가 JSON 배열을 돌려주지 못한 경우에도, 교수자 화면이 깨지지 않게 안전하게 실패 처리합니다.
}

// 왜: 콜러스 외부 영상은 LMS DB에 없으므로, API 응답(TB_RECO_CONTENT 데이터)을 직접 사용합니다.
DataSet list = new DataSet();
KollusMediaDao kollusMedia = new KollusMediaDao();
LessonDao lesson = new LessonDao();
DataObject transcript = new DataObject("TB_KOLLUS_TRANSCRIPT");
int metaHitCount = 0;
int metaMissCount = 0;
int lessonIdResolveCount = 0;
int lessonStartUrlHitCount = 0;
int missingTimeCount = 0;
int skippedCount = 0;
int transcriptTimeHitCount = 0;
int transcriptTimeMissCount = 0;

while(recoRows.next()) {
	String rawLessonId = recoRows.s("lessonId").trim(); // 추천 API 원본 키(벡터 메타)
	String mediaKey = rawLessonId;
	if("".equals(mediaKey)) mediaKey = recoRows.s("media_content_key").trim();
	if("".equals(mediaKey)) mediaKey = recoRows.s("upload_file_key").trim();
	if("".equals(mediaKey)) {
		skippedCount++;
		continue;
	}

	String title = recoRows.s("title");
	String categoryNm = recoRows.s("categoryNm");
	String categoryKey = recoRows.s("categoryKey");
	String summary = recoRows.s("summary");
	String keywordsVal = recoRows.s("keywords");
	String scoreVal = recoRows.s("score");
	String snapshotUrl = recoRows.s("snapshot_url");
	if("".equals(snapshotUrl)) snapshotUrl = recoRows.s("thumbnail");
	String originalFileName = recoRows.s("original_file_name");

	int totalTime = recoRows.i("total_time");
	if(totalTime <= 0) totalTime = m.parseInt(recoRows.s("totalTime"));
	int contentWidth = recoRows.i("content_width");
	if(contentWidth <= 0) contentWidth = m.parseInt(recoRows.s("contentWidth"));
	int contentHeight = recoRows.i("content_height");
	if(contentHeight <= 0) contentHeight = m.parseInt(recoRows.s("contentHeight"));

	// 왜: 추천 원본 lessonId가 LM_LESSON.id(숫자)일 수 있어, 콜러스 media key(start_url)로 먼저 정규화해야
	//     추천 탭에서도 "동영상 시간/인정시간 자동 세팅"이 전체 탭과 동일하게 동작합니다.
	int lessonPk = m.parseInt(rawLessonId);
	if(lessonPk > 0 && String.valueOf(lessonPk).equals(rawLessonId)) {
		DataSet linfo = lesson.find("site_id = " + siteId + " AND id = " + lessonPk + " AND status = 1");
		if(linfo.next()) {
			if("".equals(title)) title = linfo.s("lesson_nm");
			if(totalTime <= 0) totalTime = linfo.i("total_time");
			if(contentWidth <= 0) contentWidth = linfo.i("content_width");
			if(contentHeight <= 0) contentHeight = linfo.i("content_height");

			if("05".equals(linfo.s("lesson_type"))) {
				String lessonMediaKey = linfo.s("start_url").trim();
				if(!"".equals(lessonMediaKey) && !lessonMediaKey.equals(mediaKey)) {
					mediaKey = lessonMediaKey;
					lessonIdResolveCount++;
				}
			}
		}
	}

	// 왜: lessonId가 이미 media key인 경우에도 LM_LESSON에 저장된 시간/해상도 값이 더 정확할 수 있어 보완합니다.
	if(totalTime <= 0 || contentWidth <= 0 || contentHeight <= 0 || "".equals(title)) {
		DataSet linfoByStartUrl = lesson.find(
			"site_id = " + siteId + " AND start_url = ? AND lesson_type = '05' AND status = 1",
			new Object[] { mediaKey }
		);
		if(linfoByStartUrl.next()) {
			if("".equals(title)) title = linfoByStartUrl.s("lesson_nm");
			if(totalTime <= 0) totalTime = linfoByStartUrl.i("total_time");
			if(contentWidth <= 0) contentWidth = linfoByStartUrl.i("content_width");
			if(contentHeight <= 0) contentHeight = linfoByStartUrl.i("content_height");
			lessonStartUrlHitCount++;
		}
	}

	DataSet minfo = kollusMedia.find("site_id = " + siteId + " AND media_content_key = ?", new Object[] { mediaKey });
	if(minfo.next()) {
		metaHitCount++;
		if("".equals(title)) title = minfo.s("title");
		if("".equals(categoryNm)) categoryNm = minfo.s("category_nm");
		if("".equals(categoryKey)) categoryKey = minfo.s("category_key");
		if("".equals(snapshotUrl)) snapshotUrl = minfo.s("snapshot_url");
		if("".equals(originalFileName)) originalFileName = minfo.s("original_file_name");
		if(totalTime <= 0) totalTime = minfo.i("total_time");
		if(contentWidth <= 0) contentWidth = minfo.i("content_width");
		if(contentHeight <= 0) contentHeight = minfo.i("content_height");
	} else {
		metaMissCount++;
	}

	// 왜: 추천 콘텐츠의 lesson_id는 TB_RECO_CONTENT 기준 키라서 LM_LESSON/TB_KOLLUS_MEDIA에 없을 수 있습니다.
	//     이 경우에도 TB_KOLLUS_TRANSCRIPT.duration_seconds는 존재하므로, 추천 탭 시간 표시와 차시 인정시간 기본값을 안정적으로 채울 수 있습니다.
	if(totalTime <= 0) {
		boolean transcriptFound = false;
		String transcriptKey = !"".equals(mediaKey) ? mediaKey : rawLessonId;
		DataSet tinfo = null;

		if(!"".equals(transcriptKey)) {
			tinfo = transcript.find(
				"site_id = " + siteId + " AND media_content_key = ?",
				new Object[] { transcriptKey }
			);
			if(tinfo.next()) transcriptFound = true;
		}

		if(!transcriptFound && !"".equals(rawLessonId) && !rawLessonId.equals(transcriptKey)) {
			tinfo = transcript.find(
				"site_id = " + siteId + " AND media_content_key = ?",
				new Object[] { rawLessonId }
			);
			if(tinfo.next()) {
				transcriptFound = true;
				transcriptKey = rawLessonId;
			}
		}

		if(transcriptFound) {
			int durationSeconds = tinfo.i("duration_seconds");
			if(durationSeconds > 0) {
				totalTime = (int)Math.ceil(durationSeconds / 60.0d);
				transcriptTimeHitCount++;
			} else {
				transcriptTimeMissCount++;
			}
			if("".equals(title)) title = tinfo.s("title");
			if("".equals(mediaKey)) mediaKey = transcriptKey;
		} else {
			transcriptTimeMissCount++;
		}
	}

	if(totalTime <= 0) missingTimeCount++;

	String duration = recoRows.s("duration");
	if("".equals(duration)) duration = "-";
	if(totalTime > 0) {
		duration = totalTime + "분";
	}

	list.addRow();
	list.put("id", mediaKey);                              // 프론트 선택키 (콜러스 키값)
	list.put("lesson_id", mediaKey);                       // 추천 원본 키(문자열)
	list.put("raw_lesson_id", rawLessonId);                // 추천 응답의 원본 lessonId(디버깅용)
	list.put("media_content_key", mediaKey);               // 콜러스 재생용 키값
	list.put("title", title);
	list.put("category_nm", categoryNm);
	list.put("category_key", categoryKey);
	list.put("snapshot_url", snapshotUrl);
	list.put("thumbnail", snapshotUrl);
	list.put("original_file_name", originalFileName);
	list.put("total_time", totalTime);
	list.put("duration", duration);
	list.put("content_width", contentWidth);
	list.put("content_height", contentHeight);
	list.put("summary", summary);
	list.put("keywords", keywordsVal);
	list.put("score", scoreVal);
}

// 왜: 추천 응답에 메타가 비는 경우를 운영에서 빠르게 추적하기 위해 요약 로그를 남깁니다.
m.log(
	"content_recommend",
	"rows=" + list.size()
	+ ", course_name_len=" + courseName.length()
	+ ", lesson_title_len=" + lessonTitle.length()
	+ ", keyword_len=" + keywords.length()
	+ ", context_fields=" + contextFieldCount
	+ ", skipped=" + skippedCount
	+ ", meta_hit=" + metaHitCount
	+ ", meta_miss=" + metaMissCount
	+ ", lesson_id_resolve=" + lessonIdResolveCount
	+ ", lesson_start_url_hit=" + lessonStartUrlHitCount
	+ ", transcript_time_hit=" + transcriptTimeHitCount
	+ ", transcript_time_miss=" + transcriptTimeMissCount
	+ ", missing_time=" + missingTimeCount
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_total", list.size());
result.put("rst_data", list);
result.print();

%>
