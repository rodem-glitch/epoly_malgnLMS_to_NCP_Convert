<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 학사 과목 강의목차(주차/차시/콘텐츠)를 DB에 저장합니다.
//- 그리고 "목차에 등록한 시험/과제/자료"가 학생 화면(강의평가/시험방/과제방/자료)에 바로 보이도록,
//  실제 LMS 모듈(LM_COURSE_MODULE, LM_HOMEWORK, LM_LIBRARY 등)과 자동으로 연동해 줍니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

f.addElement("course_code", null, "hname:'강좌코드', required:'Y'");
f.addElement("open_year", null, "hname:'연도', required:'Y'");
f.addElement("open_term", null, "hname:'학기', required:'Y'");
f.addElement("bunban_code", null, "hname:'분반코드', required:'Y'");
f.addElement("group_code", null, "hname:'학부/대학원 구분', required:'Y'");
f.addElement("curriculum_json", "", "hname:'강의목차 JSON'");

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

String courseCode = f.get("course_code");
String openYear = f.get("open_year");
String openTerm = f.get("open_term");
String bunbanCode = f.get("bunban_code");
String groupCode = f.get("group_code");
String curriculumJson = f.get("curriculum_json");

PolyCourseSettingDao setting = new PolyCourseSettingDao();
// 왜: Resin이 예전 클래스(기본 PK=id)로 로딩한 상태여도, 여기서 명시적으로 고정해 INSERT(id 자동추가) 오류를 막습니다.
setting.PK = "site_id,course_code,open_year,open_term,bunban_code,group_code";
setting.useSeq = "N";

// =========================
// 0) 기존 커리큘럼(삭제 비교용) 로드
// =========================
String oldCurriculumJson = "";
try {
	DataSet oldInfo = setting.find(
		"site_id = " + siteId
		+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
		+ " AND status != -1"
		, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
	);
	if(oldInfo.next()) oldCurriculumJson = oldInfo.s("curriculum_json");
} catch(Exception ignore) {}

// =========================
// 1) 학사키 → LMS 과정ID 매핑
// =========================
int mappedCourseId = 0;
String endDateTime = m.time("yyyyMMddHHmmss");
try {
	String haksaCourseKey = courseCode + "_" + openYear + "_" + openTerm + "_" + bunbanCode + "_" + groupCode;
	String haksaCourseCd = m.md5(haksaCourseKey);
	if(haksaCourseCd != null && haksaCourseCd.length() > 20) haksaCourseCd = haksaCourseCd.substring(0, 20);
	if(haksaCourseCd == null) haksaCourseCd = "";

	String safeHaksaCourseKey = m.replace(haksaCourseKey, "'", "''");
	String safeHaksaCourseCd = m.replace(haksaCourseCd, "'", "''");

	CourseDao course = new CourseDao();
	DataSet mappedCourse = course.find(
		"site_id = " + siteId
		+ " AND (course_cd = '" + safeHaksaCourseCd + "' OR course_cd = '" + safeHaksaCourseKey + "' OR etc1 = '" + safeHaksaCourseKey + "')"
		+ " AND status != -1"
	);
	if(mappedCourse.next()) {
		mappedCourseId = mappedCourse.i("id");
		String studyEdate = mappedCourse.s("study_edate");
		if(studyEdate != null && studyEdate.length() >= 8) endDateTime = studyEdate.substring(0, 8) + "235959";
	}
} catch(Exception ignore) {}

