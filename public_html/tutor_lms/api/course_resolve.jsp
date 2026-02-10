<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 대시보드 딥링크는 "담당과목 목록에서 찾아서 열기" 방식이라
//  페이지/필터/탭 상태에 따라 과목을 못 찾는 문제가 생깁니다.
//- 그래서 과목 ID 기반으로 "단건 조회"를 제공해, 목록을 거치지 않고 바로 과목 관리 화면을 열 수 있게 합니다.

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();
SubjectDao subject = new SubjectDao();
PolyCourseDao polyCourse = new PolyCourseDao();
PolyStudentDao polyStudent = new PolyStudentDao();
PolyCourseProfDao polyCourseProf = new PolyCourseProfDao();
PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();
UserDao user = new UserDao();

int courseId = m.ri("course_id");
String sourceType = m.rs("source_type"); // prism/haksa

if(courseId <= 0) {
	result.put("rst_code", "9999");
	result.put("rst_message", "과목 ID가 올바르지 않습니다.");
	result.print();
	return;
}

DataSet courseInfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!courseInfo.next()) {
	result.put("rst_code", "9999");
	result.put("rst_message", "과목 정보를 찾지 못했습니다.");
	result.print();
	return;
}

String actualSourceType = sourceType;
if("".equals(actualSourceType)) {
	actualSourceType = "HAKSA_MAPPED".equals(courseInfo.s("etc2")) ? "haksa" : "prism";
}

if(!"prism".equals(actualSourceType) && !"haksa".equals(actualSourceType)) {
	result.put("rst_code", "9999");
	result.put("rst_message", "과목 유형이 올바르지 않습니다.");
	result.print();
	return;
}

// 권한 확인(관리자 제외)
if(!isAdmin) {
	if("prism".equals(actualSourceType)) {
		int allowed = course.getOneInt(
			" SELECT COUNT(*) FROM " + course.table + " c "
			+ " LEFT JOIN " + courseTutor.table + " ct ON ct.course_id = c.id AND ct.user_id = " + userId + " AND ct.site_id = " + siteId + " "
			+ " LEFT JOIN " + courseManager.table + " cm ON cm.course_id = c.id AND cm.user_id = " + userId + " AND cm.site_id = " + siteId + " "
			+ " WHERE c.id = " + courseId + " AND c.site_id = " + siteId + " AND c.status != -1 "
			+ " AND (c.manager_id = " + userId + " OR ct.type IN ('major','minor') OR cm.user_id IS NOT NULL) "
		);
		if(allowed <= 0) {
			result.put("rst_code", "4030");
			result.put("rst_message", "접근 권한이 없습니다.");
			result.print();
			return;
		}
	} else {
		// 학사 과목은 교수자 매핑(폴리텍 교수 테이블) 기준으로 제한합니다.
		String haksaKey = courseInfo.s("etc1");
		if("".equals(haksaKey)) {
			result.put("rst_code", "9999");
			result.put("rst_message", "학사 과목 키가 비어 있습니다.");
			result.print();
			return;
		}
		String[] parts = haksaKey.split("_");
		if(parts.length < 5) {
			result.put("rst_code", "9999");
			result.put("rst_message", "학사 과목 키 형식이 올바르지 않습니다.");
			result.print();
			return;
		}
		String courseCode = parts[0];
		String openYear = parts[1];
		String openTerm = parts[2];
		String bunbanCode = parts[3];
		String groupCode = parts[4];

		String dbLoginId = loginId;
		try {
			DataSet loginInfo = user.find("id = " + userId);
			if(loginInfo.next() && !"".equals(loginInfo.s("login_id"))) dbLoginId = loginInfo.s("login_id");
		} catch(Exception ignore) {}

		String safeLoginId = m.replace(loginId, "'", "''");
		String safeDbLoginId = m.replace(dbLoginId, "'", "''");
		String resolvedMemberKey = "";
		try {
			DataSet mk = polyMemberKey.query(
				"SELECT member_key FROM " + polyMemberKey.table
				+ " WHERE alias_key = '" + safeDbLoginId + "' OR member_key = '" + safeDbLoginId + "'"
				+ " OR alias_key = '" + safeLoginId + "' OR member_key = '" + safeLoginId + "'"
				+ " LIMIT 1"
			);
			if(mk.next()) resolvedMemberKey = mk.s("member_key");
		} catch(Exception ignore) {}
		if("".equals(resolvedMemberKey)) resolvedMemberKey = dbLoginId;
		String safeResolvedMemberKey = m.replace(resolvedMemberKey, "'", "''");

		int allowed = polyCourseProf.getOneInt(
			" SELECT COUNT(*) FROM " + polyCourseProf.table
			+ " WHERE course_code = '" + m.replace(courseCode, "'", "''") + "'"
			+ " AND open_year = '" + m.replace(openYear, "'", "''") + "'"
			+ " AND open_term = '" + m.replace(openTerm, "'", "''") + "'"
			+ " AND bunban_code = '" + m.replace(bunbanCode, "'", "''") + "'"
			+ " AND group_code = '" + m.replace(groupCode, "'", "''") + "'"
			+ " AND member_key = '" + safeResolvedMemberKey + "'"
		);
		if(allowed <= 0) {
			result.put("rst_code", "4030");
			result.put("rst_message", "접근 권한이 없습니다.");
			result.print();
			return;
		}
	}
}

