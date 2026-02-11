<%@ page contentType="text/html; charset=utf-8" %><%@ page import="java.io.*" %><%@ page import="java.net.*" %><%@ page import="java.nio.charset.StandardCharsets" %><%@ page import="malgnsoft.json.*" %><%@ include file="init.jsp" %><%@ include file="/kollus/thumb_util.jspf" %><%

//기본키
String subject = m.rs("subject");
String keyword = m.replace(m.rs("s_keyword").trim().replaceAll("[^가-힣a-zA-Z0-9\\s]", ""), " ", "|");
while(-1 < keyword.indexOf("||")) keyword = m.replace(keyword, "||", "|");
if(keyword.startsWith("|")) keyword = keyword.substring(1);
if(keyword.endsWith("|")) keyword = keyword.substring(0, keyword.length() - 1);
if("".equals(subject) || "".equals(keyword)) { m.jsError(_message.get("alert.common.enter_keyword")); return; }
if(100 < keyword.length()) keyword = m.cutString(keyword, 100, "");

// 왜: 신규 메인에서 들어온 검색은 동일한 헤더/푸터를 써야 합니다.
String chParam = m.rs("ch");
boolean isNewMain = "new_main".equals(chParam);

//객체
UserDao user = new UserDao();
CourseDao course = new CourseDao();
PostDao post = new PostDao();
ClPostDao clPost = new ClPostDao();
BoardDao board = new BoardDao();
TutorDao tutor = new TutorDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseTargetDao courseTarget = new CourseTargetDao();
WebtvDao webtv = new WebtvDao();
WebtvTargetDao webtvTarget = new WebtvTargetDao();
LmCategoryDao category = new LmCategoryDao();
LmCategoryTargetDao categoryTarget = new LmCategoryTargetDao();
SearchLogDao searchLog = new SearchLogDao(request);

// 사용자 정보 (헤더 표시용)
DataSet uinfo = null;
if(userId > 0) {
	uinfo = user.find("id = " + userId + " AND status = 1");
	if(!uinfo.next()) uinfo = null;
}

//로그
if(!searchLog.addLog(siteId, userId, m.rs("s_keyword"))) { }


//폼체크
Form f2 = new Form("form_search");
f2.setRequest(request);
f2.addElement("s_keyword", null, null);

//변수
String today = m.time("yyyyMMdd");
String searchType = "post".equals(subject) ? "post" : ("webtv".equals(subject) ? "webtv" : "course");
String[] subjectList = {"tutor=>강사", "course=>강의", "webtv=>방송", "post=>게시물"};
ListManager lm = new ListManager();
DataSet list = new DataSet();

// 왜: 강의(=과정) 상세 '더보기' 화면에서도 벡터검색 결과를 전체로 보고 싶다는 요구가 있어서,
//     subject=course 이면서 mode=vector면 DB REGEXP 대신 벡터검색 API 결과를 그대로 출력합니다.
String mode = m.rs("mode").trim();