// =========================
// 2) JSON 정규화(v1 → v2)
// =========================
JSONArray weeks = new JSONArray();
try {
	String safeJson = curriculumJson;
	if(safeJson == null) safeJson = "";
	safeJson = safeJson.trim();
	if("".equals(safeJson)) safeJson = "[]";

	JSONArray rawArr = new JSONArray(safeJson);
	JSONObject first = rawArr.length() > 0 ? rawArr.optJSONObject(0) : null;
	boolean isV2 = first != null && first.has("sessions");
	boolean isV1 = !isV2 && first != null && first.has("type");

	if(isV2) {
		weeks = rawArr;
	} else if(isV1) {
		// 왜: 구형 데이터(콘텐츠 배열)를 주차→차시 구조로 감싸 줘야 화면(학생/교수자)이 정상 렌더링됩니다.
		java.util.HashMap<Integer, JSONArray> weekContents = new java.util.HashMap<Integer, JSONArray>();
		int maxWeek = 1;
		for(int i = 0; i < rawArr.length(); i++) {
			JSONObject c = rawArr.optJSONObject(i);
			if(c == null) continue;
			int w = c.optInt("weekNumber", 1);
			if(w <= 0) w = 1;
			if(w > maxWeek) maxWeek = w;
			JSONArray list = weekContents.get(w);
			if(list == null) { list = new JSONArray(); weekContents.put(w, list); }
			list.put(c);
		}
		for(int w = 1; w <= maxWeek; w++) {
			JSONArray list = weekContents.get(w);
			if(list == null) list = new JSONArray();

			JSONObject sessionObj = new JSONObject();
			sessionObj.put("sessionId", "session_migrated_" + w);
			sessionObj.put("sessionName", "1차시");
			sessionObj.put("isExpanded", w == 1);
			sessionObj.put("contents", list);

			JSONObject weekObj = new JSONObject();
			weekObj.put("weekNumber", w);
			weekObj.put("title", w + "주차");
			weekObj.put("isExpanded", w == 1);
			weekObj.put("sessions", new JSONArray().put(sessionObj));
			weeks.put(weekObj);
		}
	} else {
		// 알 수 없는 형식이면 빈 목차로 처리(서버 저장 시 깨진 JSON을 확산시키지 않기 위함)
		weeks = new JSONArray();
	}
} catch(Exception e) {
	result.put("rst_code", "1002");
	result.put("rst_message", "강의목차 JSON 형식이 올바르지 않습니다.");
	result.print();
	return;
}

// =========================
// 3) 목차 ↔ LMS 모듈 연동(시험/과제/자료)
// =========================
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();
LibraryDao library = new LibraryDao();
CourseLibraryDao courseLibrary = new CourseLibraryDao();
CourseLessonDao courseLesson = new CourseLessonDao();
LessonDao lesson = new LessonDao();
CourseProgressDao courseProgress = new CourseProgressDao(siteId);

java.util.HashSet<Integer> newHomeworkIds = new java.util.HashSet<Integer>();
java.util.HashSet<Integer> newExamModuleIds = new java.util.HashSet<Integer>();
java.util.HashSet<Integer> newLibraryIds = new java.util.HashSet<Integer>();
java.util.HashSet<String> newVideoKeys = new java.util.HashSet<String>(); // lesson_id:chapter
java.util.HashSet<Integer> changedVideoLessonIds = new java.util.HashSet<Integer>();

java.util.HashSet<Integer> oldHomeworkIds = new java.util.HashSet<Integer>();
java.util.HashSet<Integer> oldExamModuleIds = new java.util.HashSet<Integer>();
java.util.HashSet<Integer> oldLibraryIds = new java.util.HashSet<Integer>();
java.util.HashSet<String> oldVideoKeys = new java.util.HashSet<String>(); // lesson_id:chapter

