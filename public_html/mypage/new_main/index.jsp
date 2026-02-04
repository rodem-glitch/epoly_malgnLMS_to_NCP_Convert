<%@ page contentType="text/html; charset=utf-8" %>
<%@ page import="java.io.*" %>
<%@ page import="java.net.*" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.*" %>
<%@ page import="malgnsoft.json.*" %>
<%@ include file="../../init.jsp" %>
<%@ include file="/kollus/thumb_util.jspf" %><%

// -------------------------------------------------------------------
// 목적: /mypage/new_main 전용 신규 메인 페이지(Full-screen)
// 레이아웃: GrowAI 스타일 (히어로 섹션, 필터 탭, 카드 그리드)
// 비로그인 접속 허용: 계속 학습하기 섹션만 비활성화
// -------------------------------------------------------------------

//SSO 첫 방문 동의 체크
// 왜: SSO 연동 사이트에서 첫 방문(신규 메인 진입) 시 개인정보 수집·이용 및 제공 동의를 받아야 합니다.
//     새 페이지를 만들지 않고 기존 동의 화면(member/privacy_agree.jsp)을 재사용합니다.
if(userId > 0 && siteinfo.b("sso_yn")) {
	AgreementLogDao agreementLog = new AgreementLogDao(p, siteId);
	String consentModule = "sso_20260120";
	boolean agreed = "Y".equals(agreementLog.getOne(
		"SELECT agreement_yn FROM " + agreementLog.table
		+ " WHERE user_id = " + userId
		+ " AND type = 'sso'"
		+ " AND module = '" + consentModule + "'"
		+ " ORDER BY reg_date DESC"
	));
	if(!agreed) {
		String qs = m.qs("");
		String cur = request.getRequestURI() + ("".equals(qs) ? "" : "?" + qs);
		String pek = m.encrypt("PRIVACY_" + userId + "_AGREE_" + m.time("yyyyMMdd"));
		m.log("agreement_gate_" + siteId, "path=/mypage/new_main/index.jsp user_id=" + userId + " type=sso module=" + consentModule);
		m.redirect("/member/privacy_agree.jsp?id=" + userId + "&ek=" + pek + "&ag=sso&returl=" + m.urlencode(cur));
		return;
	}
}

//객체
UserDao user = new UserDao();
UserDeptDao userDept = new UserDeptDao();
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
LmCategoryDao lmCategory = new LmCategoryDao("course");
StudentRecoPromptDao recoPrompt = new StudentRecoPromptDao();

PolyStudentDao polyStudent = new PolyStudentDao();
PolyCourseDao polyCourse = new PolyCourseDao();
PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();

BoardDao board = new BoardDao();
PostDao post = new PostDao();
ClBoardDao clBoard = new ClBoardDao();
ClPostDao clPost = new ClPostDao();

TutorDao tutor = new TutorDao();
CourseTutorDao courseTutor = new CourseTutorDao();
LessonDao lesson = new LessonDao();
KollusDao kollus = new KollusDao(siteId);

//변수
String today = m.time("yyyyMMdd");

// 사용자 정보 (로그인 상태에서만)
DataSet uinfo = null;
if(userId > 0) {
	uinfo = user.find("id = " + userId + " AND status = 1");
	if(!uinfo.next()) {
		uinfo = null;
	}
}

// 추천 프롬프트 (왜: 홈 추천 쿼리는 학생이 저장한 문장만 사용합니다.)
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

//===== 강의 섹션 - 운영중인 과목 리스트 (4개) - 폴리텍 콘텐츠(cid=133) 기준 =====
String subIdx = lmCategory.getSubIdx(siteId, 133);
String courseOrd = "a.request_edate DESC, a.reg_date DESC, a.id DESC"; // 기본 정렬

// 카테고리 정보에서 정렬 타입 가져오기
DataSet cateInfo = lmCategory.find("id = 133");
if(cateInfo.next() && !"".equals(cateInfo.s("sort_type"))) {
	courseOrd = cateInfo.s("sort_type");
}

