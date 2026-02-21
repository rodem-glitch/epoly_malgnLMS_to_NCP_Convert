<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 성적관리 화면에서 그래프를 그리려면, 개별 학생 목록이 아니라 "점수 구간별 집계" 데이터가 필요합니다.
//- 기존 성적 목록 API와 동일한 권한/과목 기준으로 분포 통계를 제공합니다.

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 성적 통계를 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

m.log(
	"grades_distribution",
	"stats_start user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
);

DataSet scores = courseUser.find(
	"site_id = " + siteId + " AND course_id = " + courseId + " AND status IN (1,3)",
	"id, progress_ratio, total_score"
);

int totalCount = 0;
double sumScore = 0.0;
double minScore = 0.0;
double maxScore = 0.0;
boolean hasScore = false;

int passCount = 0;
int completeCount = 0;
int failCount = 0;

int[] bucketCounts = new int[] {0, 0, 0, 0, 0}; // 90~100, 80~89, 70~79, 60~69, 0~59

scores.first();
while(scores.next()) {
	double score = scores.d("total_score");
	if(score < 0) score = 0;
	if(score > 100) score = 100;

	sumScore += score;
	totalCount++;
	if(!hasScore) {
		minScore = score;
		maxScore = score;
		hasScore = true;
	} else {
		if(score < minScore) minScore = score;
		if(score > maxScore) maxScore = score;
	}

	if(score >= 90) bucketCounts[0]++;
	else if(score >= 80) bucketCounts[1]++;
	else if(score >= 70) bucketCounts[2]++;
	else if(score >= 60) bucketCounts[3]++;
	else bucketCounts[4]++;

	// 왜: 성적표의 합격/수료/미달 기준과 동일해야 화면 통계와 목록 값이 서로 어긋나지 않습니다.
	boolean passYn = "Y".equals(cinfo.s("pass_yn"));
	boolean meetPass = passYn
		&& scores.d("progress_ratio") >= cinfo.d("limit_progress")
		&& (0 == cinfo.i("limit_total_score") || score >= cinfo.d("limit_total_score"));
	boolean meetComplete = scores.d("progress_ratio") >= cinfo.d("complete_limit_progress")
		&& (0 == cinfo.i("complete_limit_total_score") || score >= cinfo.d("complete_limit_total_score"));
	if(meetPass) passCount++;
	else if(meetComplete) completeCount++;
	else failCount++;
}

double avgScore = 0.0;
if(0 < totalCount) avgScore = sumScore / totalCount;

DataSet summary = new DataSet();
summary.addRow();
summary.put("total_count", totalCount);
summary.put("avg_score", Math.round(avgScore * 100.0) / 100.0);
summary.put("min_score", hasScore ? (Math.round(minScore * 100.0) / 100.0) : 0);
summary.put("max_score", hasScore ? (Math.round(maxScore * 100.0) / 100.0) : 0);
summary.put("pass_count", passCount);
summary.put("complete_count", completeCount);
summary.put("fail_count", failCount);
summary.next();

String[] bucketKeys = new String[] {"A", "B", "C", "D", "F"};
String[] bucketLabels = new String[] {"90~100점", "80~89점", "70~79점", "60~69점", "0~59점"};
int[] bucketMins = new int[] {90, 80, 70, 60, 0};
int[] bucketMaxs = new int[] {100, 89, 79, 69, 59};

DataSet distribution = new DataSet();
for(int i = 0; i < bucketKeys.length; i++) {
	double ratio = 0.0;
	if(0 < totalCount) ratio = (bucketCounts[i] * 100.0) / totalCount;
	distribution.addRow();
	distribution.put("bucket_key", bucketKeys[i]);
	distribution.put("bucket_label", bucketLabels[i]);
	distribution.put("min_score", bucketMins[i]);
	distribution.put("max_score", bucketMaxs[i]);
	distribution.put("student_count", bucketCounts[i]);
	distribution.put("ratio", Math.round(ratio * 100.0) / 100.0);
}

m.log(
	"grades_distribution",
	"stats_done user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", total_count=" + totalCount
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", totalCount);
result.put("rst_summary", summary);
result.put("rst_data", distribution);
result.print();

%>
