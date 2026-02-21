<%@ page pageEncoding="utf-8" %><%@ page import="java.util.*" %><%@ include file="init.jsp" %><%!
// 왜 필요한가:
// - 교수자가 "여러 차시 × 여러 학생" 출결을 한 번에 처리해야 수업 운영 시간이 줄어듭니다.
// - 특히 같은 주차 안에서 차시별(Y/N) 상태를 다르게 저장해야 "부분 출석/결석"을 빠르게 반영할 수 있습니다.

private String[] pickValues(HttpServletRequest req, String[] keys) {
	if(req == null || keys == null) return null;
	for(int i = 0; i < keys.length; i++) {
		String key = keys[i];
		if(key == null || "".equals(key)) continue;
		String[] arr = req.getParameterValues(key);
		if(arr != null && arr.length > 0) return arr;

		String one = req.getParameter(key);
		if(one != null && !"".equals(one.trim())) return new String[] { one };
	}
	return null;
}

private ArrayList<String> toTokens(String[] values) {
	ArrayList<String> tokens = new ArrayList<String>();
	if(values == null) return tokens;

	for(int i = 0; i < values.length; i++) {
		String v = values[i];
		if(v == null) continue;
		String[] split = v.split(",");
		for(int j = 0; j < split.length; j++) {
			String t = split[j] != null ? split[j].trim() : "";
			if(!"".equals(t)) tokens.add(t);
		}
	}
	return tokens;
}

private Integer toPositiveInt(String token) {
	if(token == null) return null;
	try {
		int n = Integer.parseInt(token.trim());
		return n > 0 ? Integer.valueOf(n) : null;
	} catch(Exception e) {
		return null;
	}
}

private String normalizeAttendStatus(String raw) {
	if(raw == null) return "";
	String v = raw.trim().toUpperCase();
	return ("Y".equals(v) || "N".equals(v)) ? v : "";
}

private String joinInts(List<Integer> values) {
	if(values == null || values.size() == 0) return "0";
	StringBuilder sb = new StringBuilder();
	for(int i = 0; i < values.size(); i++) {
		if(i > 0) sb.append(",");
		sb.append(values.get(i).intValue());
	}
	return sb.toString();
}
%><%

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

ArrayList<String> lessonTokens = toTokens(pickValues(request, new String[] { "lesson_ids[]", "lesson_ids", "lid[]", "lid" }));
ArrayList<String> statusTokens = toTokens(pickValues(request, new String[] { "attend_statuses[]", "attend_statuses", "att_status[]", "att_status" }));
ArrayList<String> courseUserTokens = toTokens(pickValues(request, new String[] { "course_user_ids[]", "course_user_ids", "cu_idx[]", "cu_idx" }));

if(lessonTokens.size() == 0 || statusTokens.size() == 0 || courseUserTokens.size() == 0) {
	result.put("rst_code", "1002");
	result.put("rst_message", "lesson_ids, attend_statuses, course_user_ids가 필요합니다.");
	result.print();
	return;
}
if(lessonTokens.size() != statusTokens.size()) {
	result.put("rst_code", "1003");
	result.put("rst_message", "lesson_ids와 attend_statuses 개수가 일치해야 합니다.");
	result.print();
	return;
}

ArrayList<Integer> lessonIds = new ArrayList<Integer>();
HashSet<Integer> lessonIdSet = new HashSet<Integer>();
for(int i = 0; i < lessonTokens.size(); i++) {
	Integer id = toPositiveInt(lessonTokens.get(i));
	if(id == null) {
		result.put("rst_code", "1004");
		result.put("rst_message", "유효하지 않은 lesson_id가 있습니다.");
		result.print();
		return;
	}
	if(lessonIdSet.contains(id)) {
		result.put("rst_code", "1005");
		result.put("rst_message", "lesson_id가 중복되었습니다.");
		result.print();
		return;
	}
	lessonIdSet.add(id);
	lessonIds.add(id);
}

ArrayList<String> statuses = new ArrayList<String>();
for(int i = 0; i < statusTokens.size(); i++) {
	String st = normalizeAttendStatus(statusTokens.get(i));
	if("".equals(st)) {
		result.put("rst_code", "1006");
		result.put("rst_message", "attend_statuses는 Y/N만 허용됩니다.");
		result.print();
		return;
	}
	statuses.add(st);
}