DataSet courseList = course.query(
	" SELECT a.id, a.course_nm, a.course_file, a.content1, a.course_type, a.study_sdate, a.study_edate "
	+ " FROM " + course.table + " a "
	+ " WHERE a.site_id = " + siteId + " "
	+ " AND a.status = 1 AND a.display_yn = 'Y' AND a.close_yn = 'N' "
	+ " AND a.category_id IN (" + (!"".equals(subIdx) ? subIdx : "133") + ") "
	+ " ORDER BY " + courseOrd
	, 4
);
while(courseList.next()) {
	// 썸네일
	if(!"".equals(courseList.s("course_file"))) {
		courseList.put("course_file_url", m.getUploadUrl(courseList.s("course_file")));
	} else {
		courseList.put("course_file_url", "/html/images/common/noimage_course.gif");
	}
	
	// 과목명
	courseList.put("course_nm_conv", m.cutString(courseList.s("course_nm"), 30));
	
	// 과목설명
	courseList.put("content_conv", m.cutString(m.stripTags(courseList.s("content1")), 60));
	
	// 교육기간
	if("R".equals(courseList.s("course_type"))) {
		courseList.put("study_date", m.time(_message.get("format.date.dot"), courseList.s("study_sdate")) + " - " + m.time(_message.get("format.date.dot"), courseList.s("study_edate")));
	} else {
		courseList.put("study_date", "상시수강");
	}
	
	// 담당강사
	DataSet tutorInfo = courseTutor.query(
		" SELECT t.tutor_nm FROM " + courseTutor.table + " ct "
		+ " LEFT JOIN " + tutor.table + " t ON ct.user_id = t.user_id "
		+ " WHERE ct.course_id = " + courseList.i("id") + " "
		+ " ORDER BY t.sort ASC, t.tutor_nm ASC LIMIT 1 "
	);
	if(tutorInfo.next()) {
		courseList.put("tutor_nm", tutorInfo.s("tutor_nm"));
	} else {
		courseList.put("tutor_nm", "-");
	}
}

//===== 추천 동영상 섹션 - 학생 홈 추천(4개) =====
// 왜: 화면에서는 "추천 동영상" 4개만 보여주면 되므로, JSP에서 추천 API를 호출해 4개만 loop로 내려줍니다.
// - 장점: 같은 도메인/세션을 그대로 쓰기 때문에 CORS/인증 이슈가 없고, 화면단은 렌더링만 하면 됩니다.
DataSet recoVideoList = new DataSet();
try {
	// 추천 API 주소(환경별로 다를 수 있어 env로 제어)
	String apiBase = System.getenv("POLYTECH_LMS_API_BASE");
	if(apiBase == null || "".equals(apiBase.trim())) apiBase = "http://localhost:8081";
	apiBase = apiBase.replaceAll("/+$", "");

	String url = apiBase + "/student/content-recommend/home";

	JSONObject payload = new JSONObject();
	// 왜: userId가 있어야 "수강/시청/완료 제외"가 동작합니다. 비로그인일 때는 null로 보내서 일반 추천만 받습니다.
	if(userId > 0) payload.put("userId", userId);
	payload.put("siteId", siteId);
	payload.put("topK", 4);
	// 왜: 학생이 저장한 프롬프트만 기준으로 추천합니다.
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
			String lessonId = recoRows.s("lessonId");  // 콜러스 영상 키값 (예: "5vcd73vW")
			if("".equals(lessonId)) continue;

			recoVideoList.addRow();
			recoVideoList.put("lesson_id", lessonId);
			recoVideoList.put("title", recoRows.s("title"));
			recoVideoList.put("category_nm", recoRows.s("categoryNm"));
			recoVideoList.put("score", recoRows.s("score"));

			// 왜: 학생도 교수자처럼 작은 팝업으로 미리보기 재생이 필요합니다.
			String playUrl = "/kollus/preview.jsp?key=" + lessonId;
			recoVideoList.put("play_url", playUrl);

			// 왜: 외부 영상은 시간 정보가 없으므로 빈 값
			recoVideoList.put("duration_conv", "");

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
			recoVideoList.put("thumbnail", thumbnail);
		}
	}
} catch(Exception e) {
	// 왜: 추천 서버/네트워크가 잠깐 실패해도 메인 화면 전체가 깨지면 안 되므로, 추천 섹션만 비워 둡니다.
	malgnsoft.util.Malgn.errorLog("학생 홈 추천 동영상 조회 실패(site_id=" + siteId + ", user_id=" + userId + "): " + e.getMessage(), e);
}

