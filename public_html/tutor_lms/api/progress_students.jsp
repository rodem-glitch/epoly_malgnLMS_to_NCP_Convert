<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 특정 차시(레슨)를 선택했을 때, 수강생별로 진도율/학습시간/완료여부를 표로 보여줘야 합니다.
//- 그래서 LM_COURSE_USER(수강생) + TB_USER(회원) + LM_COURSE_PROGRESS(진도)를 합쳐 목록을 내려줍니다.

int courseId = m.ri("course_id");
int lessonId = m.ri("lesson_id");
// 왜: lesson_id가 -1이면 전체 진도율만 조회합니다.
boolean isOverallView = (lessonId == -1);
if(0 == courseId || (0 == lessonId && !isOverallView)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id와 lesson_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
CourseProgressDao courseProgress = new CourseProgressDao(siteId);
UserDao user = new UserDao();

//권한: 교수자는 본인 과목(주/보조강사)만, 관리자는 전체
if(!isAdmin) {
	// 왜: 보조강사도 진도/출석을 확인해야 하므로 major+minor를 모두 허용합니다.
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type IN ('major','minor') AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 진도를 조회할 권한이 없습니다.");
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

DataSet list;
if(isOverallView) {
	// 왜: 전체 보기 모드에서는 차시별 진도가 아닌 과정 전체 진도율만 조회합니다.
	list = courseUser.query(
		" SELECT cu.id course_user_id, cu.user_id, cu.course_id "
		+ " , cu.progress_ratio total_progress_ratio "
		+ " , u.login_id, u.user_nm, u.email "
		+ " , IFNULL(cu.progress_ratio, 0) ratio "
		+ " , (SELECT SUM(IFNULL(cp2.study_time, 0)) FROM LM_COURSE_PROGRESS cp2 WHERE cp2.course_user_id = cu.id AND cp2.site_id = " + siteId + " AND cp2.status = 1) study_time "
		+ " , (SELECT COUNT(*) FROM LM_COURSE_LESSON cl WHERE cl.course_id = cu.course_id AND cl.progress_yn = 'Y' AND cl.status = 1) lesson_cnt "
		+ " , (SELECT COUNT(*) FROM LM_COURSE_PROGRESS cp3 WHERE cp3.course_user_id = cu.id AND cp3.site_id = " + siteId + " AND cp3.complete_yn = 'Y' AND cp3.status = 1) complete_cnt "
		+ " , cu.complete_yn, cu.complete_date, '' last_date "
		+ " FROM " + courseUser.table + " cu "
		+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status NOT IN (-1, -4) "
		+ " ORDER BY u.user_nm ASC, cu.id ASC "
	);
} else {
	list = courseUser.query(
		" SELECT cu.id course_user_id, cu.user_id, cu.course_id "
		+ " , cu.progress_ratio total_progress_ratio "
		+ " , u.login_id, u.user_nm, u.email "
		+ " , IFNULL(cp.ratio, 0) ratio, IFNULL(cp.study_time, 0) study_time, IFNULL(cp.view_cnt, 0) view_cnt "
		+ " , (SELECT COUNT(*) FROM LM_COURSE_LESSON cl WHERE cl.course_id = cu.course_id AND cl.progress_yn = 'Y' AND cl.status = 1) lesson_cnt "
		+ " , (SELECT COUNT(*) FROM LM_COURSE_PROGRESS cp3 WHERE cp3.course_user_id = cu.id AND cp3.site_id = " + siteId + " AND cp3.complete_yn = 'Y' AND cp3.status = 1) complete_cnt "
		+ " , IFNULL(cp.complete_yn, 'N') complete_yn, IFNULL(cp.complete_date, '') complete_date, IFNULL(cp.last_date, '') last_date "
		+ " FROM " + courseUser.table + " cu "
		+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id "
		+ " LEFT JOIN " + courseProgress.table + " cp ON cp.course_user_id = cu.id AND cp.lesson_id = " + lessonId + " AND cp.site_id = " + siteId + " AND cp.status = 1 "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status NOT IN (-1, -4) "
		+ " ORDER BY u.user_nm ASC, cu.id ASC "
	);
}

while(list.next()) {
	list.put("student_id", list.s("login_id"));
	list.put("name", list.s("user_nm"));
	list.put("email", list.s("email"));
	list.put("ratio", Malgn.round(list.d("ratio"), 1));

	int time = list.i("study_time");
	list.put("study_time_conv", String.format("%02d:%02d:%02d", (time / 3600), (time % 3600 / 60), (time % 3600 % 60)));

	String completeDate = list.s("complete_date");
	list.put("complete_date_conv", !"".equals(completeDate) ? m.time("yyyy.MM.dd HH:mm", completeDate) : "-");

	String lastDate = list.s("last_date");
	list.put("last_date_conv", !"".equals(lastDate) ? m.time("yyyy.MM.dd HH:mm", lastDate) : "-");

	int lessonCnt = list.i("lesson_cnt");
	int completeCnt = list.i("complete_cnt");
	int absenceCnt = Math.max(0, lessonCnt - completeCnt);
	boolean absenceFail = "Y".equals(cinfo.s("limit_absence_yn"))
		&& cinfo.i("limit_absence_cnt") > 0
		&& absenceCnt >= cinfo.i("limit_absence_cnt");

	list.put("absence_cnt", absenceCnt);
	list.put("absence_fail_yn", absenceFail ? "Y" : "N");
	list.put("limit_absence_yn", cinfo.s("limit_absence_yn"));
	list.put("limit_absence_cnt", cinfo.i("limit_absence_cnt"));
	if(absenceFail) {
		list.put("absence_status_label", "R".equals(cinfo.s("course_type")) ? "F" : "미수료");
	} else {
		list.put("absence_status_label", "-");
	}
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.put("rst_course", cinfo);
result.print();

%>
