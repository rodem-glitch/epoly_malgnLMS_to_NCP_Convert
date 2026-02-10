<%@ page contentType="text/html; charset=utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%

//객체
LessonDao lesson = new LessonDao();
CourseLessonDao courseLesson = new CourseLessonDao();
CourseSectionDao courseSection = new CourseSectionDao();
CourseProgressDao courseProgress = new CourseProgressDao();
FileDao file = new FileDao();
// 왜: 강의목차에서 서브영상(다중영상) 정보를 함께 보여주기 위해 조회합니다.
CourseLessonVideoDao courseLessonVideo = new CourseLessonVideoDao();

ExamDao exam = new ExamDao();
HomeworkDao homework = new HomeworkDao();
LibraryDao library = new LibraryDao();
CourseLibraryDao courseLibrary = new CourseLibraryDao();
ForumDao forum = new ForumDao();
SurveyDao survey = new SurveyDao();
ExamUserDao examUser = new ExamUserDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();
ForumUserDao forumUser = new ForumUserDao();
SurveyUserDao surveyUser = new SurveyUserDao();

ClPostDao clPost = new ClPostDao();

//변수
boolean isPrev = false;
boolean isPrevExam = false;
boolean isPrevHomework = false;
boolean isPrevForum = false;
boolean isPrevSurvey = false;
int totalSurveyCnt = 0;
int submitSurveyCnt = 0;

//목록-모듈
//String[] moduleNames = {"exam=>시험", "homework=>과제", "forum=>토론", "survey=>설문"};
//courseModule.setDebug(out);
DataSet modules = courseModule.find("status = 1 AND course_id = " + courseId,
	"*"
	+ ", CASE WHEN module = 'exam' AND (SELECT count(*) FROM " + examUser.table + " WHERE exam_id = " + courseModule.table + ".module_id AND course_user_id = " + cuid + " AND submit_yn = 'Y' AND status = 1) = 1 THEN 'Y'"
	+ " WHEN module = 'homework' AND (SELECT count(*) FROM " + homeworkUser.table + " WHERE homework_id = " + courseModule.table + ".module_id AND course_user_id = " + cuid + " AND submit_yn = 'Y' AND status = 1) = 1 THEN 'Y'"
	+ " WHEN module = 'forum' AND (SELECT count(*) FROM " + forumUser.table + " WHERE forum_id = " + courseModule.table + ".module_id AND course_user_id = " + cuid + " AND submit_yn = 'Y' AND status = 1) = 1 THEN 'Y'"
	+ " WHEN module = 'survey' AND (SELECT count(*) FROM " + surveyUser.table + " WHERE survey_id = " + courseModule.table + ".module_id AND course_user_id = " + cuid + " AND status = 1) = 1 THEN 'Y'"
	+ " ELSE 'N' END submit_yn"
	, "chapter, start_date ASC");

while(modules.next()) {
	modules.put("module_conv", m.getValue(modules.s("module"), courseModule.evaluationsMsg));
	modules.put("module_nm_conv", m.cutString(modules.s("module_nm"), 30));

	//상태 [progress] (W : 대기, E : 종료, I : 수강중, R : 복습중)
	boolean isReady = false; //대기
	boolean isEnd = false; //완료
	if("1".equals(modules.s("apply_type"))) { //기간
		modules.put("start_date_conv", m.time(_message.get("format.datetime.dot"), modules.s("start_date")));
		modules.put("end_date_conv", m.time(_message.get("format.datetime.dot"), modules.s("end_date")));

		isReady = 0 > m.diffDate("I", modules.s("start_date"), now);
		isEnd = 0 < m.diffDate("I", modules.s("end_date"), now);

		modules.put("apply_type_1", true);
		modules.put("apply_type_2", false);
	} else if("2".equals(modules.s("apply_type"))) { //차시
		modules.put("apply_conv", modules.i("chapter") == 0 ? _message.get("classroom.module.before_study") : _message.get("classroom.module.after_study", new String[] { "chapter=>" + modules.i("chapter") }));
		if(modules.i("chapter") > 0 && 0 == courseProgress.findCount("course_id = " + courseId + " AND chapter = " + modules.i("chapter") + " AND course_user_id = " + cuid + " AND complete_yn = 'Y'")) isReady = true;

		modules.put("apply_type_1", false);
		modules.put("apply_type_2", true);
	}

	String status = "-";
	if(modules.b("submit_yn")) status = _message.get("classroom.module.status.submit");
	else if(isReady) status = _message.get("classroom.module.status.waiting");
	else if(isEnd) status = _message.get("classroom.module.status.end");
	else status = _message.get("classroom.module.status.nosubmit");
	modules.put("status_conv", status);

	//처리-설문
	if("survey".equals(modules.s("module"))) {
		totalSurveyCnt++;
		if(modules.b("submit_yn")) submitSurveyCnt++;
	}
}