// 왜: 삭제 동기화를 위해 "이전 목차에 있었던 모듈"만 추려 둡니다.
try {
	if(oldCurriculumJson != null && !"".equals(oldCurriculumJson.trim())) {
		JSONArray oldArr = new JSONArray(oldCurriculumJson);
		JSONObject oldFirst = oldArr.length() > 0 ? oldArr.optJSONObject(0) : null;
		boolean oldIsV2 = oldFirst != null && oldFirst.has("sessions");
		JSONArray oldWeeks = oldIsV2 ? oldArr : null;

		// v1은 여기서 굳이 변환하지 않고 "type이 있는 배열"로만 수집합니다.
		if(oldWeeks != null) {
			int oldSeq = 0;
			for(int i = 0; i < oldWeeks.length(); i++) {
				JSONObject w = oldWeeks.optJSONObject(i);
				if(w == null) continue;
				JSONArray sessions = w.optJSONArray("sessions");
				if(sessions == null) continue;
				for(int s = 0; s < sessions.length(); s++) {
					JSONObject sessionObj = sessions.optJSONObject(s);
					if(sessionObj == null) continue;
					oldSeq++;
					int oldSessionNo = sessionObj.optInt("sessionNo", 0);
					if(oldSessionNo <= 0) oldSessionNo = oldSeq;
					JSONArray contents = sessionObj.optJSONArray("contents");
					if(contents == null) continue;
					for(int c = 0; c < contents.length(); c++) {
						JSONObject content = contents.optJSONObject(c);
						if(content == null) continue;
						String t = content.optString("type", "");
						// 왜: 과거/외부 데이터는 type 값이 homework/file/library 등으로 들어올 수 있어,
						//     삭제 동기화가 누락되지 않도록 여기서 표준 타입으로 맞춰 비교합니다.
						if("homework".equalsIgnoreCase(t)) t = "assignment";
						else if("file".equalsIgnoreCase(t) || "library".equalsIgnoreCase(t)) t = "document";
						if("assignment".equalsIgnoreCase(t)) {
							int hid = content.optInt("homeworkId", 0);
							if(hid > 0) oldHomeworkIds.add(hid);
						} else if("exam".equalsIgnoreCase(t)) {
							int eid = content.optInt("examModuleId", 0);
							if(eid <= 0) {
								try { eid = Integer.parseInt(content.optString("examId", "0")); } catch(Exception ignore) {}
							}
							if(eid > 0) oldExamModuleIds.add(eid);
						} else if("document".equalsIgnoreCase(t)) {
							int lid = content.optInt("libraryId", 0);
							if(lid > 0) oldLibraryIds.add(lid);
						} else if("video".equalsIgnoreCase(t)) {
							int lessonId = content.optInt("lessonId", 0);
							if(lessonId > 0) oldVideoKeys.add(lessonId + ":" + oldSessionNo);
						}
					}
				}
			}
		} else {
			for(int i = 0; i < oldArr.length(); i++) {
				JSONObject content = oldArr.optJSONObject(i);
				if(content == null) continue;
				String t = content.optString("type", "");
				if("homework".equalsIgnoreCase(t)) t = "assignment";
				else if("file".equalsIgnoreCase(t) || "library".equalsIgnoreCase(t)) t = "document";
				if("assignment".equalsIgnoreCase(t)) {
					int hid = content.optInt("homeworkId", 0);
					if(hid > 0) oldHomeworkIds.add(hid);
				} else if("exam".equalsIgnoreCase(t)) {
					int eid = content.optInt("examModuleId", 0);
					if(eid <= 0) {
						try { eid = Integer.parseInt(content.optString("examId", "0")); } catch(Exception ignore) {}
					}
					if(eid > 0) oldExamModuleIds.add(eid);
				} else if("document".equalsIgnoreCase(t)) {
					int lid = content.optInt("libraryId", 0);
					if(lid > 0) oldLibraryIds.add(lid);
				} else if("video".equalsIgnoreCase(t)) {
					int lessonId = content.optInt("lessonId", 0);
					if(lessonId > 0) oldVideoKeys.add(lessonId + ":1");
				}
			}
		}
	}
} catch(Exception ignore) {}

boolean curriculumChanged = false;
String now = m.time("yyyyMMddHHmmss");

