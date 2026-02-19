<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 학사 연동 커리큘럼에서 특정 영상 항목을 삭제해 교수자가 직접 목차를 정리할 수 있어야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

String courseCode = m.rs("course_code");
String openYear = m.rs("open_year");
String openTerm = m.rs("open_term");
String bunbanCode = m.rs("bunban_code");
String groupCode = m.rs("group_code");
String contentId = m.rs("content_id").trim();

if("".equals(courseCode) || "".equals(openYear) || "".equals(openTerm) || "".equals(bunbanCode) || "".equals(groupCode) || "".equals(contentId)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "학사 과목 키와 content_id가 필요합니다.");
	result.print();
	return;
}

PolyCourseSettingDao setting = new PolyCourseSettingDao();
DataSet info = setting.find(
	"site_id = " + siteId
	+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
	+ " AND status != -1"
	, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode }
);
if(!info.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "학사 과목 설정이 없습니다.");
	result.print();
	return;
}

String curriculumJson = info.s("curriculum_json");
if("".equals(curriculumJson)) curriculumJson = "[]";

JSONArray weeks = new JSONArray();
try {
	JSONArray rawArr = new JSONArray(curriculumJson);
	JSONObject first = rawArr.length() > 0 ? rawArr.optJSONObject(0) : null;
	boolean isV2 = first != null && first.has("sessions");
	boolean isV1 = !isV2 && first != null && first.has("type");

	if(isV2) {
		weeks = rawArr;
	} else if(isV1) {
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
			sessionObj.put("sessionNo", 1);
			sessionObj.put("contents", list);

			JSONObject weekObj = new JSONObject();
			weekObj.put("weekNumber", w);
			weekObj.put("title", w + "주차");
			weekObj.put("sessions", new JSONArray().put(sessionObj));
			weeks.put(weekObj);
		}
	}
} catch(Exception e) {
	result.put("rst_code", "1002");
	result.put("rst_message", "강의목차 JSON 형식이 올바르지 않습니다.");
	result.print();
	return;
}

boolean removed = false;
int removedLessonId = 0;

for(int i = 0; i < weeks.length() && !removed; i++) {
	JSONObject w = weeks.optJSONObject(i);
	if(w == null) continue;
	int weekNumber = w.optInt("weekNumber", i + 1);
	if(weekNumber <= 0) weekNumber = i + 1;

	JSONArray sessions = w.optJSONArray("sessions");
	if(sessions == null) continue;

	for(int s = 0; s < sessions.length() && !removed; s++) {
		JSONObject sessionObj = sessions.optJSONObject(s);
		if(sessionObj == null) continue;
		int sessionNo = sessionObj.optInt("sessionNo", 0);
		if(sessionNo <= 0) sessionNo = s + 1;

		JSONArray contents = sessionObj.optJSONArray("contents");
		if(contents == null) continue;

		JSONArray nextContents = new JSONArray();
		for(int c = 0; c < contents.length(); c++) {
			JSONObject content = contents.optJSONObject(c);
			if(content == null) continue;

			String itemContentId = content.optString("id", "").trim();
			if("".equals(itemContentId)) itemContentId = "video_" + weekNumber + "_" + sessionNo + "_" + (c + 1);

			if(!removed && contentId.equals(itemContentId) && "video".equalsIgnoreCase(content.optString("type", ""))) {
				removed = true;
				removedLessonId = content.optInt("lessonId", 0);
				if(removedLessonId <= 0) {
					String rawLessonId = content.optString("lessonId", "").trim();
					if(!"".equals(rawLessonId)) {
						try { removedLessonId = Integer.parseInt(rawLessonId); } catch(Exception ignore) {}
					}
				}
				continue;
			}

			nextContents.put(content);
		}

		if(removed) {
			sessionObj.put("contents", nextContents);
			break;
		}
	}
}

if(!removed) {
	result.put("rst_code", "4041");
	result.put("rst_message", "삭제할 영상 항목을 찾을 수 없습니다.");
	result.print();
	return;
}

String now = m.time("yyyyMMddHHmmss");
String safeCourseCode = m.replace(courseCode, "'", "''");
String safeOpenYear = m.replace(openYear, "'", "''");
String safeOpenTerm = m.replace(openTerm, "'", "''");
String safeBunbanCode = m.replace(bunbanCode, "'", "''");
String safeGroupCode = m.replace(groupCode, "'", "''");

setting.item("curriculum_json", weeks.toString());
setting.item("mod_date", now);
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
	result.put("rst_message", "학사 영상 삭제 저장 중 오류가 발생했습니다.");
	result.print();
	return;
}

if(removedLessonId > 0) {
	int mappedCourseId = 0;
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
		if(mappedCourse.next()) mappedCourseId = mappedCourse.i("id");
	} catch(Exception ignore) {}

	if(mappedCourseId > 0) {
		CourseLessonDao courseLesson = new CourseLessonDao();
		courseLesson.item("status", 0);
		courseLesson.update(
			"course_id = " + mappedCourseId
			+ " AND lesson_id = " + removedLessonId
			+ " AND site_id = " + siteId
			+ " AND status != -1"
		);
	}
}

m.log(
	"haksa_video_delete",
	"delete_ok course_code=" + courseCode
	+ ", open_year=" + openYear
	+ ", open_term=" + openTerm
	+ ", bunban_code=" + bunbanCode
	+ ", group_code=" + groupCode
	+ ", content_id=" + contentId
	+ ", lesson_id=" + removedLessonId
	+ ", user_id=" + userId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", removedLessonId);
result.put("rst_content_id", contentId);
result.print();

%>