//목록
DataSet list = courseLesson.query(
	"SELECT a.* "
	+ ", l.onoff_type, l.lesson_nm, l.start_url, l.mobile_a, l.mobile_i, l.short_url, l.lesson_type, l.content_width, l.content_height, l.complete_time, l.total_time, l.total_page, l.status lesson_status "
	+ ", c.complete_yn, c.ratio, c.last_date, c.study_page, c.study_time, c.paragraph, c.complete_date, c.last_date, c.reg_date "
	+ ", cs.id section_id, cs.section_nm "
	+ ", ( CASE WHEN c.last_date BETWEEN '" + today + "000000' AND '" + today + "235959' THEN 'Y' ELSE 'N' END ) is_study "
	+ " FROM " + courseLesson.table + " a "
	+ " INNER JOIN " + lesson.table + " l ON a.lesson_id = l.id "
	+ " LEFT JOIN " + courseProgress.table + " c ON c.course_user_id = " + cuid + " AND a.lesson_id = c.lesson_id "
	+ " LEFT JOIN " + courseSection.table + " cs ON a.section_id = cs.id AND a.course_id = cs.course_id AND cs.status = 1 "
	+ " WHERE a.status = 1 AND a.course_id = " + courseId + " "
	+ " ORDER BY a.chapter ASC "
	//+ " ORDER BY a.chapter " + ("A".equals(cinfo.s("lesson_display_ord")) ? "ASC" : "DESC")
);

// 다중영상: 차시별 서브영상 목록/개수 사전 조회
// 왜: 학습자가 목록에서 바로 서브영상 유무와 제목을 확인하고 펼쳐볼 수 있게 합니다.
java.util.HashMap<Integer, DataSet> videoByLesson = new java.util.HashMap<Integer, DataSet>();
DataSet videoRows = courseLessonVideo.query(
	"SELECT v.lesson_id, v.video_id, v.sort"
	+ ", l.lesson_nm, l.total_time, l.complete_time "
	+ " FROM " + courseLessonVideo.table + " v "
	+ " INNER JOIN " + lesson.table + " l ON l.id = v.video_id AND l.status = 1 "
	+ " WHERE v.course_id = " + courseId
	+ " AND v.site_id = " + siteId
	+ " AND v.status = 1 "
	+ " ORDER BY v.lesson_id ASC, v.sort ASC "
);
while(videoRows.next()) {
	int lId = videoRows.i("lesson_id");
	if(!videoByLesson.containsKey(lId)) videoByLesson.put(lId, new DataSet());
	DataSet rows = videoByLesson.get(lId);
	rows.addRow();
	rows.put("ord", rows.size());
	rows.put("lesson_nm", videoRows.s("lesson_nm"));
	rows.put("total_time", videoRows.i("total_time"));
	rows.put("complete_time", videoRows.i("complete_time"));
	rows.put("video_id", videoRows.i("video_id"));
	rows.put("lesson_id", lId);
}

//학습제한-속진여부
boolean limitFlag = (cinfo.b("limit_lesson_yn") ? limitFlag = courseProgress.getLimitFlag(cuid, cinfo) : false);

//수강대기, 종료
boolean isWait = "W".equals(cuinfo.s("progress"));
boolean isEnd = "E".equals(cuinfo.s("progress"));
//boolean isRestudy = "R".equals(cuinfo.s("progress"));