if(mappedCourseId > 0) {
	try {
		int seq = 0;
		for(int i = 0; i < weeks.length(); i++) {
			JSONObject w = weeks.optJSONObject(i);
			if(w == null) continue;
			JSONArray sessions = w.optJSONArray("sessions");
			if(sessions == null) continue;

			for(int s = 0; s < sessions.length(); s++) {
				JSONObject sessionObj = sessions.optJSONObject(s);
				if(sessionObj == null) continue;
				seq++;
				int sessionNo = sessionObj.optInt("sessionNo", 0);
				if(sessionNo <= 0) sessionNo = seq;

				// 차시 기간(없으면 빈 값)
				String sDate = sessionObj.optString("startDate", "");
				String eDate = sessionObj.optString("endDate", "");
				String sTime = sessionObj.optString("startTime", "");
				String eTime = sessionObj.optString("endTime", "");

				// 왜: 날짜/시간 포맷이 2026-01-20, 04:11 처럼 섞여 들어올 수 있어 숫자만 남겨 DB 포맷(yyyyMMdd / HHmmss)으로 맞춥니다.
				try {
					if(sDate != null) sDate = sDate.replaceAll("[^0-9]", "");
					if(eDate != null) eDate = eDate.replaceAll("[^0-9]", "");
					if(sTime != null) sTime = sTime.replaceAll("[^0-9]", "");
					if(eTime != null) eTime = eTime.replaceAll("[^0-9]", "");
				} catch(Exception ignore2) {}
				if(sDate == null) sDate = "";
				if(eDate == null) eDate = "";
				if(sTime == null) sTime = "";
				if(eTime == null) eTime = "";

				if(sDate.length() >= 8) sDate = sDate.substring(0, 8);
				if(eDate.length() >= 8) eDate = eDate.substring(0, 8);
				if(sTime.length() >= 4) sTime = sTime.substring(0, 4) + "00";
				if(eTime.length() >= 4) eTime = eTime.substring(0, 4) + "59";

				JSONArray contents = sessionObj.optJSONArray("contents");
				if(contents == null) continue;

				for(int c = 0; c < contents.length(); c++) {
					JSONObject content = contents.optJSONObject(c);
					if(content == null) continue;

					String contentType = content.optString("type", "");
					// 왜: 학생/교수자 화면 모두 같은 타입(assignment/document/exam/video) 기준으로 동작하도록 표준화합니다.
					if("homework".equalsIgnoreCase(contentType)) {
						content.put("type", "assignment");
						contentType = "assignment";
						curriculumChanged = true;
					} else if("file".equalsIgnoreCase(contentType) || "library".equalsIgnoreCase(contentType)) {
						content.put("type", "document");
						contentType = "document";
						curriculumChanged = true;
					}
					String title = content.optString("title", "");
					String safeTitle = m.replace(title, "'", "''");

					// 과제(=homework)
					if("assignment".equalsIgnoreCase(contentType)) {
						int homeworkId = content.optInt("homeworkId", 0);

						// 왜: 삭제했다가 다시 추가하는 경우를 위해 status=0도 재사용 대상으로 봅니다.
						if(homeworkId <= 0 && !"".equals(safeTitle)) {
							DataSet hwLink = courseModule.query(
								"SELECT module_id FROM " + courseModule.table
								+ " WHERE course_id = " + mappedCourseId + " AND module = 'homework' AND module_nm = '" + safeTitle + "' AND status IN (0,1)"
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
							homework.item("manager_id", userId);
							homework.item("reg_date", now);
							homework.item("status", 1);

							if(homework.insert()) {
								courseModule.item("course_id", mappedCourseId);
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

								if(courseModule.insert()) homeworkId = newHomeworkId;
								else {
									homework.item("status", -1);
									homework.update("id = " + newHomeworkId);
								}
							}
						} else {
							// 왜: 기존에 이미 운영 중인 과제 모듈은 start/end/적용방식이 있을 수 있어,
							//     여기서 임의로 기간을 덮어쓰지 않고 "표시용 제목 + 활성화(status)"만 맞춥니다.
							int cmCount = courseModule.findCount(
								"course_id = " + mappedCourseId + " AND module = 'homework' AND module_id = " + homeworkId
							);
							if(cmCount > 0) {
								courseModule.clear();
								courseModule.item("module_nm", !"".equals(title) ? title : "과제");
								courseModule.item("status", 1);
								courseModule.update(
									"course_id = " + mappedCourseId
									+ " AND module = 'homework' AND module_id = " + homeworkId
								);
							} else {
								// 왜: homework는 존재하지만 course_module 연결이 없는 경우, 연결을 새로 만들어 줍니다.
								courseModule.clear();
								courseModule.item("course_id", mappedCourseId);
								courseModule.item("site_id", siteId);
								courseModule.item("module", "homework");
								courseModule.item("module_id", homeworkId);
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
								courseModule.insert();
							}
						}

						if(homeworkId > 0) {
							if(content.optInt("homeworkId", 0) != homeworkId) {
								content.put("homeworkId", homeworkId);
								curriculumChanged = true;
							}
							newHomeworkIds.add(homeworkId);
						}
					}
					// 자료(=library)
					else if("document".equalsIgnoreCase(contentType)) {
						int libraryId = content.optInt("libraryId", 0);
						if(libraryId <= 0 && !"".equals(safeTitle)) {
							DataSet libLink = courseLibrary.query(
								"SELECT l.id FROM " + courseLibrary.table + " cl "
								+ " INNER JOIN " + library.table + " l ON l.id = cl.library_id AND l.status = 1 "
								+ " WHERE cl.course_id = " + mappedCourseId + " AND l.library_nm = '" + safeTitle + "' "
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
							library.item("manager_id", userId);
							library.item("reg_date", now);
							library.item("status", 1);

							if(library.insert()) {
								courseLibrary.item("course_id", mappedCourseId);
								courseLibrary.item("library_id", newLibraryId);
								courseLibrary.item("site_id", siteId);
								if(courseLibrary.insert()) libraryId = newLibraryId;
								else {
									library.item("status", -1);
									library.update("id = " + newLibraryId);
								}
							}
						} else {
							// 기존 자료면 링크 테이블이 없을 수 있어 보장합니다.
							if(0 == courseLibrary.findCount("course_id = " + mappedCourseId + " AND library_id = " + libraryId)) {
								courseLibrary.item("course_id", mappedCourseId);
								courseLibrary.item("library_id", libraryId);
								courseLibrary.item("site_id", siteId);
								courseLibrary.insert();
							}
						}

						if(libraryId > 0) {
							if(content.optInt("libraryId", 0) != libraryId) {
								content.put("libraryId", libraryId);
								curriculumChanged = true;
							}
							newLibraryIds.add(libraryId);
						}
					}
					// 시험(=exam)
					else if("exam".equalsIgnoreCase(contentType)) {
						int examModuleId = content.optInt("examModuleId", 0);
						int examTemplateId = 0;
						try { examTemplateId = Integer.parseInt(content.optString("examId", "0")); } catch(Exception ignore) {}
						if(examModuleId <= 0 && examTemplateId > 0) examModuleId = examTemplateId;

						if(examModuleId > 0) {
							JSONObject examSettings = content.optJSONObject("examSettings");
							boolean hasExamSettings = examSettings != null;
							boolean allowRetake = hasExamSettings && examSettings.optBoolean("allowRetake", false);
							int retakeScore = hasExamSettings ? examSettings.optInt("retakeScore", 0) : 0;
							int retakeCount = hasExamSettings ? examSettings.optInt("retakeCount", 0) : 0;
							boolean showResults = !hasExamSettings || examSettings.optBoolean("showResults", true);
							int assignScore = hasExamSettings ? examSettings.optInt("points", 0) : 0;

							int cmCount = courseModule.findCount(
								"course_id = " + mappedCourseId + " AND module = 'exam' AND module_id = " + examModuleId
							);
							if(cmCount > 0) {
								// 왜: 기존 운영 중인 시험 모듈은 기간/적용방식이 있을 수 있어,
								//     여기서 start/end를 덮어쓰지 않고 표시용 정보(제목/배점/재응시/노출/활성화)만 맞춥니다.
								courseModule.clear();
								courseModule.item("module_nm", !"".equals(title) ? title : "시험");
								if(hasExamSettings) {
									courseModule.item("assign_score", assignScore);
									courseModule.item("retry_yn", allowRetake ? "Y" : "N");
									courseModule.item("retry_score", retakeScore);
									courseModule.item("retry_cnt", retakeCount);
									courseModule.item("result_yn", showResults ? "Y" : "N");
								}
								courseModule.item("status", 1);
								courseModule.update("course_id = " + mappedCourseId + " AND module = 'exam' AND module_id = " + examModuleId);
							} else {
								// 연결이 없으면 신규 연결 생성
								courseModule.clear();
								courseModule.item("course_id", mappedCourseId);
								courseModule.item("site_id", siteId);
								courseModule.item("module", "exam");
								courseModule.item("module_id", examModuleId);
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
								courseModule.insert();
							}

							if(content.optInt("examModuleId", 0) != examModuleId) {
								content.put("examModuleId", examModuleId);
								curriculumChanged = true;
							}
							if("".equals(content.optString("examId", ""))) {
								content.put("examId", String.valueOf(examModuleId));
								curriculumChanged = true;
							}
							newExamModuleIds.add(examModuleId);
						}
					}
					// 동영상(=video)
					else if("video".equalsIgnoreCase(contentType)) {
						int lessonId = content.optInt("lessonId", 0);
						String rawLessonId = content.optString("lessonId", "").trim();
						// 왜: lessonId가 문자열로 저장된 케이스(예: "123", "00123")도 숫자 ID로 통일해야 학생 재생이 깨지지 않습니다.
						if(lessonId <= 0 && !"".equals(rawLessonId)) {
							try { lessonId = Integer.parseInt(rawLessonId); } catch(Exception ignore) {}
						}
						if(lessonId <= 0) {
							// 왜: lessonId가 비어도 mediaKey(start_url)로 기존 레슨을 찾거나 생성해서 커리큘럼을 정상 상태로 되돌립니다.
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
									lessonId = lfind.i("id");
								} else {
									int createTotalTime = content.optInt("totalTime", 0);
									if(createTotalTime <= 0) createTotalTime = content.optInt("total_time", 0);
									int createCompleteTime = content.optInt("completeTime", 0);
									if(createCompleteTime <= 0) createCompleteTime = content.optInt("complete_time", 0);
									if(createTotalTime <= 0 && createCompleteTime > 0) createTotalTime = createCompleteTime;
									if(createCompleteTime <= 0 && createTotalTime > 0) createCompleteTime = createTotalTime;
									int createContentWidth = content.optInt("contentWidth", 0);
									if(createContentWidth <= 0) createContentWidth = content.optInt("content_width", 0);
									int createContentHeight = content.optInt("contentHeight", 0);
									if(createContentHeight <= 0) createContentHeight = content.optInt("content_height", 0);
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
									lesson.item("total_time", createTotalTime);
									lesson.item("complete_time", createCompleteTime);
									lesson.item("content_width", createContentWidth);
									lesson.item("content_height", createContentHeight);
									lesson.item("description", "");
									lesson.item("manager_id", userId);
									lesson.item("use_yn", "Y");
									lesson.item("sort", 0);
									lesson.item("reg_date", now);
									lesson.item("status", 1);

									if(lesson.insert()) {
										lessonId = newLessonId;
										m.log("haksa_curriculum", "[update] lesson created course_id=" + mappedCourseId + ", media_key=" + mediaKey + ", lesson_id=" + lessonId);
									} else {
										m.log("haksa_curriculum", "[update] lesson create failed course_id=" + mappedCourseId + ", media_key=" + mediaKey);
									}
								}
							}
						}
						if(lessonId <= 0) {
							m.log("haksa_curriculum", "[update] unresolved video lessonId course_id=" + mappedCourseId + ", session_no=" + sessionNo + ", raw_lesson_id=" + rawLessonId);
							continue;
						}
						if(content.optInt("lessonId", 0) != lessonId) {
							content.put("lessonId", lessonId);
							curriculumChanged = true;
							m.log("haksa_curriculum", "[update] lessonId normalized course_id=" + mappedCourseId + ", session_no=" + sessionNo + ", lesson_id=" + lessonId);
						}

						// 1) 인정시간(completeTime) → LM_LESSON.complete_time 동기화
						int completeTime = content.optInt("completeTime", 0);
						if(completeTime <= 0) completeTime = content.optInt("complete_time", 0);
						if(completeTime > 0) {
							DataSet linfo = lesson.find("id = " + lessonId + " AND site_id = " + siteId + " AND status != -1", "id, total_time, complete_time");
							if(linfo.next()) {
								int beforeComplete = linfo.i("complete_time");
								if(beforeComplete != completeTime) {
									lesson.clear();
									lesson.item("complete_time", completeTime);
									// 왜: 총 시간이 비어 있는 영상은 인정시간을 기본값으로 써서 진도율 계산이 0/100으로 튀지 않게 합니다.
									if(linfo.i("total_time") <= 0) lesson.item("total_time", completeTime);
									lesson.update("id = " + lessonId + " AND site_id = " + siteId + " AND status != -1");
									changedVideoLessonIds.add(Integer.valueOf(lessonId));
								}
							}
						}

						// 2) 차시 구성(LM_COURSE_LESSON) 동기화
						newVideoKeys.add(lessonId + ":" + sessionNo);

						int exist = courseLesson.findCount(
							"course_id = " + mappedCourseId + " AND lesson_id = " + lessonId + " AND chapter = " + sessionNo
						);
						if(exist > 0) {
							courseLesson.clear();
							courseLesson.item("start_date", sDate);
							courseLesson.item("end_date", eDate);
							courseLesson.item("start_time", sTime);
							courseLesson.item("end_time", eTime);
							courseLesson.item("status", 1);
							courseLesson.update(
								"course_id = " + mappedCourseId
								+ " AND lesson_id = " + lessonId
								+ " AND chapter = " + sessionNo
								+ " AND site_id = " + siteId
							);
						} else {
							courseLesson.clear();
							courseLesson.item("course_id", mappedCourseId);
							courseLesson.item("lesson_id", lessonId);
							courseLesson.item("section_id", 0);
							courseLesson.item("site_id", siteId);
							courseLesson.item("chapter", sessionNo);
							courseLesson.item("start_day", 0);
							courseLesson.item("period", 0);
							courseLesson.item("start_date", sDate);
							courseLesson.item("end_date", eDate);
							courseLesson.item("start_time", sTime);
							courseLesson.item("end_time", eTime);
							courseLesson.item("lesson_hour", 1.00);
							courseLesson.item("progress_yn", "Y");
							courseLesson.item("status", 1);
							courseLesson.insert();
						}
					}
				}
			}
		}
	} catch(Exception ignore) {}
}

