<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 의심쌍 목록에서 특정 쌍을 눌렀을 때, 양쪽 제출 내용/첨부를 비교 확인할 상세 데이터가 필요합니다.

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
int leftCourseUserId = m.ri("left_course_user_id");
int rightCourseUserId = m.ri("right_course_user_id");

if(0 == courseId || 0 == homeworkId || 0 == leftCourseUserId || 0 == rightCourseUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id, left_course_user_id, right_course_user_id가 필요합니다.");
	result.print();
	return;
}

int normalizedLeft = Math.min(leftCourseUserId, rightCourseUserId);
int normalizedRight = Math.max(leftCourseUserId, rightCourseUserId);

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();
HomeworkSimilarityResultDao similarityResult = new HomeworkSimilarityResultDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();
UserDao user = new UserDao();
ClFileDao file = new ClFileDao();

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 일치율 상세를 조회할 권한이 없습니다.");
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
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet pair = similarityResult.find(
	"site_id = " + siteId
	+ " AND course_id = " + courseId
	+ " AND homework_id = " + homeworkId
	+ " AND left_course_user_id = " + normalizedLeft
	+ " AND right_course_user_id = " + normalizedRight
	+ " AND status = 1"
);
if(!pair.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 유사도 결과가 없습니다.");
	result.print();
	return;
}

DataSet leftInfo = homeworkUser.query(
	" SELECT cu.id course_user_id, cu.user_id, u.login_id, u.user_nm "
	+ " , hu.subject, hu.content, hu.reg_date submit_date "
	+ " FROM " + courseUser.table + " cu "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id AND u.status != -1 "
	+ " LEFT JOIN " + homeworkUser.table + " hu ON hu.course_user_id = cu.id AND hu.homework_id = " + homeworkId + " AND hu.status = 1 "
	+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.id = " + normalizedLeft + " AND cu.status IN (1,3) "
);
if(!leftInfo.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "왼쪽 수강생 정보를 찾을 수 없습니다.");
	result.print();
	return;
}

DataSet rightInfo = homeworkUser.query(
	" SELECT cu.id course_user_id, cu.user_id, u.login_id, u.user_nm "
	+ " , hu.subject, hu.content, hu.reg_date submit_date "
	+ " FROM " + courseUser.table + " cu "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id AND u.status != -1 "
	+ " LEFT JOIN " + homeworkUser.table + " hu ON hu.course_user_id = cu.id AND hu.homework_id = " + homeworkId + " AND hu.status = 1 "
	+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.id = " + normalizedRight + " AND cu.status IN (1,3) "
);
if(!rightInfo.next()) {
	result.put("rst_code", "4043");
	result.put("rst_message", "오른쪽 수강생 정보를 찾을 수 없습니다.");
	result.print();
	return;
}

ArrayList<Hashtable<String, Object>> leftFiles = new ArrayList<Hashtable<String, Object>>();
DataSet leftFileList = file.find("module = 'homework_" + homeworkId + "' AND module_id = " + normalizedLeft + " AND site_id = " + siteId + " AND status = 1", "*", "id ASC");
while(leftFileList.next()) {
	Hashtable<String, Object> row = new Hashtable<String, Object>();
	String fileId = leftFileList.s("id");
	String ek = m.encrypt(fileId);
	row.put("id", leftFileList.i("id"));
	row.put("filename", leftFileList.s("filename"));
	row.put("ek", ek);
	row.put("download_url", "/classroom/download_cl.jsp?id=" + fileId + "&ek=" + ek);
	leftFiles.add(row);
}

ArrayList<Hashtable<String, Object>> rightFiles = new ArrayList<Hashtable<String, Object>>();
DataSet rightFileList = file.find("module = 'homework_" + homeworkId + "' AND module_id = " + normalizedRight + " AND site_id = " + siteId + " AND status = 1", "*", "id ASC");
while(rightFileList.next()) {
	Hashtable<String, Object> row = new Hashtable<String, Object>();
	String fileId = rightFileList.s("id");
	String ek = m.encrypt(fileId);
	row.put("id", rightFileList.i("id"));
	row.put("filename", rightFileList.s("filename"));
	row.put("ek", ek);
	row.put("download_url", "/classroom/download_cl.jsp?id=" + fileId + "&ek=" + ek);
	rightFiles.add(row);
}

Hashtable<String, Object> leftData = new Hashtable<String, Object>();
leftData.put("course_user_id", leftInfo.i("course_user_id"));
leftData.put("user_id", leftInfo.i("user_id"));
leftData.put("login_id", leftInfo.s("login_id"));
leftData.put("user_nm", leftInfo.s("user_nm"));
leftData.put("subject", leftInfo.s("subject"));
leftData.put("content", leftInfo.s("content"));
leftData.put("submitted_at", !"".equals(leftInfo.s("submit_date")) ? m.time("yyyy.MM.dd HH:mm", leftInfo.s("submit_date")) : "-");
leftData.put("files", leftFiles);

Hashtable<String, Object> rightData = new Hashtable<String, Object>();
rightData.put("course_user_id", rightInfo.i("course_user_id"));
rightData.put("user_id", rightInfo.i("user_id"));
rightData.put("login_id", rightInfo.s("login_id"));
rightData.put("user_nm", rightInfo.s("user_nm"));
rightData.put("subject", rightInfo.s("subject"));
rightData.put("content", rightInfo.s("content"));
rightData.put("submitted_at", !"".equals(rightInfo.s("submit_date")) ? m.time("yyyy.MM.dd HH:mm", rightInfo.s("submit_date")) : "-");
rightData.put("files", rightFiles);

Hashtable<String, Object> pairData = new Hashtable<String, Object>();
pairData.put("id", pair.i("id"));
pairData.put("run_id", pair.i("run_id"));
pairData.put("left_course_user_id", pair.i("left_course_user_id"));
pairData.put("right_course_user_id", pair.i("right_course_user_id"));
pairData.put("subject_score", pair.d("subject_score"));
pairData.put("content_score", pair.d("content_score"));
pairData.put("file_score", pair.d("file_score"));
pairData.put("total_score", pair.d("total_score"));
pairData.put("reason_json", pair.s("reason_json"));
pairData.put("reg_date", pair.s("reg_date"));

Hashtable<String, Object> data = new Hashtable<String, Object>();
data.put("pair", pairData);
data.put("left", leftData);
data.put("right", rightData);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", data);
result.print();

%>