if("course".equals(searchType) && "vector".equals(mode)) {
	// ===== 벡터(추천) 검색 - 학생 검색 추천(동영상/강의) 전체 =====
	// 주의: JSP에는 기본 내장객체로 JspWriter `out`이 이미 있어서, 변수명 충돌을 피해야 합니다.
	DataSet vectorList = new DataSet();
	int vectorSummaryCount = 0;
	int vectorKeywordCount = 0;
	try {
		String vectorQuery = m.rs("s_keyword").trim();
		vectorQuery = vectorQuery.replaceAll("[^가-힣a-zA-Z0-9\\s]", " ");
		vectorQuery = vectorQuery.replaceAll("\\s+", " ").trim();
		if(vectorQuery.length() > 200) vectorQuery = m.cutString(vectorQuery, 200, "");

		if(!"".equals(vectorQuery)) {
			String apiBase = System.getenv("POLYTECH_LMS_API_BASE");
			if(apiBase == null || "".equals(apiBase.trim())) apiBase = "http://localhost:8081";
			apiBase = apiBase.replaceAll("/+$", "");

			String url = apiBase + "/student/content-recommend/search";

			JSONObject payload = new JSONObject();
			if(userId > 0) payload.put("userId", userId);
			payload.put("siteId", siteId);
			payload.put("query", vectorQuery);
			payload.put("topK", 200);

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

				KollusDao kollus = new KollusDao(siteId);
				KollusMediaDao kollusMedia = new KollusMediaDao();
				HashMap<String, String> thumbCache = new HashMap<String, String>();

				// 왜: 검색 추천 결과의 lessonId(콜러스 키)는 TB_KOLLUS_MEDIA에 없을 수 있습니다(찜/매핑 전).
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
					String lessonId = recoRows.s("lessonId");  // 콜러스 영상 키값 (예: "5vcd73vW")
					if("".equals(lessonId)) continue;

					vectorList.addRow();
					vectorList.put("is_reco_video", true);
					vectorList.put("id", lessonId);
					vectorList.put("course_nm", recoRows.s("title"));
					vectorList.put("course_nm_conv", m.cutString(recoRows.s("title"), 48));
					vectorList.put("category_nm", recoRows.s("categoryNm"));
					// 왜: 강의 더보기에서도 검색 미리보기와 같은 요약 정보를 보여야 사용자 기대와 화면이 일치합니다.
					String summaryConv = m.nl2br(m.stripTags(recoRows.s("summary")));
					vectorList.put("subtitle_conv", summaryConv);
					if(!"".equals(summaryConv)) vectorSummaryCount++;
					// 왜: 더보기에서도 카테고리 대신 키워드를 보여줘야 검색 목록과 정보 구성이 동일합니다.
					String keywordsConv = m.stripTags(recoRows.s("keywords"));
					vectorList.put("keywords_conv", keywordsConv);
					if(!"".equals(keywordsConv)) vectorKeywordCount++;
					vectorList.put("onoff_type_conv", "온라인");
					vectorList.put("recomm_yn", true);

					// 왜: 외부 콜러스 영상은 학습 상태가 없으므로 빈 값
					vectorList.put("status_badge", "");

					// 왜: 학생도 교수자처럼 작은 팝업으로 미리보기 재생이 필요합니다.
					String playUrl = "/kollus/preview.jsp?key=" + lessonId;
					vectorList.put("play_url", playUrl);

					vectorList.put("duration_conv", "");
					// 왜: 가능하면 TB_KOLLUS_MEDIA를 우선 사용하고, 없으면 콜러스 API에서 가져와 캐싱합니다.
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
					vectorList.put("course_file_url", thumbnail);
				}
				// 왜: 더보기 화면에서 summary 노출 건수를 남겨 두면, 검색-더보기 불일치 이슈를 운영에서 빠르게 확인할 수 있습니다.
				m.log(
					"search_vector",
					"mode=detail site_id=" + siteId
					+ ", user_id=" + userId
					+ ", qlen=" + vectorQuery.length()
					+ ", reco_total=" + vectorList.size()
					+ ", summary_total=" + vectorSummaryCount
					+ ", keyword_total=" + vectorKeywordCount
				);
			}
		}
	} catch(Exception e) {
		// 왜: 추천/검색 서버 장애가 나도 검색(더보기) 화면이 통째로 깨지면 안 되므로, 추천 결과만 비워 둡니다.
		malgnsoft.util.Malgn.errorLog("검색 추천 동영상(더보기) 조회 실패(site_id=" + siteId + ", user_id=" + userId + "): " + e.getMessage(), e);
	}

	list = vectorList;
} else if("post".equals(searchType)) {
	//목록
	//lm.d(out);
	lm.setRequest(request);
	lm.setListNum(10);
	lm.setTable(
		post.table + " a "
		+ " INNER JOIN " + board.table + " b ON b.id = a.board_id AND b.site_id = " + siteId + " AND b.status = 1 "
			+ (userId > 0 ? " AND b.auth_list LIKE '%|U|%' " : " AND b.auth_list LIKE '%|0|%' ")
	);
	lm.setFields("a.*, b.board_nm, b.code, b.layout, b.comment_yn");
	lm.addWhere("(a.subject REGEXP ? OR a.content REGEXP ?)", new Object[] {keyword, keyword});
	lm.addWhere("a.site_id = " + siteId);
	lm.addWhere("a.secret_yn = 'N'");
	lm.addWhere("a.display_yn = 'Y'");
	lm.addWhere("a.status = 1");
	lm.addWhere("a.depth = 'A'");
	lm.setOrderBy("a.id desc");

	//포맷팅
	list = lm.getDataSet();
	while(list.next()) {
		list.put("subject_conv", m.cutString(list.s("subject"), 80));
		list.put("reg_date_conv", m.time(_message.get("format.date.dot"), list.s("reg_date")));
		list.put("hit_cnt_conv", m.nf(list.i("hit_cnt")));
		list.put("comment_conv", list.b("comment_yn") && list.i("comm_cnt") > 0 ? "(" + list.i("comm_cnt") + ")" : "");
	}
} else if("webtv".equals(searchType)) {
	//목록
	//lm.d(out);
	lm.setRequest(request);
	lm.setListNum(10);
	lm.setTable(
		webtv.table + " a "
		+ " INNER JOIN " + category.table + " c ON a.category_id = c.id AND c.status = 1 "
	);
	lm.setFields("a.*, c.category_nm, IF('" + today + "' >= a.open_date, 'Y', 'N') is_open");
	lm.addWhere("(a.webtv_nm REGEXP ? OR a.subtitle REGEXP ? OR a.content REGEXP ? OR a.keywords REGEXP ?)", new Object[] {keyword, keyword, keyword, keyword}); 
	lm.addWhere("a.site_id = " + siteId + "");
	lm.addWhere("a.display_yn = 'Y'");
	lm.addWhere("a.status = 1");
	lm.addWhere("a.open_date <= '" + m.time("yyyyMMddHHmmss") + "'");
	lm.addWhere( //시청대상그룹
		"(a.target_yn = 'N'"
		+ (!"".equals(userGroups)
			? " OR EXISTS (SELECT 1 FROM " + webtvTarget.table + " WHERE webtv_id = a.id AND group_id IN (" + userGroups + "))"
			: "")
		+ ")"
	);
	lm.addWhere( //카테고리시청대상그룹
		"(c.target_yn = 'N'" //카테고리시청대상그룹
		+ (!"".equals(userGroups)
			? " OR EXISTS (SELECT 1 FROM " + categoryTarget.table + " WHERE category_id = c.id AND group_id IN (" + userGroups + "))"
			: "")
		+ ")"
	);
	lm.setOrderBy("a.open_date desc, a.id desc");

	//포맷팅
	list = lm.getDataSet();
	while(list.next()) {
		list.put("webtv_nm_conv", m.cutString(list.s("webtv_nm"), 70));
		
		list.put("subtitle_conv", m.nl2br(list.s("subtitle")));
		list.put("length_conv", m.strrpad(list.s("length_min"), 2, "0") + ":" + m.strrpad(list.s("length_sec"), 2, "0"));

		if(!"".equals(list.s("webtv_file"))) {
			list.put("webtv_file_url", m.getUploadUrl(list.s("webtv_file")));
		} else if("".equals(list.s("webtv_file_url"))) {
			list.put("webtv_file_url", "/common/images/default/noimage_webtv.jpg");
		}

		list.put("content_width_conv", list.i("content_width") + 20);
		list.put("content_height_conv", list.i("content_height") + 23);
		
		list.put("hit_cnt_conv", m.nf(list.i("hit_cnt")));
		list.put("reg_date_conv", m.time(_message.get("format.datetime.dot"), list.s("reg_date")));
		list.put("open_date_conv", m.time(_message.get("format.datetime.dot"), list.s("open_date")));

	}
} else {
	//목록
	//lm.d(out);
	lm.setRequest(request);
	lm.setListNum(10);
	lm.setTable(
		course.table + " a "
		+ " LEFT JOIN " + category.table + " c ON a.category_id = c.id "
		+ ("tutor".equals(subject) ?
			" INNER JOIN " + courseTutor.table + " ct ON ct.course_id = a.id "
			+ " INNER JOIN " + tutor.table + " t ON t.user_id = ct.user_id AND t.status = 1 "
		: "")
	);
	lm.setFields("a.*, c.category_nm");
	if("course".equals(subject)) lm.addWhere("a.course_nm REGEXP ?", new Object[] {keyword}); 
	else if("tutor".equals(subject)) lm.addWhere("t.tutor_nm REGEXP ?", new Object[] {keyword});
	lm.addWhere("a.site_id = " + siteId + "");
	lm.addWhere("a.status = 1");
	lm.addWhere("a.display_yn = 'Y'");
	lm.addWhere("a.close_yn = 'N'");
	lm.addWhere(
		"(a.target_yn = 'N'"
		+ (!"".equals(userGroups)
			? " OR EXISTS (SELECT 1 FROM " + courseTarget.table + " WHERE course_id = a.id AND group_id IN (" + userGroups + "))"
			: "")
		+ ")"
	);
	lm.setOrderBy("a.id desc");

	//포맷팅
	list = lm.getDataSet();
	while(list.next()) {
		list.put("request_date", "-");
		if("R".equals(list.s("course_type"))) {
			list.put("is_regular", true);
			list.put("request_date", m.time(_message.get("format.date.dot"), list.s("request_sdate")) + " - " + m.time(_message.get("format.date.dot"), list.s("request_edate")));
			list.put("study_date", m.time(_message.get("format.date.dot"), list.s("study_sdate")) + " - " + m.time(_message.get("format.date.dot"), list.s("study_edate")));
			list.put("ready_block", 0 > m.diffDate("D", list.s("request_sdate"), today));
		} else if("A".equals(list.s("course_type"))) {
			list.put("is_regular", false);
			list.put("request_date", "상시");
			list.put("study_date", "상시");
			list.put("ready_block", false);
		}

		list.put("course_nm_conv", m.cutString(list.s("course_nm"), 48));
		
		list.put("subtitle_conv", m.nl2br(list.s("subtitle")));
		list.put("content_conv", m.cutString(m.stripTags(list.s("subtitle")), 120));
		//list.put("content_conv", !"".equals(list.s("subtitle_conv")) ? list.s("subtitle_conv") : m.cutString(m.stripTags(list.s("content1")), 120));

		if(!"".equals(list.s("course_file"))) {
			list.put("course_file_url", m.getUploadUrl(list.s("course_file")));
		} else {
			list.put("course_file_url", "/html/images/common/noimage_course.gif");
		}
		
		list.put("is_online", "N".equals(list.s("onoff_type")));
		list.put("is_offline", "F".equals(list.s("onoff_type")));
		list.put("is_blend", "B".equals(list.s("onoff_type")));
		list.put("is_package", "P".equals(list.s("onoff_type")));
		list.put("onoff_type_conv", m.getValue(list.s("onoff_type"), course.onoffPackageTypesMsg));

		list.put("tutor_nm", courseTutor.getTutorSummary(list.i("id")));
	}
}