LinkedHashSet<Integer> courseUserIdSet = new LinkedHashSet<Integer>();
for(int i = 0; i < courseUserTokens.size(); i++) {
	Integer id = toPositiveInt(courseUserTokens.get(i));
	if(id == null) {
		result.put("rst_code", "1007");
		result.put("rst_message", "유효하지 않은 course_user_id가 있습니다.");
		result.print();
		return;
	}
	courseUserIdSet.add(id);
}

ArrayList<Integer> courseUserIds = new ArrayList<Integer>(courseUserIdSet);
if(courseUserIds.size() == 0) {
	result.put("rst_code", "1008");
	result.put("rst_message", "처리할 수강생이 없습니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseLessonDao courseLesson = new CourseLessonDao();
CourseUserDao courseUser = new CourseUserDao();
CourseProgressDao courseProgress = new CourseProgressDao(siteId);
LessonDao lesson = new LessonDao();

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type IN ('major','minor') AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 출결을 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

HashMap<Integer, Integer> lessonChapterMap = new HashMap<Integer, Integer>();
HashMap<Integer, String> lessonTypeMap = new HashMap<Integer, String>();

for(int i = 0; i < lessonIds.size(); i++) {
	int lessonId = lessonIds.get(i).intValue();
	DataSet linfo = courseLesson.query(
		" SELECT cl.course_id, cl.lesson_id, cl.chapter, l.lesson_type "
		+ " FROM " + courseLesson.table + " cl "
		+ " INNER JOIN " + lesson.table + " l ON l.id = cl.lesson_id AND l.status = 1 "
		+ " WHERE cl.course_id = " + courseId + " AND cl.lesson_id = " + lessonId + " AND cl.site_id = " + siteId + " AND cl.status = 1 "
	);
	if(!linfo.next()) {
		result.put("rst_code", "4041");
		result.put("rst_message", "유효하지 않은 차시가 포함되어 있습니다. lesson_id=" + lessonId);
		result.print();
		return;
	}
	lessonChapterMap.put(Integer.valueOf(lessonId), Integer.valueOf(linfo.i("chapter")));
	lessonTypeMap.put(Integer.valueOf(lessonId), linfo.s("lesson_type"));
}

String courseUserIn = joinInts(courseUserIds);
DataSet students = courseUser.query(
	" SELECT id course_user_id, user_id "
	+ " FROM " + courseUser.table
	+ " WHERE course_id = " + courseId + " AND site_id = " + siteId + " AND status IN (1,3) "
	+ " AND id IN (" + courseUserIn + ") "
	+ " ORDER BY id ASC "
);
if(students.size() != courseUserIds.size()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "유효하지 않은 수강생이 포함되어 있습니다.");
	result.put("rst_valid_count", students.size());
	result.put("rst_request_count", courseUserIds.size());
	result.print();
	return;
}

m.log(
	"attendance_batch_update",
	"batch_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", lesson_count=" + lessonIds.size()
	+ ", student_count=" + students.size()
);

int requestedCount = lessonIds.size() * students.size();
int successCount = 0;
int lessonSuccessCount = 0;

for(int i = 0; i < lessonIds.size(); i++) {
	int lessonId = lessonIds.get(i).intValue();
	String attendStatus = statuses.get(i);

	DataSet llist = new DataSet();
	llist.addRow();
	llist.put("course_id", courseId);
	llist.put("lesson_id", lessonId);
	llist.put("chapter", lessonChapterMap.get(Integer.valueOf(lessonId)).intValue());
	llist.put("lesson_type", lessonTypeMap.get(Integer.valueOf(lessonId)));

	DataSet ulist = new DataSet();
	students.first();
	while(students.next()) {
		ulist.addRow();
		ulist.put("course_user_id", students.i("course_user_id"));
		ulist.put("user_id", students.i("user_id"));
		ulist.put("attend_status", attendStatus);
	}

	int handled = courseProgress.attendUser(llist, ulist, userId);
	if(handled < 0) {
		result.put("rst_code", "5001");
		result.put("rst_message", "출결 처리 중 오류가 발생했습니다. lesson_id=" + lessonId);
		result.put("rst_success_count", successCount);
		result.print();
		return;
	}

	successCount += handled;
	if(handled == students.size()) lessonSuccessCount++;
}

m.log(
	"attendance_batch_update",
	"batch_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", requested_count=" + requestedCount
	+ ", success_count=" + successCount
	+ ", lesson_success_count=" + lessonSuccessCount
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_lesson_count", lessonIds.size());
result.put("rst_student_count", students.size());
result.put("rst_requested_count", requestedCount);
result.put("rst_success_count", successCount);
result.put("rst_data", successCount);
result.print();

%>