int lastChapter = 1;
int lastSectionId = 0;
while(list.next()) {
	//다중영상 차시는 과정-차시 테이블의 합산시간을 사용합니다.
	if("Y".equals(list.s("multi_yn"))) {
		if(list.i("multi_total_time") > 0) list.put("total_time", list.i("multi_total_time"));
		if(list.i("multi_complete_time") > 0) list.put("complete_time", list.i("multi_complete_time"));
	}
	// 서브영상 정보 세팅
	if(videoByLesson.containsKey(list.i("lesson_id"))) {
		DataSet videos = videoByLesson.get(list.i("lesson_id"));
		list.put("sub_video_cnt", videos.size());
		list.put("sub_video_block", videos.size() > 0);
		list.put(".sub_video_list", videos);
	} else {
		list.put("sub_video_cnt", 0);
		list.put("sub_video_block", false);
		list.put(".sub_video_list", new DataSet());
	}
	String[] paragraph = m.split(",", m.replace(list.s("paragraph"), "'", ""));
	Arrays.sort(paragraph);
	list.put("study_min", list.i("study_time") / 60);
	list.put("study_sec", list.i("study_time") % 60);
	list.put("complete_date_conv", m.time(_message.get("format.datetime.dot"), list.s("complete_date")));
	list.put("last_date_conv", m.time(_message.get("format.datetime.dot"), !"".equals(list.s("last_date")) ? list.s("last_date") : list.s("reg_date")));
	list.put("reg_date_conv", m.time(_message.get("format.datetime.dot"), list.s("reg_date")));
	list.put("study_page", list.i("study_page"));
	list.put("paragraph_conv", m.join("<br>", paragraph));
	list.put("over_block", list.i("study_time") > (list.i("total_time") * 60));

	if(list.b("complete_yn")) lastChapter = list.i("chapter") + 1;
	//list.put("lesson_nm_conv", m.cutString(list.s("lesson_nm"), 50));
	list.put("lesson_nm_conv", list.s("lesson_nm"));
	list.put("study_date", !"".equals(list.s("last_date")) ? m.time(_message.get("format.datetime.dot"), list.s("last_date")) : "-");
	list.put("complete_conv", list.b("complete_yn") ? "Y" : "N");
	//list.put("attend_cnt", list.i("attend_cnt"));
	list.put("lesson_type_conv", m.getValue(list.s("lesson_type"), lesson.offlineTypesMsg));

	list.put("ratio_conv", m.nf(list.d("ratio"), 0));

	if("N".equals(list.s("onoff_type"))) {
		list.put("online_block", true);
		list.put("start_date_conv", m.time(_message.get("format.date.dot"), list.s("start_date")));
		list.put("end_date_conv", m.time(_message.get("format.date.dot"), list.s("end_date")));
		if(list.s("start_date").length() == 8 && list.s("end_date").length() == 8 && list.s("start_time").length() == 6 && list.s("end_time").length() == 6) {
			list.put("start_date_conv", m.time(_message.get("format.datetime.dot"), list.s("start_date") + list.s("start_time")));
			list.put("end_date_conv", m.time(_message.get("format.datetime.dot"), list.s("end_date") + list.s("end_time")));
		}
		list.put("date_conv", list.s("start_date_conv") + " 부터 <br> " + list.s("end_date_conv") + " 까지");
	} else if("F".equals(list.s("onoff_type"))) {
		list.put("online_block", false);
		list.put("start_date_conv", m.time(_message.get("format.date.dot"), list.s("start_date")));
		list.put("end_date_conv", m.time("HH:mm", list.s("start_date") + list.s("end_time")));
		list.put("date_conv", list.s("start_date_conv") + " <br> " + m.time("HH:mm", list.s("start_date") + list.s("start_time")) + " - " + list.s("end_date_conv"));
	} else if("T".equals(list.s("onoff_type"))) {
		list.put("online_block", true);
		list.put("date_conv", "-");
		list.put("start_date_conv", m.time(_message.get("format.date.dot"), list.s("start_date")));
		list.put("end_date_conv", m.time(_message.get("format.date.dot"), list.s("end_date")));
		if(list.s("start_date").length() == 8 && list.s("end_date").length() == 8 && list.s("start_time").length() == 6 && list.s("end_time").length() == 6) {
			list.put("start_date_conv", m.time(_message.get("format.datetime.dot"), list.s("start_date") + list.s("start_time")));
			list.put("end_date_conv", m.time(_message.get("format.datetime.dot"), list.s("end_date") + list.s("end_time")));
			list.put("date_conv", list.s("start_date_conv") + " 부터 <br> " + list.s("end_date_conv") + " 까지");
		}
	}

	DataSet files = file.getFileList(list.i("lesson_id"), "lesson");
	while(files.next()) {
		files.put("file_ext", file.getFileExt(files.s("filename")));
		files.put("filename_conv", m.urlencode(Base64Coder.encode(files.s("filename"))));
		files.put("ext", file.getFileIcon(files.s("filename")));
		files.put("ek", m.encrypt(files.s("id") + m.time("yyyyMMdd")));
		files.put("cek", m.encrypt(cuid + files.s("id") + list.s("lesson_id") + m.time("yyyyMMdd")));
		files.put("sep", !files.b("__last") ? "<br>" : "");
		files.put("filesize_conv", file.getFileSize(files.l("filesize")));
	}

	list.put(".files", files);
	list.put("file_block", files.size() > 0);

	if(cinfo.b("limit_ratio_yn")) {
		double limitStudyTime = list.i("total_time") * 60 * cinfo.d("limit_ratio");
		list.put("limit_study_min", (int)limitStudyTime / 60);
		list.put("limit_study_sec", (int)limitStudyTime % 60);
	}

	boolean isOpen = true;

	if(isOpen && limitFlag) { //속진제한
		isOpen = "Y".equals(list.s("is_study"));
		list.put("msg", cinfo.i("limit_day") + "일 " + cinfo.i("limit_lesson") + "차시로 학습이 제한되어 있습니다.\\n관리자에게 문의하십시오.");
	}
	if(isOpen && cinfo.b("period_yn")) { //수강기간 제한

		boolean isTwoway = "T".equals(list.s("onoff_type"));
		String startDateTime = list.s("start_date") + (list.s("start_time").length() == 6 ? list.s("start_time") : "000000");
		String endDateTime = list.s("end_date") + (list.s("end_time").length() == 6 ? list.s("end_time") : "235959");
		long nowDateTime = m.parseLong(now);

		if(isTwoway) {
			//화상강의에는 복습이 없음
			isOpen = m.parseLong(m.time("yyyyMMddHHmmss", m.addDate("I", -30, startDateTime))) <= nowDateTime && m.parseLong(endDateTime) >= nowDateTime;
		} else {
			//복습 허용시 강의수강 가능
			isOpen = m.parseLong(startDateTime) <= nowDateTime && (cinfo.b("restudy_yn") || m.parseLong(endDateTime) >= nowDateTime);
		}
		list.put("msg", "학습기간이 아닙니다.\\n관리자에게 문의하십시오.");

	}
	if(isOpen && cinfo.b("lesson_order_yn")) { //순차적용
		isOpen = lastChapter >= list.i("chapter");
		list.put("msg", (list.i("chapter") - 1) + "장을 학습하셔야 합니다.\\n(순차학습 과정입니다.)\\n관리자에게 문의하십시오.");
	}

	if("Y".equals(list.s("complete_yn")) && "N".equals(list.s("onoff_type"))) isOpen = true;

	if(cinfo.b("limit_ratio_yn") && (list.i("total_time") * 60 * cinfo.d("limit_ratio") < list.d("study_time"))) { //배수제한
		//total_time * limit_ratio < study_time
		isOpen = false;
		list.put("msg", "허용된 학습량을 초과하였습니다.");
	}

	if(isEnd || isWait) { //수강 대기, 종료
		isOpen = false;
		list.put("msg", "수강기간이 아닙니다.");
	}

	if(isPrev) {
		isOpen = false;
		if(isPrevExam) list.put("msg", "선행해야 하는 시험이 있습니다.");
		else if(isPrevHomework) list.put("msg", "선행해야 하는 과제가 있습니다.");
		else if(isPrevForum) list.put("msg", "선행해야 하는 토론이 있습니다.");
		else if(isPrevSurvey) list.put("msg", "선행해야 하는 설문이 있습니다.");
	}

	if(isOpen && 1 != list.i("lesson_status")) {
		isOpen = false;
		list.put("msg", _message.get("alert.lesson.stopped"));
	}

	list.put("open_block", isOpen);

	if(lastSectionId != list.i("section_id") && 0 < list.i("section_id")) {
		lastSectionId = list.i("section_id");
		list.put("section_block", true);
	} else {
		list.put("section_block", false);
	}
	
	list.put("download_block", siteinfo.b("download_yn") && "05".equals(list.s("lesson_type")));
}

