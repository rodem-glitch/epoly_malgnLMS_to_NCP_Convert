<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 과제 제출물 의심쌍(일치율 높은 순)을 빠르게 확인할 수 있어야 피드백/채점 전에 점검할 수 있습니다.

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
int pageNum = m.ri("page");
int pageSize = m.ri("page_size");
double minScore = m.parseDouble(m.rs("min_score"));

if(0 == courseId || 0 == homeworkId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id가 필요합니다.");
	result.print();
	return;
}
if(pageNum < 1) pageNum = 1;
if(pageSize < 1) pageSize = 20;
if(200 < pageSize) pageSize = 200;
if(minScore <= 0) minScore = 70.0;
if(minScore < 0 || 100 < minScore) {
	result.put("rst_code", "1100");
	result.put("rst_message", "min_score는 0~100 사이여야 합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
HomeworkDao homework = new HomeworkDao();
HomeworkSimilarityResultDao similarityResult = new HomeworkSimilarityResultDao();
HomeworkSimilarityRunDao similarityRun = new HomeworkSimilarityRunDao();
CourseUserDao courseUser = new CourseUserDao();
UserDao user = new UserDao();

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 일치율을 조회할 권한이 없습니다.");
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

String countWhere =
	"site_id = " + siteId
	+ " AND course_id = " + courseId
	+ " AND homework_id = " + homeworkId
	+ " AND status = 1";

String listWhere =
	"r.site_id = " + siteId
	+ " AND r.course_id = " + courseId
	+ " AND r.homework_id = " + homeworkId
	+ " AND r.status = 1";

int totalCount = similarityResult.findCount(countWhere + " AND total_score >= " + minScore);
int totalPage = totalCount > 0 ? (int)Math.ceil((double)totalCount / (double)pageSize) : 1;
int offset = (pageNum - 1) * pageSize;

DataSet list = similarityResult.query(
	" SELECT r.id, r.run_id, r.left_course_user_id, r.right_course_user_id "
	+ " , r.subject_score, r.content_score, r.file_score, r.total_score "
	+ " , lu.user_id left_user_id, ru.user_id right_user_id "
	+ " , l.login_id left_login_id, l.user_nm left_user_nm "
	+ " , rr.login_id right_login_id, rr.user_nm right_user_nm "
	+ " FROM " + similarityResult.table + " r "
	+ " INNER JOIN " + courseUser.table + " lu ON lu.id = r.left_course_user_id AND lu.site_id = " + siteId + " AND lu.course_id = " + courseId + " AND lu.status IN (1,3) "
	+ " INNER JOIN " + user.table + " l ON l.id = lu.user_id AND l.status != -1 "
	+ " INNER JOIN " + courseUser.table + " ru ON ru.id = r.right_course_user_id AND ru.site_id = " + siteId + " AND ru.course_id = " + courseId + " AND ru.status IN (1,3) "
	+ " INNER JOIN " + user.table + " rr ON rr.id = ru.user_id AND rr.status != -1 "
	+ " WHERE " + listWhere + " AND r.total_score >= " + minScore
	+ " ORDER BY r.total_score DESC, r.id DESC "
	+ " LIMIT " + offset + ", " + pageSize
);

while(list.next()) {
	list.put("subject_score_conv", m.nf(list.d("subject_score"), 2));
	list.put("content_score_conv", m.nf(list.d("content_score"), 2));
	list.put("file_score_conv", m.nf(list.d("file_score"), 2));
	list.put("total_score_conv", m.nf(list.d("total_score"), 2));
}

Hashtable<String, Object> latestRun = new Hashtable<String, Object>();
DataSet runInfo = similarityRun.query(
	" SELECT id, run_type, threshold_score, pair_total, pair_saved, started_at, ended_at, message, status "
	+ " FROM " + similarityRun.table
	+ " WHERE site_id = " + siteId + " AND course_id = " + courseId + " AND homework_id = " + homeworkId
	+ " ORDER BY id DESC "
	, 1
);
if(runInfo.next()) {
	latestRun.put("id", runInfo.i("id"));
	latestRun.put("run_type", runInfo.s("run_type"));
	latestRun.put("threshold_score", runInfo.d("threshold_score"));
	latestRun.put("pair_total", runInfo.i("pair_total"));
	latestRun.put("pair_saved", runInfo.i("pair_saved"));
	latestRun.put("started_at", runInfo.s("started_at"));
	latestRun.put("ended_at", runInfo.s("ended_at"));
	latestRun.put("message", runInfo.s("message"));
	latestRun.put("status", runInfo.i("status"));
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_total_count", totalCount);
result.put("rst_total_page", totalPage);
result.put("rst_page", pageNum);
result.put("rst_page_size", pageSize);
result.put("rst_min_score", minScore);
result.put("rst_run", latestRun);
result.put("rst_data", list);
result.print();

%>