// 4) 삭제 동기화: 이전 목차에는 있었는데 새 목차에는 없으면 "안 보이게" 처리합니다.
if(mappedCourseId > 0) {
	try {
		for(Integer hid : oldHomeworkIds) {
			if(newHomeworkIds.contains(hid)) continue;
			courseModule.clear();
			courseModule.item("status", 0);
			courseModule.update("course_id = " + mappedCourseId + " AND module = 'homework' AND module_id = " + hid);
		}
		for(Integer eid : oldExamModuleIds) {
			if(newExamModuleIds.contains(eid)) continue;
			courseModule.clear();
			courseModule.item("status", 0);
			courseModule.update("course_id = " + mappedCourseId + " AND module = 'exam' AND module_id = " + eid);
		}
		for(Integer lid : oldLibraryIds) {
			if(newLibraryIds.contains(lid)) continue;
			// 왜: LM_COURSE_LIBRARY는 status 컬럼이 없는 환경이 있어, 연결 행 자체를 삭제합니다.
			courseLibrary.delete("course_id = " + mappedCourseId + " AND library_id = " + lid);
		}
		// 왜: 동영상은 진도 기록이 남아 있을 수 있어 완전 삭제 대신 status=0(숨김) 처리합니다.
		for(String key : oldVideoKeys) {
			if(newVideoKeys.contains(key)) continue;
			try {
				String[] parts = key.split(":");
				if(parts.length < 2) continue;
				int lid = Integer.parseInt(parts[0]);
				int chap = Integer.parseInt(parts[1]);
				courseLesson.clear();
				courseLesson.item("status", 0);
				courseLesson.update(
					"course_id = " + mappedCourseId
					+ " AND lesson_id = " + lid
					+ " AND chapter = " + chap
					+ " AND site_id = " + siteId
				);
			} catch(Exception ignore2) {}
		}
	} catch(Exception ignore) {}
}