//정렬
if("D".equals(cinfo.s("lesson_display_ord"))) {
	list.sort("chapter", "desc");
}

//선행
DataSet prev = new DataSet(); prev.addRow();
if(isPrev) {
	if(isPrevExam) {
		prev.put("msg", "선행해야 하는 시험이 있습니다. 시험방으로 이동합니다.");
		prev.put("link", "exam.jsp?cuid=" + cuid);
	} else if(isPrevHomework) {
		prev.put("msg", "선행해야 하는 과제가 있습니다. 과제방으로 이동합니다.");
		prev.put("link", "homework.jsp?cuid=" + cuid);
	} else if(isPrevForum) {
		prev.put("msg", "선행해야 하는 토론이 있습니다. 토론방으로 이동합니다.");
		prev.put("link", "forum.jsp?cuid=" + cuid);
	} else if(isPrevSurvey) {
		prev.put("msg", "선행해야 하는 설문이 있습니다. 설문방으로 이동합니다.");
		prev.put("link", "survey.jsp?cuid=" + cuid);
	}
}

//남은일수
cuinfo.put("term_day", "W".equals(progress) ? "학습대기" : ("E".equals(progress) ? "학습종료" : (alltime ? "상시" : m.diffDate("D", today, cuinfo.s("end_date")) + "일")));
cuinfo.put("t_day", m.diffDate("D", cuinfo.s("start_date"), cuinfo.s("end_date"))); //전체일수
cuinfo.put("d_day", "W".equals(progress) ? 0 : ("E".equals(progress) ? cuinfo.i("t_day") : cuinfo.s("past_day"))); //경과일수

//진도율
cuinfo.put("avg_progress_ratio", cu.getOne("SELECT AVG(progress_ratio) avg FROM " + cu.table + " WHERE course_id = '" + courseId + "' AND status IN (1,3)"));
cuinfo.put("my_progress", cuinfo.d("progress_ratio") == 0 ? "0" : m.nf(190 * cuinfo.d("progress_ratio") / 100, 0));
cuinfo.put("avg_progress", cuinfo.d("avg_progress_ratio") == 0 ? "0" : m.nf(190 * cuinfo.d("avg_progress_ratio") / 100, 0));
cuinfo.put("avg_progress_ratio" , m.nf(cuinfo.d("avg_progress_ratio"), 1));
cuinfo.put("progress_ratio" , m.nf(cuinfo.d("progress_ratio"), 1));

//정보-최근공지
DataSet notices = clPost.find("site_id = " + siteId + " AND course_id = " + cinfo.i("id") + " AND board_cd = 'notice' AND depth = 'A' AND display_yn = 'Y' AND status = 1", "*", "thread ASC", 5);
while(notices.next()) {
	notices.put("subject_conv", m.cutString(notices.s("subject"), 50));
	notices.put("reg_date_conv", m.time(_message.get("format.date.dot"), notices.s("reg_date")));
}

//정보-QNA
DataSet qnas = clPost.find("site_id = " + siteId + " AND course_id = " + cinfo.i("id") + " AND board_cd = 'qna' AND depth = 'A' AND display_yn = 'Y' AND status = 1", "*", "thread ASC", 5);
while(qnas.next()) {
	qnas.put("subject_conv", m.cutString(qnas.s("subject"), 50));
	qnas.put("reg_date_conv", m.time(_message.get("format.date.dot"), qnas.s("reg_date")));
}

//사이트설정
DataSet siteconfig = SiteConfig.getArr("classroom_");

