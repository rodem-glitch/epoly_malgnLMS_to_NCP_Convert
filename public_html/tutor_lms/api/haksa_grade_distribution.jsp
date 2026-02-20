<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 학사(정규) 과목은 course_id 대신 학사 5종 키로 성적을 관리하므로, 전용 분포 통계 API가 필요합니다.
//- 그래프 화면에서 바로 쓸 수 있게 점수구간/등급구간 집계를 함께 제공합니다.

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

PolyCourseGradeDao grade = new PolyCourseGradeDao();
PolyCourseProfDao polyCourseProf = new PolyCourseProfDao();
PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();
UserDao user = new UserDao();

//권한
if(!isAdmin) {
	String dbLoginId = loginId;
	try {
		DataSet loginInfo = user.find("id = " + userId + " AND site_id = " + siteId + " AND status = 1");
		if(loginInfo.next() && !"".equals(loginInfo.s("login_id"))) dbLoginId = loginInfo.s("login_id");
	} catch(Exception ignore) {}

	String resolvedMemberKey = dbLoginId;
	try {
		DataSet mk = polyMemberKey.query(
			" SELECT member_key FROM " + polyMemberKey.table
			+ " WHERE alias_key = ? OR member_key = ? OR alias_key = ? OR member_key = ? "
			+ " LIMIT 1 "
			, new Object[] { dbLoginId, dbLoginId, loginId, loginId }
		);
		if(mk.next() && !"".equals(mk.s("member_key"))) resolvedMemberKey = mk.s("member_key");
	} catch(Exception ignore) {}

	int allowed = polyCourseProf.getOneInt(
		" SELECT COUNT(*) FROM " + polyCourseProf.table
		+ " WHERE course_code = ? AND open_year = ? AND open_term = ? AND bunban_code = ? AND group_code = ? AND member_key = ? "
		, new Object[] { courseCode, openYear, openTerm, bunbanCode, groupCode, resolvedMemberKey }
	);
	if(allowed <= 0) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 학사 과목의 성적 통계를 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

m.log(
	"haksa_grade_distribution",
	"stats_start user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", open_year=" + openYear
	+ ", open_term=" + openTerm
	+ ", bunban_code=" + bunbanCode
	+ ", group_code=" + groupCode
);

DataSet list = grade.query(
	" SELECT grade, score "
	+ " FROM " + grade.table
	+ " WHERE site_id = ? AND course_code = ? AND open_year = ? AND open_term = ? "
	+ " AND bunban_code = ? AND group_code = ? AND status != -1 "
	, new Object[] { siteId, courseCode, openYear, openTerm, bunbanCode, groupCode }
);

int totalCount = 0;
double sumScore = 0.0;
double minScore = 0.0;
double maxScore = 0.0;
boolean hasScore = false;

int[] scoreBucketCounts = new int[] {0, 0, 0, 0, 0}; // 90~100, 80~89, 70~79, 60~69, 0~59
String[] gradeKeys = new String[] {"A+", "A", "B+", "B", "C+", "C", "D+", "D", "F", "ETC"};
int[] gradeCounts = new int[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

list.first();
while(list.next()) {
	int score = list.i("score");
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

	if(score >= 90) scoreBucketCounts[0]++;
	else if(score >= 80) scoreBucketCounts[1]++;
	else if(score >= 70) scoreBucketCounts[2]++;
	else if(score >= 60) scoreBucketCounts[3]++;
	else scoreBucketCounts[4]++;

	String gradeValue = m.replace(list.s("grade"), " ", "").toUpperCase();
	if("A+".equals(gradeValue)) gradeCounts[0]++;
	else if("A".equals(gradeValue)) gradeCounts[1]++;
	else if("B+".equals(gradeValue)) gradeCounts[2]++;
	else if("B".equals(gradeValue)) gradeCounts[3]++;
	else if("C+".equals(gradeValue)) gradeCounts[4]++;
	else if("C".equals(gradeValue)) gradeCounts[5]++;
	else if("D+".equals(gradeValue)) gradeCounts[6]++;
	else if("D".equals(gradeValue)) gradeCounts[7]++;
	else if("F".equals(gradeValue)) gradeCounts[8]++;
	else gradeCounts[9]++;
}

double avgScore = 0.0;
if(0 < totalCount) avgScore = sumScore / totalCount;

DataSet summary = new DataSet();
summary.addRow();
summary.put("total_count", totalCount);
summary.put("avg_score", Math.round(avgScore * 100.0) / 100.0);
summary.put("min_score", hasScore ? (Math.round(minScore * 100.0) / 100.0) : 0);
summary.put("max_score", hasScore ? (Math.round(maxScore * 100.0) / 100.0) : 0);
summary.next();

String[] scoreBucketLabels = new String[] {"90~100점", "80~89점", "70~79점", "60~69점", "0~59점"};
int[] scoreBucketMins = new int[] {90, 80, 70, 60, 0};
int[] scoreBucketMaxs = new int[] {100, 89, 79, 69, 59};

DataSet scoreDistribution = new DataSet();
for(int i = 0; i < scoreBucketLabels.length; i++) {
	double ratio = 0.0;
	if(0 < totalCount) ratio = (scoreBucketCounts[i] * 100.0) / totalCount;
	scoreDistribution.addRow();
	scoreDistribution.put("bucket_key", (i == 0 ? "A" : i == 1 ? "B" : i == 2 ? "C" : i == 3 ? "D" : "F"));
	scoreDistribution.put("bucket_label", scoreBucketLabels[i]);
	scoreDistribution.put("min_score", scoreBucketMins[i]);
	scoreDistribution.put("max_score", scoreBucketMaxs[i]);
	scoreDistribution.put("student_count", scoreBucketCounts[i]);
	scoreDistribution.put("ratio", Math.round(ratio * 100.0) / 100.0);
}

DataSet gradeDistribution = new DataSet();
for(int i = 0; i < gradeKeys.length; i++) {
	double ratio = 0.0;
	if(0 < totalCount) ratio = (gradeCounts[i] * 100.0) / totalCount;
	gradeDistribution.addRow();
	gradeDistribution.put("grade_key", gradeKeys[i]);
	gradeDistribution.put("student_count", gradeCounts[i]);
	gradeDistribution.put("ratio", Math.round(ratio * 100.0) / 100.0);
}

m.log(
	"haksa_grade_distribution",
	"stats_done user_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_code=" + courseCode
	+ ", total_count=" + totalCount
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", totalCount);
result.put("rst_summary", summary);
result.put("rst_data", scoreDistribution);
result.put("rst_grade_data", gradeDistribution);
result.print();

%>