// 5) 인정시간 변경 시, 기존 진도(특히 complete_time=0으로 100% 처리된 케이스)를 재계산해 화면/출석이 꼬이지 않게 보완합니다.
if(mappedCourseId > 0 && changedVideoLessonIds.size() > 0) {
	try {
		for(Integer lidObj : changedVideoLessonIds) {
			int lessonId = lidObj.intValue();
			DataSet linfo = lesson.find("id = " + lessonId + " AND site_id = " + siteId + " AND status = 1", "total_time, complete_time");
			if(!linfo.next()) continue;

			int totalSec = linfo.i("total_time") * 60;
			int completeSec = linfo.i("complete_time") * 60;

			// 왜: 동영상 진도율은 LEAST(study_time, last_time)을 기준으로 계산합니다(기존 CourseProgressDao 로직과 동일한 철학).
			String ratioExpr =
				(completeSec <= 0)
				? "100.0"
				: ("(CASE"
					+ " WHEN " + totalSec + " > 0 AND LEAST(IFNULL(cp.study_time,0), IFNULL(cp.last_time,0)) < " + completeSec + " THEN LEAST(100.0, (LEAST(IFNULL(cp.study_time,0), IFNULL(cp.last_time,0)) / " + (double)totalSec + ") * 100.0)"
					+ " ELSE 100.0 END)");

			String completeExpr =
				(completeSec <= 0)
				? "'Y'"
				: ("(CASE"
					+ " WHEN LEAST(IFNULL(cp.study_time,0), IFNULL(cp.last_time,0)) >= " + completeSec + " THEN 'Y'"
					+ " WHEN " + totalSec + " > 0 AND LEAST(IFNULL(cp.study_time,0), IFNULL(cp.last_time,0)) >= " + totalSec + " THEN 'Y'"
					+ " ELSE 'N' END)");

			courseProgress.execute(
				"UPDATE " + courseProgress.table + " cp "
				+ " INNER JOIN LM_COURSE_USER cu ON cu.id = cp.course_user_id AND cu.course_id = " + mappedCourseId + " AND cu.site_id = " + siteId + " AND cu.status NOT IN (-1, -4) "
				+ " SET cp.ratio = " + ratioExpr + ", cp.complete_yn = " + completeExpr
				+ " , cp.complete_date = (CASE WHEN " + completeExpr + " = 'Y' THEN (CASE WHEN IFNULL(cp.complete_date,'') = '' THEN '" + now + "' ELSE cp.complete_date END) ELSE '' END) "
				+ " WHERE cp.site_id = " + siteId + " AND cp.status = 1 AND cp.lesson_id = " + lessonId
			);
		}
	} catch(Exception ignore) {}
}