//학사 커리큘럼(정규 탭) 데이터
String haksaCurriculumJson = "";
String haksaVideoEkMapJson = "{}";
String haksaVideoTimeMapJson = "{}";
String haksaVideoProgressMapJson = "{}";
int haksaWeekCount = 15;
if(cuinfo.b("is_haksa")) {
	PolyCourseSettingDao haksaSetting = new PolyCourseSettingDao();
	String hkCourseCode = cuinfo.s("haksa_course_code");
	String hkOpenYear = cuinfo.s("haksa_open_year");
	String hkOpenTerm = cuinfo.s("haksa_open_term");
	String hkBunbanCode = cuinfo.s("haksa_bunban_code");
	String hkGroupCode = cuinfo.s("haksa_group_code");

	// 왜: 혹시 init.jsp에서 키가 비어있을 때를 대비해, 요청 파라미터에서 한번 더 보정합니다.
	if("".equals(hkCourseCode) || "".equals(hkOpenYear) || "".equals(hkOpenTerm) || "".equals(hkBunbanCode) || "".equals(hkGroupCode)) {
		String haksaCuidParam = m.rs("haksa_cuid");
		if(!"".equals(haksaCuidParam)) {
			String[] parts = haksaCuidParam.split("_");
			if(parts.length >= 4) {
				if("".equals(hkCourseCode)) hkCourseCode = parts[0];
				if("".equals(hkOpenYear)) hkOpenYear = parts[1];
				if("".equals(hkOpenTerm)) hkOpenTerm = parts[2];
				if("".equals(hkBunbanCode)) hkBunbanCode = parts[3];
				if("".equals(hkGroupCode) && parts.length >= 5) hkGroupCode = parts[4];
			}
		}
	}

	if(!"".equals(hkCourseCode) && !"".equals(hkOpenYear) && !"".equals(hkOpenTerm) && !"".equals(hkBunbanCode) && !"".equals(hkGroupCode)) {
		DataSet haksaSettingInfo = haksaSetting.find(
			"site_id = " + siteId
			+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
			+ " AND status != -1"
			, new Object[] { hkCourseCode, hkOpenYear, hkOpenTerm, hkBunbanCode, hkGroupCode }
		);
		if(haksaSettingInfo.next()) {
			haksaCurriculumJson = haksaSettingInfo.s("curriculum_json");
		}
	}

	// 왜: 학사 과목의 주차 수는 DB에 있을 수 있어 우선 사용합니다.
	try {
		int wk = Integer.parseInt(cuinfo.s("haksa_week"));
		if(wk > 0) haksaWeekCount = wk;
	} catch(Exception ignore) {}

	// 왜: 동영상 재생을 위해 lessonId별 ek를 서버에서 미리 만들어 둡니다.
	try {
		if(!"".equals(haksaCurriculumJson)) {
			int haksaCourseId = m.parseInt(courseId);
			boolean curriculumChanged = false;
			String endDateTime = "";
			if(cuinfo.s("end_date").length() >= 8) endDateTime = cuinfo.s("end_date").substring(0, 8) + "235959";
			if("".equals(endDateTime)) endDateTime = now;

				JSONObject ekMap = new JSONObject();
				java.util.HashSet<Integer> haksaLessonIds = new java.util.HashSet<Integer>();
				java.util.HashSet<Integer> activeHaksaHomeworkIds = new java.util.HashSet<Integer>();
				if(haksaCourseId > 0) {
					DataSet activeHomeworkModules = courseModule.query(
						"SELECT cm.module_id "
						+ " FROM " + courseModule.table + " cm "
						+ " INNER JOIN " + homework.table + " h ON h.id = cm.module_id "
							+ " AND h.site_id = " + siteId + " AND h.status != -1 "
						+ " WHERE cm.course_id = " + haksaCourseId + " "
						+ " AND cm.module = 'homework' AND cm.status = 1 "
					);
					while(activeHomeworkModules.next()) {
						activeHaksaHomeworkIds.add(activeHomeworkModules.i("module_id"));
					}
				}
				int maxWeek = 0;
				JSONArray weeks = new JSONArray(haksaCurriculumJson);
				for(int i = 0; i < weeks.length(); i++) {
					JSONObject w = weeks.optJSONObject(i);
					if(w == null) continue;
				int wnum = w.optInt("weekNumber", 0);
				if(wnum > maxWeek) maxWeek = wnum;
				JSONArray sessions = w.optJSONArray("sessions");
				if(sessions == null) continue;
					for(int s = 0; s < sessions.length(); s++) {
						JSONObject sessionObj = sessions.optJSONObject(s);
						if(sessionObj == null) continue;
						JSONArray contents = sessionObj.optJSONArray("contents");
						if(contents == null) continue;
						JSONArray normalizedContents = new JSONArray();
						for(int c = 0; c < contents.length(); c++) {
							JSONObject content = contents.optJSONObject(c);
							if(content == null) continue;

							String contentType = content.optString("type", "");
							String title = content.optString("title", "");
							String safeTitle = m.replace(title, "'", "''");

							// 왜: 학사 커리큘럼에 등록된 과제/시험/자료가 실제 LMS 모듈과 연결되어야 학생이 제출/열람할 수 있습니다.
							if(haksaCourseId > 0 && "assignment".equalsIgnoreCase(contentType)) {
								int homeworkId = content.optInt("homeworkId", 0);
								// 왜: 운영에서 과제를 삭제하면 course_module 연결이 끊기므로,
								//     커리큘럼 JSON에 남아 있는 과제 항목도 학생 화면에서 같이 숨겨야 "삭제했는데 보임" 혼선을 막을 수 있습니다.
								if(homeworkId > 0 && !activeHaksaHomeworkIds.contains(homeworkId)) {
									curriculumChanged = true;
									m.log("haksa_curriculum", "[index] stale assignment removed course_id=" + haksaCourseId + ", homework_id=" + homeworkId + ", session_id=" + sessionObj.optString("sessionId", ""));
									continue;
								}
								if(homeworkId <= 0 && !"".equals(safeTitle)) {
									DataSet hwLink = courseModule.query(
										"SELECT module_id FROM " + courseModule.table
										+ " WHERE course_id = " + haksaCourseId + " AND module = 'homework' AND module_nm = '" + safeTitle + "' AND status = 1"
									);
								if(hwLink.next()) homeworkId = hwLink.i("module_id");
							}
							if(homeworkId <= 0) {
								String desc = content.optString("description", "");
								int newHomeworkId = homework.getSequence();
								homework.item("id", newHomeworkId);
								homework.item("site_id", siteId);
								homework.item("onoff_type", "N");
								homework.item("category_id", 0);
								homework.item("homework_nm", !"".equals(title) ? title : "과제");
								homework.item("content", desc);
								homework.item("manager_id", -99);
								homework.item("reg_date", m.time("yyyyMMddHHmmss"));
								homework.item("status", 1);
								if(homework.insert()) {
									courseModule.item("course_id", haksaCourseId);
									courseModule.item("site_id", siteId);
									courseModule.item("module", "homework");
									courseModule.item("module_id", newHomeworkId);
									courseModule.item("module_nm", !"".equals(title) ? title : "과제");
									courseModule.item("parent_id", 0);
									courseModule.item("item_type", "R");
									courseModule.item("assign_score", 0);
									courseModule.item("apply_type", "1");
									courseModule.item("start_day", 0);
									courseModule.item("period", 0);
									courseModule.item("start_date", now);
									courseModule.item("end_date", endDateTime);
									courseModule.item("chapter", 0);
									courseModule.item("retry_yn", "N");
									courseModule.item("retry_score", 0);
									courseModule.item("retry_cnt", 0);
									courseModule.item("review_yn", "N");
									courseModule.item("result_yn", "Y");
									courseModule.item("status", 1);
									if(courseModule.insert()) {
										homeworkId = newHomeworkId;
										activeHaksaHomeworkIds.add(homeworkId);
									}
									else {
										homework.item("status", -1);
										homework.update("id = " + newHomeworkId);
									}
								}
							}
							if(homeworkId > 0) {
								content.put("homeworkId", homeworkId);
								curriculumChanged = true;
							}
						} else if(haksaCourseId > 0 && "document".equalsIgnoreCase(contentType)) {
							int libraryId = content.optInt("libraryId", 0);
							if(libraryId <= 0 && !"".equals(safeTitle)) {
								DataSet libLink = courseLibrary.query(
									"SELECT l.id FROM " + courseLibrary.table + " cl "
									+ " INNER JOIN " + library.table + " l ON l.id = cl.library_id AND l.status = 1 "
									+ " WHERE cl.course_id = " + haksaCourseId + " AND l.library_nm = '" + safeTitle + "' "
								);
								if(libLink.next()) libraryId = libLink.i("id");
							}
							if(libraryId <= 0) {
								String desc = content.optString("description", "-");
								if("".equals(desc)) desc = "-";
								int newLibraryId = library.getSequence();
								library.item("id", newLibraryId);
								library.item("site_id", siteId);
								library.item("category_id", 0);
								library.item("library_nm", !"".equals(title) ? title : "학습자료");
								library.item("content", desc);
								library.item("library_file", "");
								library.item("library_link", "");
								library.item("download_cnt", 0);
								library.item("manager_id", -99);
								library.item("reg_date", m.time("yyyyMMddHHmmss"));
								library.item("status", 1);
								if(library.insert()) {
									courseLibrary.item("course_id", haksaCourseId);
									courseLibrary.item("library_id", newLibraryId);
									courseLibrary.item("site_id", siteId);
									if(courseLibrary.insert()) libraryId = newLibraryId;
									else {
										library.item("status", -1);
										library.update("id = " + newLibraryId);
									}
								}
							}
							if(libraryId > 0) {
								content.put("libraryId", libraryId);
								curriculumChanged = true;
							}
						} else if(haksaCourseId > 0 && "exam".equalsIgnoreCase(contentType)) {
							int examModuleId = content.optInt("examModuleId", 0);
							int examTemplateId = 0;
							try { examTemplateId = Integer.parseInt(content.optString("examId", "0")); } catch(Exception ignore) {}

							if(examModuleId <= 0 && examTemplateId > 0) {
								if(0 < courseModule.findCount("course_id = " + haksaCourseId + " AND module = 'exam' AND module_id = " + examTemplateId + " AND status = 1")) {
									examModuleId = examTemplateId;
								} else {
									JSONObject examSettings = content.optJSONObject("examSettings");
									boolean allowRetake = examSettings != null && examSettings.optBoolean("allowRetake", false);
									int retakeScore = examSettings != null ? examSettings.optInt("retakeScore", 0) : 0;
									int retakeCount = examSettings != null ? examSettings.optInt("retakeCount", 0) : 0;
									boolean showResults = examSettings == null || examSettings.optBoolean("showResults", true);
									int assignScore = examSettings != null ? examSettings.optInt("points", 0) : 0;

									courseModule.item("course_id", haksaCourseId);
									courseModule.item("site_id", siteId);
									courseModule.item("module", "exam");
									courseModule.item("module_id", examTemplateId);
									courseModule.item("module_nm", !"".equals(title) ? title : "시험");
									courseModule.item("parent_id", 0);
									courseModule.item("item_type", "R");
									courseModule.item("assign_score", assignScore);
									courseModule.item("apply_type", "1");
									courseModule.item("start_day", 0);
									courseModule.item("period", 0);
									courseModule.item("start_date", now);
									courseModule.item("end_date", endDateTime);
									courseModule.item("chapter", 0);
									courseModule.item("retry_yn", allowRetake ? "Y" : "N");
									courseModule.item("retry_score", retakeScore);
									courseModule.item("retry_cnt", retakeCount);
									courseModule.item("review_yn", "N");
									courseModule.item("result_yn", showResults ? "Y" : "N");
									courseModule.item("status", 1);
									if(courseModule.insert()) examModuleId = examTemplateId;
								}
							}
							if(examModuleId > 0) {
								content.put("examModuleId", examModuleId);
								curriculumChanged = true;
							}
						}

						if(!"video".equalsIgnoreCase(contentType)) {
							normalizedContents.put(content);
							continue;
						}
						int lid = content.optInt("lessonId", 0);
						String rawLessonId = content.optString("lessonId", "").trim();
						// 왜: 커리큘럼 lessonId가 문자열로 저장된 경우(예: mediaKey)도 숫자 ID로 정규화해야 학생 재생이 정상 동작합니다.
						if(lid <= 0 && !"".equals(rawLessonId)) {
							try { lid = Integer.parseInt(rawLessonId); } catch(Exception ignore) {}
						}
						if(lid <= 0) {
							String mediaKey = content.optString("mediaKey", "").trim();
							if(!"".equals(mediaKey)) {
								String safeMediaKey = m.replace(mediaKey, "'", "''");
								DataSet lfind = lesson.find(
									"site_id = " + siteId
									+ " AND start_url = '" + safeMediaKey + "'"
									+ " AND lesson_type = '05'"
									+ " AND status != -1"
									, "id"
								);
								if(lfind.next()) {
									lid = lfind.i("id");
								} else {
									int totalTime = content.optInt("totalTime", 0);
									if(totalTime <= 0) totalTime = content.optInt("total_time", 0);
									int completeTime = content.optInt("completeTime", 0);
									if(completeTime <= 0) completeTime = content.optInt("complete_time", 0);
									if(totalTime <= 0 && completeTime > 0) totalTime = completeTime;
									if(completeTime <= 0 && totalTime > 0) completeTime = totalTime;
									int contentWidth = content.optInt("contentWidth", 0);
									if(contentWidth <= 0) contentWidth = content.optInt("content_width", 0);
									int contentHeight = content.optInt("contentHeight", 0);
									if(contentHeight <= 0) contentHeight = content.optInt("content_height", 0);
									String lessonTitle = content.optString("title", "");

									int newLessonId = lesson.getSequence();
									lesson.clear();
									lesson.item("id", newLessonId);
									lesson.item("site_id", siteId);
									lesson.item("content_id", 0);
									lesson.item("lesson_nm", !"".equals(lessonTitle) ? lessonTitle : ("콜러스 " + mediaKey));
									lesson.item("onoff_type", "N");
									lesson.item("lesson_type", "05");
									lesson.item("author", "");
									lesson.item("start_url", mediaKey);
									lesson.item("mobile_a", mediaKey);
									lesson.item("mobile_i", mediaKey);
									lesson.item("total_page", 0);
									lesson.item("total_time", totalTime);
									lesson.item("complete_time", completeTime);
									lesson.item("content_width", contentWidth);
									lesson.item("content_height", contentHeight);
									lesson.item("description", "");
									lesson.item("manager_id", userId);
									lesson.item("use_yn", "Y");
									lesson.item("sort", 0);
									lesson.item("reg_date", m.time("yyyyMMddHHmmss"));
									lesson.item("status", 1);

									if(lesson.insert()) {
										lid = newLessonId;
										m.log("haksa_curriculum", "[index] lesson created course_id=" + haksaCourseId + ", media_key=" + mediaKey + ", lesson_id=" + lid);
									} else {
										m.log("haksa_curriculum", "[index] lesson create failed course_id=" + haksaCourseId + ", media_key=" + mediaKey);
									}
								}
							}
						}
						if(lid <= 0) {
							m.log("haksa_curriculum", "[index] unresolved video lessonId course_id=" + haksaCourseId + ", session_id=" + sessionObj.optString("sessionId", "") + ", raw_lesson_id=" + rawLessonId);
							normalizedContents.put(content);
							continue;
						}
						if(content.optInt("lessonId", 0) != lid) {
							content.put("lessonId", lid);
							curriculumChanged = true;
							m.log("haksa_curriculum", "[index] lessonId normalized course_id=" + haksaCourseId + ", session_id=" + sessionObj.optString("sessionId", "") + ", lesson_id=" + lid);
						}
						String lidKey = "" + lid;
						if(!ekMap.has(lidKey)) {
							ekMap.put(lidKey, m.encrypt(lid + "|0|" + m.time("yyyyMMdd")));
						}
						// 왜: 학사 탭에서 인정시간(complete_time)을 보여주기 위해 영상 ID를 모아둡니다.
						haksaLessonIds.add(Integer.valueOf(lid));
						normalizedContents.put(content);
					}
					sessionObj.put("contents", normalizedContents);
				}
			}

			// 왜: 학사 커리큘럼에는 영상 시간 정보가 없을 수 있어, DB에서 인정시간을 보완합니다.
			if(haksaLessonIds.size() > 0) {
				StringBuilder idList = new StringBuilder();
				for(Integer idVal : haksaLessonIds) {
					if(idList.length() > 0) idList.append(",");
					idList.append(idVal.intValue());
				}
				JSONObject timeMap = new JSONObject();
				DataSet timeRows = lesson.query(
					"SELECT id, total_time, complete_time"
					+ " FROM " + lesson.table
					+ " WHERE site_id = " + siteId
					+ " AND status = 1"
					+ " AND id IN (" + idList.toString() + ")"
				);
				while(timeRows.next()) {
					JSONObject t = new JSONObject();
					t.put("total_time", timeRows.i("total_time"));
					t.put("complete_time", timeRows.i("complete_time"));
					timeMap.put("" + timeRows.i("id"), t);
				}
				haksaVideoTimeMapJson = timeMap.toString();

				// 왜: 학사 탭에서 내 진도(시청 시간)를 보여주기 위해 사용자별 학습시간을 조회합니다.
				if(cuid > 0) {
					JSONObject progressMap = new JSONObject();
					java.util.HashSet<Integer> missingIds = new java.util.HashSet<Integer>();
					DataSet progressRows = courseProgress.query(
						"SELECT lesson_id, study_time"
						+ " FROM " + courseProgress.table
						+ " WHERE course_user_id = " + cuid
						+ " AND lesson_id IN (" + idList.toString() + ")"
					);
					while(progressRows.next()) {
						progressMap.put("" + progressRows.i("lesson_id"), progressRows.i("study_time"));
					}

					// 왜: 다중영상(서브영상) 진도는 LM_COURSE_PROGRESS_VIDEO에 저장되므로,
					//     LM_COURSE_PROGRESS에 없는 영상은 서브영상 테이블에서 보완합니다.
					for(Integer idVal : haksaLessonIds) {
						if(!progressMap.has("" + idVal.intValue())) missingIds.add(idVal);
					}
					if(missingIds.size() > 0) {
						StringBuilder missingList = new StringBuilder();
						for(Integer idVal : missingIds) {
							if(missingList.length() > 0) missingList.append(",");
							missingList.append(idVal.intValue());
						}
						CourseProgressVideoDao cpv = new CourseProgressVideoDao(siteId);
						DataSet pvRows = cpv.query(
							"SELECT video_id, MAX(study_time) study_time"
							+ " FROM " + cpv.table
							+ " WHERE course_user_id = " + cuid
							+ " AND video_id IN (" + missingList.toString() + ")"
							+ " GROUP BY video_id"
						);
						while(pvRows.next()) {
							progressMap.put("" + pvRows.i("video_id"), pvRows.i("study_time"));
						}
					}

					haksaVideoProgressMapJson = progressMap.toString();
				}
			}

			if(curriculumChanged) {
				haksaCurriculumJson = weeks.toString();
				haksaSetting.PK = "site_id,course_code,open_year,open_term,bunban_code,group_code";
				haksaSetting.useSeq = "N";
				haksaSetting.item("curriculum_json", haksaCurriculumJson);
				haksaSetting.item("mod_date", now);
				String safeCourseCode = m.replace(hkCourseCode, "'", "''");
				String safeOpenYear = m.replace(hkOpenYear, "'", "''");
				String safeOpenTerm = m.replace(hkOpenTerm, "'", "''");
				String safeBunbanCode = m.replace(hkBunbanCode, "'", "''");
				String safeGroupCode = m.replace(hkGroupCode, "'", "''");
				haksaSetting.update(
					"site_id = " + siteId
					+ " AND course_code = '" + safeCourseCode + "'"
					+ " AND open_year = '" + safeOpenYear + "'"
					+ " AND open_term = '" + safeOpenTerm + "'"
					+ " AND bunban_code = '" + safeBunbanCode + "'"
					+ " AND group_code = '" + safeGroupCode + "'"
					+ " AND status != -1"
				);
			}

			if(haksaWeekCount <= 0) haksaWeekCount = maxWeek;
			if(haksaWeekCount <= 0) haksaWeekCount = 15;
			haksaVideoEkMapJson = ekMap.toString();
		}
	} catch(Exception ignore) {}
}

