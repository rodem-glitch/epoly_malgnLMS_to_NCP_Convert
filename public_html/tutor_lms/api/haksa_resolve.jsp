<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 학사 과목은 5종키로 식별되는데, 교수자 LMS는 숫자 course_id를 기준으로 동작합니다.
// - 그래서 "학사키 → LMS 과정/수강생"을 한 번에 매핑해,
//   시험/과제/자료/Q&A/성적/진도/출석이 프리즘과 동일하게 동작하도록 합니다.

String courseCode = m.rs("course_code");
String openYear = m.rs("open_year");
String openTerm = m.rs("open_term");
String bunbanCode = m.rs("bunban_code");
String groupCode = m.rs("group_code");

System.out.println("[haksa_resolve] start course_code=" + courseCode + " open_year=" + openYear
	+ " open_term=" + openTerm + " bunban_code=" + bunbanCode + " group_code=" + groupCode + " user_id=" + userId);

if("".equals(courseCode) || "".equals(openYear) || "".equals(openTerm) || "".equals(bunbanCode) || "".equals(groupCode)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "학사 과목 키(course_code/open_year/open_term/bunban_code/group_code)가 필요합니다.");
	result.print();
	return;
}

try {
	CourseDao course = new CourseDao();
	CourseUserDao courseUser = new CourseUserDao();
	CourseTutorDao courseTutor = new CourseTutorDao();
	CourseManagerDao courseManager = new CourseManagerDao();
	ClBoardDao board = new ClBoardDao(siteId);

	PolyCourseDao polyCourse = new PolyCourseDao();
	PolyStudentDao polyStudent = new PolyStudentDao();
	PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();
	UserDao user = new UserDao();

	String haksaCourseKey = courseCode + "_" + openYear + "_" + openTerm + "_" + bunbanCode + "_" + groupCode;
	String haksaCourseCd = m.md5(haksaCourseKey);
	if(haksaCourseCd != null && haksaCourseCd.length() > 20) haksaCourseCd = haksaCourseCd.substring(0, 20);
	if(haksaCourseCd == null) haksaCourseCd = "";

	String safeHaksaCourseKey = m.replace(haksaCourseKey, "'", "''");
	String safeHaksaCourseCd = m.replace(haksaCourseCd, "'", "''");

	int mappedCourseId = 0;
	String startDate = "";
	String endDate = "";

	DataSet mappedCourse = course.find(
		"site_id = " + siteId
		+ " AND (course_cd = '" + safeHaksaCourseCd + "' OR course_cd = '" + safeHaksaCourseKey + "' OR etc1 = '" + safeHaksaCourseKey + "')"
		+ " AND status != -1"
	);
	if(mappedCourse.next()) {
		mappedCourseId = mappedCourse.i("id");
		startDate = mappedCourse.s("study_sdate");
		endDate = mappedCourse.s("study_edate");
		System.out.println("[haksa_resolve] mapped course exists id=" + mappedCourseId);
		try {
			// 왜: 과거 데이터 호환을 위해 etc1/etc2 표식을 최신 상태로 보정합니다.
			boolean needCourseUpdate = false;
			course.clear();
			if(!haksaCourseKey.equals(mappedCourse.s("etc1"))) { course.item("etc1", haksaCourseKey); needCourseUpdate = true; }
			if(!"HAKSA_MAPPED".equals(mappedCourse.s("etc2"))) { course.item("etc2", "HAKSA_MAPPED"); needCourseUpdate = true; }
			if(needCourseUpdate) course.update("id = " + mappedCourseId);
		} catch(Exception ignore) {}
	}

	if(mappedCourseId == 0) {
		// 왜: 학사 과정 정보(시작/종료일/과목명)를 최대한 활용해 LMS 과정 기본값을 맞춥니다.
		DataSet haksaCourseInfo = polyCourse.query(
			"SELECT * FROM " + polyCourse.table
			+ " WHERE course_code = '" + m.replace(courseCode, "'", "''") + "'"
			+ " AND open_year = '" + m.replace(openYear, "'", "''") + "'"
			+ " AND open_term = '" + m.replace(openTerm, "'", "''") + "'"
			+ " AND bunban_code = '" + m.replace(bunbanCode, "'", "''") + "'"
			+ " AND group_code = '" + m.replace(groupCode, "'", "''") + "'"
			+ " LIMIT 1"
		);

		String courseName = "";
		if(haksaCourseInfo.next()) {
			courseName = haksaCourseInfo.s("course_name");
			startDate = haksaCourseInfo.s("startdate");
			endDate = haksaCourseInfo.s("enddate");
		}

		// 왜: 외부/미러 데이터는 날짜 포맷이 섞일 수 있어 숫자만 남깁니다.
		if(startDate != null) startDate = startDate.replaceAll("[^0-9]", "");
		if(endDate != null) endDate = endDate.replaceAll("[^0-9]", "");
		if(startDate == null) startDate = "";
		if(endDate == null) endDate = "";
		if(startDate.length() >= 8) startDate = startDate.substring(0, 8);
		if(endDate.length() >= 8) endDate = endDate.substring(0, 8);
		if(startDate.length() != 8) startDate = m.time("yyyyMMdd");
		if(endDate.length() != 8) endDate = "99991231";

		int newCourseId = course.getSequence();
		course.item("id", newCourseId);
		course.item("site_id", siteId);
		course.item("course_cd", haksaCourseCd);
		course.item("etc1", haksaCourseKey);
		// 왜: 학사 연동용으로 자동 생성된 "숨김 LMS 과정"임을 표시합니다.
		course.item("etc2", "HAKSA_MAPPED");
		course.item("year", openYear);

		int stepVal = m.parseInt(openTerm);
		if(stepVal <= 0) stepVal = 1;
		course.item("step", stepVal);
		course.item("course_nm", !"".equals(courseName) ? courseName : (courseCode + " 과목"));
		course.item("course_type", "R");
		course.item("onoff_type", "N");

		// 왜: NOT NULL + 기본값 없음인 컬럼들이 있어 기본값을 명시합니다.
		course.item("lesson_day", 0);
		course.item("lesson_time", 0);
		course.item("list_price", 0);
		course.item("price", 0);
		course.item("credit", 0);
		course.item("renew_price", 0);
		course.item("assign_progress", 100);
		course.item("assign_exam", 0);
		course.item("assign_homework", 0);
		course.item("assign_forum", 0);
		course.item("assign_etc", 0);
		course.item("limit_progress", 60);
		course.item("limit_exam", 0);
		course.item("limit_homework", 0);
		course.item("limit_forum", 0);
		course.item("limit_etc", 0);
		course.item("complete_limit_progress", 60);
		course.item("complete_limit_total_score", 0);
		course.item("study_sdate", startDate);
		course.item("study_edate", endDate);
		course.item("request_sdate", startDate);
		course.item("request_edate", endDate);
		course.item("display_yn", "N");
		course.item("sale_yn", "N");
		course.item("manager_id", 0);
		course.item("exam_yn", "Y");
		course.item("homework_yn", "Y");
		course.item("forum_yn", "N");
		course.item("survey_yn", "N");
		course.item("review_yn", "Y");
		course.item("reg_date", m.time("yyyyMMddHHmmss"));
		course.item("status", 1);
		boolean inserted = course.insert();
		if(inserted) {
			mappedCourseId = newCourseId;
			System.out.println("[haksa_resolve] course inserted id=" + mappedCourseId);
		} else {
			System.out.println("[haksa_resolve] course insert failed (id=" + newCourseId + ")");
			try {
				DataSet retry = course.find(
					"site_id = " + siteId
					+ " AND (course_cd = '" + safeHaksaCourseCd + "' OR course_cd = '" + safeHaksaCourseKey + "' OR etc1 = '" + safeHaksaCourseKey + "')"
					+ " AND status != -1"
				);
				if(retry.next()) {
					mappedCourseId = retry.i("id");
					System.out.println("[haksa_resolve] course found after insert fail id=" + mappedCourseId);
				}
			} catch(Exception ignore) {}
		}

		// 왜: Q&A 등 기본 게시판이 없으면 교수자 화면이 비어 보이므로 신규 과정일 때 생성합니다.
		if(mappedCourseId > 0) {
			try { board.insertBoard(mappedCourseId); } catch(Exception ignore) {}
		}
	}
	if(mappedCourseId <= 0) {
		System.out.println("[haksa_resolve] mapped course not found");
		result.put("rst_code", "5002");
		result.put("rst_message", "매핑된 LMS 과정ID를 찾지 못했습니다.");
		result.print();
		return;
	}

// 왜: 교수자가 해당 과목을 관리할 수 있도록 과정-강사 매핑을 보장합니다.
if(mappedCourseId > 0) {
	try {
		if(0 >= courseTutor.findCount(
			"course_id = " + mappedCourseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId
		)) {
			courseTutor.item("course_id", mappedCourseId);
			courseTutor.item("user_id", userId);
			courseTutor.item("site_id", siteId);
			courseTutor.item("type", "major");
			courseTutor.item("class", "1");
			courseTutor.insert();
		}
	} catch(Exception ignore) {}

	// 왜: sysop "과정담당자" 목록과 교수자 화면 기준을 맞추기 위해 과정담당자도 함께 보장합니다.
	try {
		if(0 >= courseManager.findCount(
			"course_id = " + mappedCourseId + " AND user_id = " + userId + " AND site_id = " + siteId
		)) {
			courseManager.item("course_id", mappedCourseId);
			courseManager.item("user_id", userId);
			courseManager.item("site_id", siteId);
			courseManager.insert();
		}
	} catch(Exception ignore) {}
}

int mappedCount = 0;
int skippedCount = 0;
int missingCount = 0;

// 왜: 교수자 탭에서 진도/성적/제출현황이 보이려면 LM_COURSE_USER가 필요합니다.
if(mappedCourseId > 0) {
	try {
		String safeCourseCode = m.replace(courseCode, "'", "''");
		String safeOpenYear = m.replace(openYear, "'", "''");
		String safeOpenTerm = m.replace(openTerm, "'", "''");
		String safeBunbanCode = m.replace(bunbanCode, "'", "''");
		String safeGroupCode = m.replace(groupCode, "'", "''");

		DataSet students = polyStudent.query(
			"SELECT s.member_key, MAX(u.id) user_id "
			+ " FROM " + polyStudent.table + " s "
			+ " LEFT JOIN " + polyMemberKey.table + " mk ON mk.member_key = s.member_key "
			+ " LEFT JOIN " + user.table + " u ON u.site_id = " + siteId + " AND u.status = 1 "
				+ " AND (u.login_id COLLATE utf8mb4_unicode_ci = s.member_key COLLATE utf8mb4_unicode_ci"
				+ " OR u.login_id COLLATE utf8mb4_unicode_ci = mk.alias_key COLLATE utf8mb4_unicode_ci) "
			+ " WHERE s.course_code = '" + safeCourseCode + "' AND s.open_year = '" + safeOpenYear + "'"
			+ " AND s.open_term = '" + safeOpenTerm + "' AND s.bunban_code = '" + safeBunbanCode + "' AND s.group_code = '" + safeGroupCode + "'"
			+ " GROUP BY s.member_key "
		);

		String today = m.time("yyyyMMdd");
		if("".equals(startDate) || startDate.length() != 8) startDate = today;
		if("".equals(endDate) || endDate.length() != 8) endDate = "99991231";

		while(students.next()) {
			int uid = students.i("user_id");
			if(uid <= 0) {
				missingCount++;
				continue;
			}

			int cuCount = courseUser.findCount(
				"course_id = " + mappedCourseId + " AND user_id = " + uid + " AND status IN (1,3)"
			);
			if(cuCount > 0) {
				skippedCount++;
				continue;
			}

			int newCuid = courseUser.getSequence();
			courseUser.item("id", newCuid);
			courseUser.item("site_id", siteId);
			courseUser.item("package_id", 0);
			courseUser.item("course_id", mappedCourseId);
			courseUser.item("user_id", uid);
			courseUser.item("order_id", 0);
			courseUser.item("order_item_id", 0);
			courseUser.item("grade", 1);
			courseUser.item("renew_cnt", 0);
			courseUser.item("start_date", startDate);
			courseUser.item("end_date", endDate);
			courseUser.item("progress_ratio", 0);
			courseUser.item("progress_score", 0);
			courseUser.item("exam_value", 0);
			courseUser.item("exam_score", 0);
			courseUser.item("homework_value", 0);
			courseUser.item("homework_score", 0);
			courseUser.item("forum_value", 0);
			courseUser.item("forum_score", 0);
			courseUser.item("etc_value", 0);
			courseUser.item("etc_score", 0);
			courseUser.item("total_score", 0);
			courseUser.item("credit", 0);
			courseUser.item("complete_status", "");
			courseUser.item("complete_yn", "");
			courseUser.item("complete_no", "");
			courseUser.item("complete_date", "");
			courseUser.item("close_yn", "N");
			courseUser.item("close_date", "");
			courseUser.item("close_user_id", 0);
			courseUser.item("change_date", "");
			courseUser.item("mod_date", "");
			courseUser.item("reg_date", m.time("yyyyMMddHHmmss"));
			courseUser.item("status", 1);
			if(courseUser.insert()) mappedCount++;
		}
	} catch(Exception ignore) {}
}

	DataSet data = new DataSet();
	data.addRow();
	data.put("mapped_course_id", mappedCourseId);
	data.put("mapped_students", mappedCount);
	data.put("skipped_students", skippedCount);
	data.put("missing_students", missingCount);

	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_data", data);
	result.print();
} catch(Exception e) {
	System.out.println("[haksa_resolve] error: " + e.getMessage());
	result.put("rst_code", "5000");
	result.put("rst_message", "해석기 처리 중 오류가 발생했습니다. " + e.getMessage());
	result.print();
}

%>
