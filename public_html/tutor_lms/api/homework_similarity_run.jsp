<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 과제 제출물 일치율 분석을 수동 실행해, 저장된 결과를 최신 상태로 갱신할 수 있어야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
String thresholdRaw = m.rs("threshold_score");
double thresholdScore = 70.0;
if(!"".equals(thresholdRaw)) thresholdScore = m.parseDouble(thresholdRaw);

if(0 == courseId || 0 == homeworkId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id가 필요합니다.");
	result.print();
	return;
}
if(thresholdScore < 0 || 100 < thresholdScore) {
	result.put("rst_code", "1100");
	result.put("rst_message", "threshold_score는 0~100 사이여야 합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();
HomeworkSimilarityResultDao homeworkSimilarity = new HomeworkSimilarityResultDao();

m.log(
	"tutor_homework_similarity",
	"run_start course_id=" + courseId + ", homework_id=" + homeworkId + ", threshold=" + thresholdScore + ", request_user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin
);

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		m.log("tutor_homework_similarity", "run_permission_denied course_id=" + courseId + ", request_user_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 일치율 분석을 실행할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet minfo = courseModule.query(
	" SELECT a.module_id "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + homework.table + " h ON a.module_id = h.id AND h.site_id = " + siteId + " AND h.status != -1 "
	+ " WHERE a.course_id = " + courseId + " AND a.module = 'homework' AND a.module_id = " + homeworkId + " AND a.status = 1 "
);
if(!minfo.next()) {
	m.log("tutor_homework_similarity", "run_not_linked course_id=" + courseId + ", homework_id=" + homeworkId + ", site_id=" + siteId + ", request_user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

Hashtable<String, Object> runOut = homeworkSimilarity.runFullAnalysis(
	siteId,
	courseId,
	homeworkId,
	userId,
	thresholdScore,
	"MANUAL"
);

if(!"Y".equals(runOut.get("success"))) {
	m.log(
		"tutor_homework_similarity",
		"run_failed course_id=" + courseId + ", homework_id=" + homeworkId + ", run_id=" + runOut.get("run_id")
		+ ", pair_total=" + runOut.get("pair_total") + ", pair_saved=" + runOut.get("pair_saved")
		+ ", request_user_id=" + userId + ", site_id=" + siteId + ", message=" + runOut.get("message")
	);
	result.put("rst_code", "2000");
	result.put("rst_message", String.valueOf(runOut.get("message")));
	result.put("rst_data", runOut);
	result.print();
	return;
}

m.log(
	"tutor_homework_similarity",
	"run_success course_id=" + courseId + ", homework_id=" + homeworkId + ", run_id=" + runOut.get("run_id")
	+ ", pair_total=" + runOut.get("pair_total") + ", pair_saved=" + runOut.get("pair_saved")
	+ ", threshold=" + thresholdScore + ", request_user_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", runOut);
result.print();

%>
