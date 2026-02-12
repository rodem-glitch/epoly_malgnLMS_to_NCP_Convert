<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%!
private String onlyDigits(String value) {
	if(value == null) return "";
	return value.replaceAll("[^0-9]", "");
}

private String buildDateTime(String date, String time, boolean isStart) {
	String ymd = onlyDigits(date);
	if(ymd.length() >= 8) ymd = ymd.substring(0, 8);
	if(ymd.length() != 8) return "";

	String hm = onlyDigits(time);
	if(hm.length() >= 4) hm = hm.substring(0, 4);
	if(hm.length() != 4) hm = isStart ? "0000" : "2359";

	return ymd + hm + (isStart ? "00" : "59");
}
%><%

//왜 필요한가:
//- 학사 커리큘럼 화면에서 "보기"로 바로 진입할 때, 서버에서도 차시 수강기간을 한 번 더 확인합니다.
//- 단, 과제는 과제 자체(과제 열람/제출 기간, 권한)로 제어되는 경우가 많아
//  차시 수강기간 때문에 "보기"가 막히면 사용성이 크게 떨어집니다.
//  그래서 시험은 차시 기간 밖이면 차단하되, 과제는 차시 기간 밖이어도 "보기"는 허용합니다.

String type = m.rs("type");
int moduleId = m.ri("module_id");
String sessionId = m.rs("session_id");
// 왜: classroom/init.jsp에서도 haksaCuid 변수를 쓰기 때문에, 여기서는 다른 이름을 사용합니다.
String haksaCuidParam = m.rs("haksa_cuid");

if("".equals(type) || moduleId <= 0 || "".equals(sessionId)) {
	m.jsErrClose("콘텐츠 정보가 없습니다.");
	return;
}

// 학사 키 보정
String hkCourseCode = cuinfo.s("haksa_course_code");
String hkOpenYear = cuinfo.s("haksa_open_year");
String hkOpenTerm = cuinfo.s("haksa_open_term");
String hkBunbanCode = cuinfo.s("haksa_bunban_code");
String hkGroupCode = cuinfo.s("haksa_group_code");

if("".equals(hkCourseCode) || "".equals(hkOpenYear) || "".equals(hkOpenTerm) || "".equals(hkBunbanCode) || "".equals(hkGroupCode)) {
	if(!"".equals(haksaCuidParam)) {
		String[] parts = haksaCuidParam.split("_");
		if(parts.length >= 5) {
			hkCourseCode = parts[0];
			hkOpenYear = parts[1];
			hkOpenTerm = parts[2];
			hkBunbanCode = parts[3];
			hkGroupCode = parts[4];
		}
	}
}

if("".equals(hkCourseCode) || "".equals(hkOpenYear) || "".equals(hkOpenTerm) || "".equals(hkBunbanCode) || "".equals(hkGroupCode)) {
	m.jsErrClose("학사 과목 키가 없어 콘텐츠를 열 수 없습니다.");
	return;
}

PolyCourseSettingDao setting = new PolyCourseSettingDao();
DataSet info = setting.find(
	"site_id = " + siteId
	+ " AND course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ?"
	+ " AND status != -1"
	, new Object[] { hkCourseCode, hkOpenYear, hkOpenTerm, hkBunbanCode, hkGroupCode }
);

if(!info.next()) {
	m.jsErrClose("강의목차를 찾지 못했습니다.");
	return;
}

String curriculumJson = info.s("curriculum_json");
if("".equals(curriculumJson)) {
	m.jsErrClose("강의목차가 비어 있습니다.");
	return;
}

JSONArray weeks = new JSONArray(curriculumJson);
JSONObject targetSession = null;
boolean moduleMatched = false;
for(int i = 0; i < weeks.length(); i++) {
	JSONObject w = weeks.optJSONObject(i);
	if(w == null) continue;
	JSONArray sessions = w.optJSONArray("sessions");
	if(sessions == null) continue;
	for(int s = 0; s < sessions.length(); s++) {
		JSONObject sessionObj = sessions.optJSONObject(s);
		if(sessionObj == null) continue;
		String sid = sessionObj.optString("sessionId", "");
		if(sessionId.equals(sid)) {
			targetSession = sessionObj;
			JSONArray contents = sessionObj.optJSONArray("contents");
			if(contents != null) {
				for(int c = 0; c < contents.length(); c++) {
					JSONObject content = contents.optJSONObject(c);
					if(content == null) continue;
					String ctype = content.optString("type", "");
					if("exam".equals(type) && "exam".equalsIgnoreCase(ctype)) {
						int eid = content.optInt("examModuleId", 0);
						if(eid <= 0) {
							try { eid = Integer.parseInt(content.optString("examId", "0")); } catch(Exception ignore) {}
						}
						if(eid == moduleId) { moduleMatched = true; break; }
					} else if("assignment".equals(type) && "assignment".equalsIgnoreCase(ctype)) {
						int hid = content.optInt("homeworkId", 0);
						if(hid == moduleId) { moduleMatched = true; break; }
					}
				}
			}
			break;
		}
	}
	if(targetSession != null) break;
}

if(targetSession == null) {
	m.jsErrClose("차시 정보를 찾지 못했습니다.");
	return;
}
if(!moduleMatched) {
	m.jsErrClose("해당 차시에 등록된 콘텐츠가 아닙니다.");
	return;
}

String startDateTime = buildDateTime(targetSession.optString("startDate", ""), targetSession.optString("startTime", ""), true);
String endDateTime = buildDateTime(targetSession.optString("endDate", ""), targetSession.optString("endTime", ""), false);
// 왜: classroom/init.jsp에서도 now 변수를 선언하므로, include 충돌을 피하려고 이름을 분리합니다.
String nowDt = m.time("yyyyMMddHHmmss");

boolean hasPeriod = !"".equals(startDateTime) && !"".equals(endDateTime);
boolean inPeriod = true;
if(hasPeriod) inPeriod = !(nowDt.compareTo(startDateTime) < 0 || nowDt.compareTo(endDateTime) > 0);

if(hasPeriod && !inPeriod) {
	// 왜: 과제는 차시 수강기간과 별도로 과제 자체 기간/권한으로 관리되는 경우가 있어,
	//     차시 기간 체크로 "보기"가 막히지 않게 예외로 둡니다(시험은 기존대로 차단).
	if("assignment".equals(type)) {
		m.log("haksa_module", "[bypass] out_of_period type=assignment module_id=" + moduleId
			+ ", session_id=" + sessionId
			+ ", now=" + nowDt
			+ ", start=" + startDateTime
			+ ", end=" + endDateTime);
	} else {
		m.jsErrClose("차시 수강기간이 아닙니다.");
		return;
	}
}

String targetUrl = "";
if("exam".equals(type)) {
	targetUrl = "../classroom/exam_view.jsp?id=" + moduleId;
} else if("assignment".equals(type)) {
	targetUrl = "../classroom/homework_view.jsp?id=" + moduleId;
} else {
	m.jsErrClose("지원하지 않는 콘텐츠입니다.");
	return;
}

String qs = "";
if(cuid > 0 && !"".equals(haksaCuidParam)) qs = "cuid=" + cuid + "&haksa_cuid=" + haksaCuidParam;
else if(cuid > 0) qs = "cuid=" + cuid;
else if(!"".equals(haksaCuidParam)) qs = "haksa_cuid=" + haksaCuidParam;

if(!"".equals(qs)) targetUrl += "&" + qs;
m.redirect(targetUrl);

%>
