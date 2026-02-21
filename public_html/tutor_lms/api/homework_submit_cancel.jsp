<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 제출된 과제를 취소할 수 있어야 합니다.
//- 제출/추가과제/첨부파일을 정리하고 성적을 즉시 재계산합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
int courseUserId = m.ri("course_user_id");

if(0 == courseId || 0 == homeworkId || 0 == courseUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();
HomeworkTaskDao homeworkTask = new HomeworkTaskDao();
ClFileDao file = new ClFileDao();
HomeworkSimilarityResultDao homeworkSimilarity = new HomeworkSimilarityResultDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 제출을 취소할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet cuinfo = courseUser.find("id = " + courseUserId + " AND course_id = " + courseId + " AND site_id = " + siteId + " AND status IN (1,3)");
if(!cuinfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 수강 정보가 없습니다.");
	result.print();
	return;
}

//왜: 제출 기록이 없으면 취소할 대상이 없으므로 안내만 합니다.
DataSet huinfo = homeworkUser.find("homework_id = " + homeworkId + " AND course_user_id = " + courseUserId + " AND status = 1");
if(!huinfo.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "해당 제출 정보가 없습니다.");
	result.print();
	return;
}

//삭제-제출
if(!homeworkUser.delete("homework_id = " + homeworkId + " AND course_user_id = " + courseUserId)) {
	result.put("rst_code", "2000");
	result.put("rst_message", "제출 취소 중 오류가 발생했습니다.");
	result.print();
	return;
}

//왜: 추가과제/첨부파일이 남으면 재제출 때 혼란이 생기므로 함께 정리합니다.
homeworkTask.delete("homework_id = " + homeworkId + " AND course_user_id = " + courseUserId);
file.execute("DELETE FROM " + file.table + " WHERE module = 'homework_" + homeworkId + "' AND module_id = " + courseUserId);
file.execute("DELETE FROM " + file.table + " WHERE module = 'homework_feedback_" + homeworkId + "' AND module_id = " + courseUserId);

// 왜: 제출 취소되면 해당 학생이 포함된 유사도 쌍을 즉시 정리해야 목록이 최신 상태를 유지합니다.
Hashtable<String, Object> similarityOut = homeworkSimilarity.runIncrementalAnalysis(
	siteId,
	courseId,
	homeworkId,
	courseUserId,
	userId,
	70.0,
	"AUTO_CANCEL"
);
if(!"Y".equals(similarityOut.get("success"))) {
	m.log(
		"tutor_homework_similarity",
		"auto_cancel_failed course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", run_id=" + similarityOut.get("run_id")
		+ ", pair_total=" + similarityOut.get("pair_total") + ", pair_saved=" + similarityOut.get("pair_saved")
		+ ", request_user_id=" + userId + ", site_id=" + siteId + ", message=" + similarityOut.get("message")
	);
}

//성적 반영
courseUser.setCourseUserScore(courseUserId, "homework");

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", courseUserId);
result.print();

%>
