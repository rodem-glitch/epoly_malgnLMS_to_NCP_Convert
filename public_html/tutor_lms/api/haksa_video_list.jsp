<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 학사 연동 커리큘럼의 "동영상 항목"만 분리 조회해 교수자가 검토/정리할 수 있어야 합니다.

String courseCode = m.rs("course_code");
String openYear = m.rs("open_year");
String openTerm = m.rs("open_term");
String bunbanCode = m.rs("bunban_code");
String groupCode = m.rs("group_code");

if("".equals(courseCode) || "".equals(openYear) || "".equals(openTerm) || "".equals(bunbanCode) || "".equals(groupCode)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "학사 과목 키(course_code/open_year/open_term/bunban_code/group_code)가 필요합니다.");
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

String curriculumJson = "";
if(info.next()) curriculumJson = info.s("curriculum_json");

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
		// 왜: 구형(v1)도 영상 검토 화면에서 바로 보이게 주차→차시 구조로 변환합니다.
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

DataSet list = new DataSet();
int seq = 0;

for(int i = 0; i < weeks.length(); i++) {
	JSONObject w = weeks.optJSONObject(i);
	if(w == null) continue;

	int weekNumber = w.optInt("weekNumber", i + 1);
	if(weekNumber <= 0) weekNumber = i + 1;
	String weekTitle = w.optString("title", weekNumber + "주차");

	JSONArray sessions = w.optJSONArray("sessions");
	if(sessions == null) continue;

	for(int s = 0; s < sessions.length(); s++) {
		JSONObject sessionObj = sessions.optJSONObject(s);
		if(sessionObj == null) continue;
		seq++;

		int sessionNo = sessionObj.optInt("sessionNo", 0);
		if(sessionNo <= 0) sessionNo = s + 1;
		int chapterNo = sessionObj.optInt("chapterNo", 0);
		if(chapterNo <= 0) chapterNo = seq;

		String sessionId = sessionObj.optString("sessionId", "");
		String sessionName = sessionObj.optString("sessionName", (s + 1) + "차시");
		String sDate = sessionObj.optString("startDate", "");
		String eDate = sessionObj.optString("endDate", "");
		String sTime = sessionObj.optString("startTime", "");
		String eTime = sessionObj.optString("endTime", "");

		JSONArray contents = sessionObj.optJSONArray("contents");
		if(contents == null) continue;

		for(int c = 0; c < contents.length(); c++) {
			JSONObject content = contents.optJSONObject(c);
			if(content == null) continue;
			if(!"video".equalsIgnoreCase(content.optString("type", ""))) continue;

			String contentId = content.optString("id", "").trim();
			if("".equals(contentId)) contentId = "video_" + weekNumber + "_" + sessionNo + "_" + (c + 1);

			int lessonId = content.optInt("lessonId", 0);
			if(lessonId <= 0) {
				String rawLessonId = content.optString("lessonId", "").trim();
				if(!"".equals(rawLessonId)) {
					try { lessonId = Integer.parseInt(rawLessonId); } catch(Exception ignore) {}
				}
			}

			int completeTime = content.optInt("completeTime", 0);
			if(completeTime <= 0) completeTime = content.optInt("complete_time", 0);

			list.addRow();
			list.put("content_id", contentId);
			list.put("type", "video");
			list.put("title", content.optString("title", ""));
			list.put("lesson_id", lessonId);
			list.put("media_key", content.optString("mediaKey", ""));
			list.put("complete_time", completeTime);
			list.put("week_number", weekNumber);
			list.put("week_title", weekTitle);
			list.put("session_id", sessionId);
			list.put("session_name", sessionName);
			list.put("session_no", sessionNo);
			list.put("chapter_no", chapterNo);
			list.put("start_date", sDate);
			list.put("start_time", sTime);
			list.put("end_date", eDate);
			list.put("end_time", eTime);
		}
	}
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