//출력
p.setLayout(ch);
p.setBody("classroom.index");
p.setVar("query", m.qs());

p.setLoop("list", list);
p.setLoop("module_list", modules);
p.setLoop("notice_list", notices);
p.setLoop("qna_list", qnas);
p.setVar("cuinfo", cuinfo);

/* p.setVar(
	"renew_block"
	, cinfo.b("renew_yn") && "A".equals(cinfo.s("course_type")) && "N".equals(cinfo.s("onoff_type"))
	&& (0 == cinfo.i("renew_max_cnt") || cinfo.i("renew_max_cnt") > cuinfo.i("renew_cnt")) && (0 <= m.diffDate("D", m.time("yyyyMMdd"), cuinfo.s("end_date")))
); */
p.setVar("renew_block", courseUser.setRenewBlock(cuinfo.getRow()));
p.setVar("prev_block", isPrev);
p.setVar("prev", prev);

p.setVar("section_colspan", 6 + (cinfo.b("period_yn") ? 1 : 0));

p.setVar("push_survey_block"
	, "I".equals(progress) && cinfo.b("push_survey_yn") //&& !cuinfo.b("complete_yn")
	&& (cinfo.d("limit_progress") <= cuinfo.d("progress_ratio"))
	&& totalSurveyCnt > submitSurveyCnt
);

p.setVar("SITE_CONFIG", siteconfig);
p.setVar("haksa_curriculum_json", haksaCurriculumJson);
p.setVar("haksa_video_ek_map_json", haksaVideoEkMapJson);
p.setVar("haksa_video_time_map_json", haksaVideoTimeMapJson);
p.setVar("haksa_video_progress_map_json", haksaVideoProgressMapJson);
p.setVar("haksa_week_count", haksaWeekCount);
p.display();

%>