//===== 로그인 상태에서만 계속 학습하기 관련 데이터 조회 =====
DataSet coursesPrism = new DataSet();
DataSet coursesHaksa = new DataSet();
DataSet qnaList = new DataSet();
DataSet noticeList = new DataSet();

if(userId > 0 && uinfo != null) {
	//목록-수강중인과정(비정규/LMS)
	coursesPrism = courseUser.query(
		" SELECT a.*, c.year, c.step, c.course_nm, c.course_type, c.onoff_type, c.course_file, c.credit "
		+ " FROM " + courseUser.table + " a "
		+ " INNER JOIN " + course.table + " c ON a.course_id = c.id "
		+ " WHERE a.user_id = " + userId + " AND a.status IN (1, 3) "
		+ " AND IFNULL(c.etc2, '') != 'HAKSA_MAPPED' "
		+ " AND a.close_yn = 'N' AND a.end_date >= '" + today + "' "
		+ " ORDER BY a.start_date DESC, a.id DESC "
		, 6
	);
	while(coursesPrism.next()) {
		coursesPrism.put("start_date_conv", m.time(_message.get("format.date.dot"), coursesPrism.s("start_date")));
		coursesPrism.put("end_date_conv", m.time(_message.get("format.date.dot"), coursesPrism.s("end_date")));
		coursesPrism.put("study_date_conv", m.time(_message.get("format.date.dot"), coursesPrism.s("start_date")) + " - " + m.time(_message.get("format.date.dot"), coursesPrism.s("end_date")));
		coursesPrism.put("course_nm_conv", m.cutString(m.htt(coursesPrism.s("course_nm")), 40));
		coursesPrism.put("progress_ratio_conv", m.nf(coursesPrism.d("progress_ratio"), 0));

		if(!"".equals(coursesPrism.s("course_file"))) {
			coursesPrism.put("course_file_url", m.getUploadUrl(coursesPrism.s("course_file")));
		} else {
			coursesPrism.put("course_file_url", "/html/images/common/noimage_course.gif");
		}
	}

	//===== 정규(학사) 수강중인 과정 =====
	String memberKey = "";
	DataSet memberKeyInfo = polyMemberKey.find("alias_key = '" + uinfo.s("login_id") + "'");
	if(memberKeyInfo.next()) {
		memberKey = memberKeyInfo.s("member_key");
	} else {
		memberKey = uinfo.s("login_id");
	}

	String currentYear = m.time("yyyy");
	coursesHaksa = polyStudent.query(
		" SELECT s.*, c.course_name, c.course_ename, c.dept_name, c.grad_name, c.week, c.grade "
		+ " , c.curriculum_name, c.category, c.startdate, c.enddate, c.hour1, c.classroom "
		+ " FROM " + polyStudent.table + " s "
		+ " INNER JOIN " + polyCourse.table + " c ON s.course_code = c.course_code "
		+ "   AND s.open_year = c.open_year AND s.open_term = c.open_term "
		+ "   AND s.bunban_code = c.bunban_code AND s.group_code = c.group_code "
		+ " WHERE s.member_key = '" + memberKey + "' "
		+ " AND s.open_year = '" + currentYear + "' "
		+ " ORDER BY c.startdate DESC, c.course_name ASC "
		, 6
	);
	while(coursesHaksa.next()) {
		String startdate = coursesHaksa.s("startdate");
		String enddate = coursesHaksa.s("enddate");
		if(startdate.length() >= 8) {
			coursesHaksa.put("start_date_conv", m.time(_message.get("format.date.dot"), startdate));
		} else {
			coursesHaksa.put("start_date_conv", startdate);
		}
		if(enddate.length() >= 8) {
			coursesHaksa.put("end_date_conv", m.time(_message.get("format.date.dot"), enddate));
		} else {
			coursesHaksa.put("end_date_conv", enddate);
		}
		coursesHaksa.put("study_date_conv", coursesHaksa.s("start_date_conv") + " - " + coursesHaksa.s("end_date_conv"));
		coursesHaksa.put("course_nm_conv", m.cutString(coursesHaksa.s("course_name"), 40));
		coursesHaksa.put("onoff_type_conv", "".equals(coursesHaksa.s("category")) ? "정규" : coursesHaksa.s("category"));
		
		String haksaCuid = coursesHaksa.s("course_code") + "_" + coursesHaksa.s("open_year") 
			+ "_" + coursesHaksa.s("open_term") + "_" + coursesHaksa.s("bunban_code") + "_" + coursesHaksa.s("group_code");
		coursesHaksa.put("haksa_cuid", haksaCuid);
	}

	//목록-QNA
	qnaList = post.query(
		" ( SELECT a.id, 0 course_user_id, a.subject, a.proc_status, a.reg_date, b.board_nm, b.code "
		+ " FROM " + post.table + " a "
		+ " INNER JOIN " + board.table + " b ON a.board_id = b.id AND b.board_type = 'qna' AND b.site_id = " + siteId + " "
		+ " WHERE a.display_yn = 'Y' AND a.status = 1 AND a.user_id = " + userId + " AND a.depth = 'A' ORDER BY a.reg_date DESC LIMIT 5 ) "
		+ " UNION " 
		+ " ( SELECT a.id, a.course_user_id, a.subject, a.proc_status, a.reg_date, c.course_nm board_nm, b.code "
		+ " FROM " + clPost.table + " a "
		+ " INNER JOIN " + clBoard.table + " b ON a.board_id = b.id AND b.board_type = 'qna' AND b.site_id = " + siteId + " "
		+ " LEFT JOIN " + course.table + " c ON b.course_id = c.id AND c.site_id = " + siteId + " AND c.status != -1 "
		+ " WHERE a.display_yn = 'Y' AND a.status = 1 AND a.user_id = " + userId + " AND a.depth = 'A' ORDER BY a.reg_date DESC LIMIT 5 ) "
		+ " ORDER BY reg_date DESC "
	);
	while(qnaList.next()) {
		qnaList.put("subject_conv", m.cutString(qnaList.s("subject"), 50));
		qnaList.put("reg_date_conv", m.time(_message.get("format.date.dot"), qnaList.s("reg_date")));
		qnaList.put("proc_status_conv", m.getValue(qnaList.s("proc_status"), post.procStatusListMsg));
	}

	//공지사항 목록
	noticeList = post.query(
			"SELECT a.id, a.subject, a.reg_date " +
			" FROM " + clPost.table + " a " +
			" WHERE a.board_cd = 'notice' and a.display_yn = 'Y' AND a.status = 1 AND a.depth = 'A' " +
			" AND exists (select 1 from " + courseUser.table + " b where b.user_id = " + userId + " and b.course_id = a.course_id) " +
			" ORDER BY reg_date DESC LIMIT 5"
	);
	while(noticeList.next()) {
		noticeList.put("subject_conv", m.cutString(noticeList.s("subject"), 50));
		noticeList.put("reg_date_conv", m.time(_message.get("format.date.dot"), noticeList.s("reg_date")));
	}
}

// 레이아웃: blank (전역 네비게이션 제외)
p.setLayout("blank");
p.setBody("mypage.new_main_full");

p.setVar("p_title", "미래형 직업교육 플랫폼");
p.setVar("query", m.qs());
p.setVar("form_script", f.getScript());

// 로그인 상태 및 사용자 정보
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

p.setLoop("courses_prism", coursesPrism);
p.setLoop("courses_haksa", coursesHaksa);
p.setLoop("course_list", courseList);
p.setLoop("reco_video_list", recoVideoList);
p.setLoop("qna_list", qnaList);
p.setLoop("notice_list", noticeList);

p.display();

%>