DataSet resultList = new DataSet();
resultList.addRow();

String today = m.time("yyyyMMdd");

if("prism".equals(actualSourceType)) {
	DataSet info = course.query(
		" SELECT c.*, s.course_nm program_nm "
		+ " FROM " + course.table + " c "
		+ " LEFT JOIN " + subject.table + " s ON s.id = c.subject_id AND s.status != -1 "
		+ " WHERE c.id = " + courseId + " AND c.site_id = " + siteId + " AND c.status != -1 "
	);
	if(!info.next()) {
		result.put("rst_code", "9999");
		result.put("rst_message", "과목 정보를 찾지 못했습니다.");
		result.print();
		return;
	}

	String ss = info.s("study_sdate");
	String se = info.s("study_edate");
	String periodConv = (!"".equals(ss) && !"".equals(se)) ? (m.time("yyyy.MM.dd", ss) + " - " + m.time("yyyy.MM.dd", se)) : "상시";
	String statusLabel = "대기";
	if(!"".equals(ss) && !"".equals(se)) {
		if(0 <= m.diffDate("D", ss, today) && 0 <= m.diffDate("D", today, se)) statusLabel = "학습기간";
		else if(0 < m.diffDate("D", se, today)) statusLabel = "종료";
	}

	resultList.put("id", info.s("id"));
	resultList.put("course_cd", info.s("course_cd"));
	resultList.put("course_id_conv", !"".equals(info.s("course_cd")) ? info.s("course_cd") : info.s("id"));
	resultList.put("course_nm", info.s("course_nm"));
	resultList.put("course_nm_conv", info.s("course_nm"));
	resultList.put("course_type", info.s("course_type"));
	resultList.put("onoff_type", info.s("onoff_type"));
	resultList.put("onoff_type_conv", m.getItem(info.s("onoff_type"), course.onoffTypes));
	resultList.put("program_nm_conv", !"".equals(info.s("program_nm")) ? info.s("program_nm") : "-");
	resultList.put("period_conv", periodConv);
	resultList.put("status_label", statusLabel);
	resultList.put("student_cnt",
		courseUser.getOneInt("SELECT COUNT(*) FROM " + courseUser.table + " cu WHERE cu.course_id = " + courseId + " AND cu.status IN (1,3)")
	);
	resultList.put("source_type", "prism");
} else {
	// 학사: LMS 과정에 기록된 etc1(학사 키) 기준으로 뷰/미러 테이블을 찾습니다.
	String haksaKey = courseInfo.s("etc1");
	if("".equals(haksaKey)) {
		result.put("rst_code", "9999");
		result.put("rst_message", "학사 과목 키가 비어 있습니다.");
		result.print();
		return;
	}
	String[] parts = haksaKey.split("_");
	if(parts.length < 5) {
		result.put("rst_code", "9999");
		result.put("rst_message", "학사 과목 키 형식이 올바르지 않습니다.");
		result.print();
		return;
	}
	String courseCode = parts.length > 0 ? parts[0] : "";
	String openYear = parts.length > 1 ? parts[1] : "";
	String openTerm = parts.length > 2 ? parts[2] : "";
	String bunbanCode = parts.length > 3 ? parts[3] : "";
	String groupCode = parts.length > 4 ? parts[4] : "";

	DataSet hinfo = polyCourse.find(
		"course_code = '" + m.replace(courseCode, "'", "''") + "'"
		+ " AND open_year = '" + m.replace(openYear, "'", "''") + "'"
		+ " AND open_term = '" + m.replace(openTerm, "'", "''") + "'"
		+ " AND bunban_code = '" + m.replace(bunbanCode, "'", "''") + "'"
		+ " AND group_code = '" + m.replace(groupCode, "'", "''") + "'"
	);

	String category = "";
	String deptName = "";
	String week = "";
	String visible = "";
	String startdate = "";
	String grade = "";
	String gradName = "";
	String dayCd = "";
	String classroom = "";
	String curriculumCode = "";
	String courseEname = "";
	String typeSyllabus = "";
	String deptCode = "";
	String courseName = "";
	String enddate = "";
	String english = "";
	String hour1 = "";
	String curriculumName = "";
	String gradCode = "";
	String isSyllabus = "";

	if(hinfo.next()) {
		category = !"".equals(hinfo.s("CATEGORY")) ? hinfo.s("CATEGORY") : hinfo.s("category");
		deptName = !"".equals(hinfo.s("DEPT_NAME")) ? hinfo.s("DEPT_NAME") : hinfo.s("dept_name");
		week = !"".equals(hinfo.s("WEEK")) ? hinfo.s("WEEK") : hinfo.s("week");
		visible = !"".equals(hinfo.s("VISIBLE")) ? hinfo.s("VISIBLE") : hinfo.s("visible");
		startdate = !"".equals(hinfo.s("STARTDATE")) ? hinfo.s("STARTDATE") : hinfo.s("startdate");
		grade = !"".equals(hinfo.s("GRADE")) ? hinfo.s("GRADE") : hinfo.s("grade");
		gradName = !"".equals(hinfo.s("GRAD_NAME")) ? hinfo.s("GRAD_NAME") : hinfo.s("grad_name");
		dayCd = !"".equals(hinfo.s("DAY_CD")) ? hinfo.s("DAY_CD") : hinfo.s("day_cd");
		classroom = !"".equals(hinfo.s("CLASSROOM")) ? hinfo.s("CLASSROOM") : hinfo.s("classroom");
		curriculumCode = !"".equals(hinfo.s("CURRICULUM_CODE")) ? hinfo.s("CURRICULUM_CODE") : hinfo.s("curriculum_code");
		courseEname = !"".equals(hinfo.s("COURSE_ENAME")) ? hinfo.s("COURSE_ENAME") : hinfo.s("course_ename");
		typeSyllabus = !"".equals(hinfo.s("TYPE_SYLLABUS")) ? hinfo.s("TYPE_SYLLABUS") : hinfo.s("type_syllabus");
		deptCode = !"".equals(hinfo.s("DEPT_CODE")) ? hinfo.s("DEPT_CODE") : hinfo.s("dept_code");
		courseName = !"".equals(hinfo.s("COURSE_NAME")) ? hinfo.s("COURSE_NAME") : hinfo.s("course_name");
		enddate = !"".equals(hinfo.s("ENDDATE")) ? hinfo.s("ENDDATE") : hinfo.s("enddate");
		english = !"".equals(hinfo.s("ENGLISH")) ? hinfo.s("ENGLISH") : hinfo.s("english");
		hour1 = !"".equals(hinfo.s("HOUR1")) ? hinfo.s("HOUR1") : hinfo.s("hour1");
		curriculumName = !"".equals(hinfo.s("CURRICULUM_NAME")) ? hinfo.s("CURRICULUM_NAME") : hinfo.s("curriculum_name");
		gradCode = !"".equals(hinfo.s("GRAD_CODE")) ? hinfo.s("GRAD_CODE") : hinfo.s("grad_code");
		isSyllabus = !"".equals(hinfo.s("IS_SYLLABUS")) ? hinfo.s("IS_SYLLABUS") : hinfo.s("is_syllabus");
	}

	if("".equals(courseName)) courseName = courseInfo.s("course_nm");

	resultList.put("source_type", "haksa");
	resultList.put("id", "H_" + courseCode + "_" + openYear + "_" + openTerm + "_" + bunbanCode + "_" + groupCode);
	resultList.put("mapped_course_id", courseId);
	resultList.put("course_cd", courseCode);
	resultList.put("course_id_conv", courseCode);
	resultList.put("course_nm", courseName);
	resultList.put("course_nm_conv", courseName);
	resultList.put("program_nm_conv", !"".equals(deptName) ? deptName : "-");
	resultList.put("course_type_conv", !"".equals(category) ? category : "-");
	resultList.put("onoff_type_conv", "학사");
	resultList.put("period_conv", !"".equals(openYear) ? (openYear + "-" + openTerm + "학기") : "-");
	resultList.put("status_label", "Y".equals(visible) ? "학습기간" : "종료");
	resultList.put("student_cnt",
		polyStudent.getOneInt(
			"SELECT COUNT(*) FROM " + polyStudent.table
			+ " WHERE course_code = '" + m.replace(courseCode, "'", "''") + "'"
			+ " AND open_year = '" + m.replace(openYear, "'", "''") + "'"
			+ " AND open_term = '" + m.replace(openTerm, "'", "''") + "'"
			+ " AND bunban_code = '" + m.replace(bunbanCode, "'", "''") + "'"
			+ " AND group_code = '" + m.replace(groupCode, "'", "''") + "'"
		)
	);

	// 학사 View 25개 필드
	resultList.put("haksa_category", category);
	resultList.put("haksa_dept_name", deptName);
	resultList.put("haksa_week", week);
	resultList.put("haksa_open_term", openTerm);
	resultList.put("haksa_course_code", courseCode);
	resultList.put("haksa_visible", visible);
	resultList.put("haksa_startdate", startdate);
	resultList.put("haksa_bunban_code", bunbanCode);
	resultList.put("haksa_grade", grade);
	resultList.put("haksa_grad_name", gradName);
	resultList.put("haksa_day_cd", dayCd);
	resultList.put("haksa_classroom", classroom);
	resultList.put("haksa_curriculum_code", curriculumCode);
	resultList.put("haksa_course_ename", courseEname);
	resultList.put("haksa_type_syllabus", typeSyllabus);
	resultList.put("haksa_open_year", openYear);
	resultList.put("haksa_dept_code", deptCode);
	resultList.put("haksa_course_name", courseName);
	resultList.put("haksa_group_code", groupCode);
	resultList.put("haksa_enddate", enddate);
	resultList.put("haksa_english", english);
	resultList.put("haksa_hour1", hour1);
	resultList.put("haksa_curriculum_name", curriculumName);
	resultList.put("haksa_grad_code", gradCode);
	resultList.put("haksa_is_syllabus", isSyllabus);
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", resultList);
result.print();

%>