int count = setting.findCount(
	"site_id = " + siteId
	+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
	+ " AND status != -1"
	, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
);

// 왜: 위 연동 과정에서 homeworkId/examModuleId/libraryId가 채워질 수 있어, 최종 JSON을 다시 저장합니다.
curriculumJson = weeks.toString();
setting.item("curriculum_json", curriculumJson);
setting.item("mod_date", now);

if(count > 0) {
	String safeCourseCode = m.replace(courseCode, "'", "''");
	String safeOpenYear = m.replace(openYear, "'", "''");
	String safeOpenTerm = m.replace(openTerm, "'", "''");
	String safeBunbanCode = m.replace(bunbanCode, "'", "''");
	String safeGroupCode = m.replace(groupCode, "'", "''");

	if(!setting.update(
		"site_id = " + siteId
		+ " AND course_code = '" + safeCourseCode + "'"
		+ " AND open_year = '" + safeOpenYear + "'"
		+ " AND open_term = '" + safeOpenTerm + "'"
		+ " AND bunban_code = '" + safeBunbanCode + "'"
		+ " AND group_code = '" + safeGroupCode + "'"
		+ " AND status != -1"
	)) {
		result.put("rst_code", "2000");
		result.put("rst_message", "저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
} else {
	setting.item("site_id", siteId);
	setting.item("course_code", courseCode);
	setting.item("open_year", openYear);
	setting.item("open_term", openTerm);
	setting.item("bunban_code", bunbanCode);
	setting.item("group_code", groupCode);
	setting.item("reg_date", m.time("yyyyMMddHHmmss"));
	setting.item("status", 1);

	if(!setting.insert()) {
		result.put("rst_code", "2000");
		result.put("rst_message", "저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.print();

%>