//출력
// 왜: 신규 메인에서 들어온 검색은 새 레이아웃을 씁니다.
p.setLayout(isNewMain ? "new_main" : "search");
p.setBody("main.search_detail");
p.setVar("p_title", m.getItem(subject, subjectList));
p.setVar("query", m.qs());
p.setVar("list_query", m.qs("id"));
p.setVar("subject_query", m.qs("id,subject,page"));
p.setVar("form_script", f2.getScript());

// 헤더용 사용자 정보
p.setVar("login_block", userId > 0 && uinfo != null);
if(userId > 0 && uinfo != null) {
	p.setVar("SYS_USERNAME", uinfo.s("user_nm"));
	String userNameForHeader = uinfo.s("user_nm");
	p.setVar("SYS_USERNAME_INITIAL", userNameForHeader.length() > 0 ? userNameForHeader.substring(0, 1) : "?");
} else {
	p.setVar("SYS_USERNAME", "");
	p.setVar("SYS_USERNAME_INITIAL", "");
}

p.setLoop("list", list);
if("course".equals(searchType) && "vector".equals(mode)) {
	p.setVar("pagebar", "");
	p.setVar("search_total", list.size());
} else {
	p.setVar("pagebar", lm.getPaging());
	p.setVar("search_total", lm.getTotalNum());
}

//p.setVar("s_keyword", keyword.replaceAll("\\|", " "));
p.setVar(searchType + "_block", true);
p.display();

%>
