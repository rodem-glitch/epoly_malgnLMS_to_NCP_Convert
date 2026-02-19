<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%!
private String getHaksaOpenTermLabel(String openTerm) {
	// 왜: 학사 open_term은 코드(10/11/20/21/30/1)로 내려오므로,
	//     담당과목 화면 기간 표시는 사람이 읽는 학기명으로 변환해야 합니다.
	if("10".equals(openTerm) || "1".equals(openTerm)) return "1학기";
	if("11".equals(openTerm)) return "여름학기";
	if("20".equals(openTerm) || "2".equals(openTerm)) return "2학기";
	if("21".equals(openTerm)) return "겨울학기";
	if("30".equals(openTerm)) return "기타";
	if("".equals(openTerm)) return "";
	// 왜: 미정의 코드가 들어오면 숨기지 않고 원본을 그대로 노출해
	//     운영 데이터 이상을 확인할 수 있게 합니다.
	return openTerm;
}
%><%

//왜 필요한가:
//- 담당과목 화면에서 "학사 탭"과 "프리즘 탭"을 분리하여 보여주기 위함입니다.
//- 프리즘 탭은 관리자 과정운영 기준(43건)으로 맞추고, 학사 탭은 폴리텍 뷰테이블 전체를 보여줍니다.

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();
SubjectDao subject = new SubjectDao();
UserDao user = new UserDao();
PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();
PolyCourseProfDao polyCourseProf = new PolyCourseProfDao();

// 왜: 교수자는 본인 담당(주/보조강사) 과목만 보이게 제한해야 합니다.
String prismWhere = "";
String haksaWhere = "";
java.util.HashSet<String> haksaKeySet = new java.util.HashSet<String>();
if(!isAdmin) {
	prismWhere = " AND (c.manager_id = " + userId
		+ " OR EXISTS (SELECT 1 FROM " + courseTutor.table + " ct "
		+ " WHERE ct.course_id = c.id AND ct.user_id = " + userId + " AND ct.site_id = " + siteId
		+ " AND ct.type IN ('major','minor')) "
		+ " OR EXISTS (SELECT 1 FROM " + courseManager.table + " cm "
		+ " WHERE cm.course_id = c.id AND cm.user_id = " + userId + " AND cm.site_id = " + siteId + ")) ";

	// 왜: 교수자의 학사 과목은 LM_POLY_COURSE_PROF 기준으로 필터링해야 일관되게 보입니다.
	//     또한 login_id ↔ member_key 매핑이 섞여 있으므로 둘 다 시도해 안전하게 키를 해석합니다.
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

	// 왜: 미러가 없어도(실시간 조회) 교수자 본인 과목만 보이게 하기 위해
	//     LM_POLY_COURSE_PROF에서 교수자의 학사 키 목록을 미리 뽑아둡니다.
	try {
		DataSet haksaKeys = polyCourseProf.query(
			"SELECT CONCAT(course_code, '_', open_year, '_', open_term, '_', bunban_code, '_', group_code) haksa_key "
			+ " FROM " + polyCourseProf.table
			+ " WHERE member_key = '" + safeResolvedMemberKey + "' "
		);
		while(haksaKeys.next()) {
			haksaKeySet.add(haksaKeys.s("haksa_key"));
		}
	} catch(Exception ignore) {}

	// 왜: 운영 중에는 학사 교수 매핑(LM_POLY_COURSE_PROF) 동기화 시차가 생길 수 있어,
	//     LMS에서 이미 담당자로 연결된 학사 과정(etc1 키 기반)도 정규 탭에 같이 보여야 "내 과목이 안 보임"을 막을 수 있습니다.
	try {
		DataSet mappedHaksaKeys = course.query(
			"SELECT lc.etc1 haksa_key "
			+ " FROM " + course.table + " lc "
			+ " LEFT JOIN " + courseTutor.table + " lct ON lct.course_id = lc.id AND lct.site_id = " + siteId
				+ " AND lct.user_id = " + userId + " AND lct.type IN ('major','minor') "
			+ " LEFT JOIN " + courseManager.table + " lcm ON lcm.course_id = lc.id AND lcm.site_id = " + siteId
				+ " AND lcm.user_id = " + userId + " "
			+ " WHERE lc.site_id = " + siteId + " AND lc.status != -1 "
			+ " AND lc.etc2 = 'HAKSA_MAPPED' AND lc.etc1 IS NOT NULL AND lc.etc1 != '' "
			+ " AND (lc.manager_id = " + userId + " OR lct.user_id IS NOT NULL OR lcm.user_id IS NOT NULL) "
		);
		while(mappedHaksaKeys.next()) {
			haksaKeySet.add(mappedHaksaKeys.s("haksa_key"));
		}
	} catch(Exception ignore) {}

	// 왜: 테이블별 collation 차이로 EXISTS 비교가 실패할 수 있어,
	//     교수자 기준 학사키 집합을 먼저 만든 뒤, 미러 테이블에서는 동일 키(IN)로만 필터링합니다.
	if(0 < haksaKeySet.size()) {
		StringBuilder haksaIn = new StringBuilder();
		for(String hk : haksaKeySet) {
			if(haksaIn.length() > 0) haksaIn.append(",");
			haksaIn.append("'").append(m.replace(hk, "'", "''")).append("'");
		}
		haksaWhere = " AND CONCAT(c.course_code, '_', c.open_year, '_', c.open_term, '_', c.bunban_code, '_', c.group_code) IN (" + haksaIn.toString() + ") ";
	} else {
		haksaWhere = " AND 1 = 0 ";
	}

	m.log(
		"course_list_combined",
		"haksa_filter_keys user_id=" + userId
		+ ", site_id=" + siteId
		+ ", key_count=" + haksaKeySet.size()
	);
}

String tab = m.rs("tab"); // "prism" 또는 "haksa"
if("".equals(tab)) tab = "prism"; // 기본값: 프리즘

String year = m.rs("year");
String keyword = m.rs("s_keyword");
String haksaCategory = m.rs("haksa_category");
String haksaGrad = m.rs("haksa_grad");
String haksaCurriculum = m.rs("haksa_curriculum");
String sortOrder = m.rs("sort_order");
if(!"asc".equals(sortOrder)) sortOrder = "desc";
String today = m.time("yyyyMMdd");
int pageNo = m.ri("page", 1);
int pageSize = m.ri("page_size", 20);
if(pageNo < 1) pageNo = 1;
if(pageSize != 20 && pageSize != 50 && pageSize != 100) pageSize = 20;
int offset = (pageNo - 1) * pageSize;

DataSet resultList = new DataSet();
String message = "";
int totalCount = 0;

//==============================================================================
// 프리즘 탭: 관리자 과정운영 기준 (43건 맞춤)
//==============================================================================
if("prism".equals(tab)) {
    String where = "";
    ArrayList<Object> params = new ArrayList<Object>();

    if(!"".equals(year) && !"전체".equals(year)) {
        where += " AND c.year = ? ";
        params.add(year);
    }
    if(!"".equals(keyword)) {
        where += " AND (c.course_nm LIKE ? OR s.course_nm LIKE ?) ";
        params.add("%" + keyword + "%");
        params.add("%" + keyword + "%");
    }

    // 왜: 담당과목(교수자) 화면은 "관리/운영" 목적이라, 과정 노출여부(display_yn)와 무관하게
    //     본인이 담당한 과목은 항상 보여야 합니다.
    //     특히 과정 복사 로직에서 display_yn이 'N'으로 저장되는 케이스가 있어,
    //     display_yn = 'Y'로 제한하면 교수자 과목이 누락될 수 있습니다.
    try {
        String baseSql =
            " FROM " + course.table + " c "
            + " LEFT JOIN " + subject.table + " s ON s.id = c.subject_id AND s.status != -1 "
            + " WHERE c.site_id = " + siteId + " AND c.status != -1 AND c.onoff_type != 'P' "
            + prismWhere
            + where;

        totalCount = course.getOneInt("SELECT COUNT(*) " + baseSql, params.toArray());

        ArrayList<Object> pageParams = new ArrayList<Object>(params);
        pageParams.add(offset);
        pageParams.add(pageSize);

        resultList = course.query(
            " SELECT c.id, c.course_cd, c.course_nm, c.course_type, c.onoff_type, c.year, c.status, c.display_yn "
            + " , c.study_sdate, c.study_edate, c.request_sdate, c.request_edate "
            + " , s.course_nm program_nm "
            + " , (SELECT COUNT(*) FROM " + courseUser.table + " cu WHERE cu.course_id = c.id AND cu.status IN (1,3)) student_cnt "
            + baseSql
            + " ORDER BY c.id DESC "
            + " LIMIT ?, ? "
            , pageParams.toArray()
        );
    } catch(Exception e) {
        message = "[PErr:" + e.getMessage() + "]";
    }

    // 데이터 정규화
    resultList.first();
    while(resultList.next()) {
        resultList.put("source_type", "prism");
        resultList.put("course_nm_conv", resultList.s("course_nm"));
        resultList.put("onoff_type_conv", m.getItem(resultList.s("onoff_type"), course.onoffTypes));
        resultList.put("program_nm_conv", !"".equals(resultList.s("program_nm")) ? resultList.s("program_nm") : "-");
        
        String ss = resultList.s("study_sdate");
        String se = resultList.s("study_edate");
        resultList.put("period_conv", (!"".equals(ss) && !"".equals(se)) ? (m.time("yyyy.MM.dd", ss) + " - " + m.time("yyyy.MM.dd", se)) : "상시");
        
        String statusLabel = "대기";
        if(!"".equals(ss) && !"".equals(se)) {
            if(0 <= m.diffDate("D", ss, today) && 0 <= m.diffDate("D", today, se)) statusLabel = "학습기간";
            else if(0 < m.diffDate("D", se, today)) statusLabel = "종료";
        }
        resultList.put("status_label", statusLabel);
    }
    
    message = "성공 (프리즘:" + totalCount + "건)" + message;
}

//==============================================================================
    // 학사 탭: 폴리텍 COM.LMS_COURSE_VIEW 테이블
//==============================================================================
else if("haksa".equals(tab)) {
    // 왜: 학사(View) 데이터는 cnt 제한 때문에 실시간 조회 시 누락이 생길 수 있어,
    //     별도 동기화(`public_html/main/poly_sync.jsp`)로 우리 DB에 저장된 미러 테이블을 조회합니다.
    PolyCourseDao polyCourse = new PolyCourseDao();
    PolyStudentDao polyStudent = new PolyStudentDao();
    PolySyncLogDao polySyncLog = new PolySyncLogDao();

    int mirrorCount = 0;
    try { mirrorCount = polyCourse.findCount("1 = 1"); } catch(Exception ignore) {}

    // 미러 데이터가 없으면(초기 구축 전) 기존 실시간 조회로 한 번 보여주되, 안내 메시지를 같이 내려줍니다.
    if(mirrorCount <= 0) {
        message = "학사 데이터가 아직 동기화되지 않았습니다. /main/poly_sync.jsp를 먼저 실행해 주세요.";

        malgnsoft.util.Http http = new malgnsoft.util.Http("https://e-poly.kopo.ac.kr/main/vpn_test.jsp");
        http.setParam("tb", "COM.LMS_COURSE_VIEW");
        http.setParam("cnt", "500");
        String jsonRaw = http.send("POST");

        if(jsonRaw != null && !jsonRaw.trim().equals("")) {
            // 왜: 응답이 <pre>로 감싸져 오는 경우가 있어, 내부만 추출합니다.
            String body = jsonRaw;
            int ps = jsonRaw.indexOf("<pre");
            if(ps >= 0) {
                int gt = jsonRaw.indexOf(">", ps);
                int pe = jsonRaw.indexOf("</pre>", gt);
                if(gt >= 0 && pe >= 0) body = jsonRaw.substring(gt + 1, pe);
            }

            String trimmed = body.trim();
            if(trimmed.startsWith("[")) {
                try { resultList = malgnsoft.util.Json.decode(trimmed); } catch(Exception ignore) {}
            } else {
                String[] lines = body.split("\n");
                java.util.Map<String, String> currentRow = new java.util.HashMap<String, String>();
                for(String line : lines) {
                    if(line.contains(":") && !line.startsWith("--")) {
                        int sep = line.indexOf(":");
                        String key = line.substring(0, sep).trim();
                        String val = line.substring(sep + 1).trim().replaceAll(",\\s*$", "");
                        if(!"".equals(key)) {
                            if(currentRow.containsKey(key)) { resultList.addRow(currentRow); currentRow = new java.util.HashMap<String, String>(); }
                            currentRow.put(key, val);
                        }
                    }
                }
                if(!currentRow.isEmpty()) resultList.addRow(currentRow);
            }
        }
        totalCount = resultList.size();

        // 왜: 미러가 없어도 학사 키(코드/연도/학기/분반/그룹)를 채워 내려야 화면 저장/조회가 동작합니다.
        resultList.first();
        DataSet filtered = new DataSet();
        while(resultList.next()) {
            // ===== LMS_COURSE_VIEW 25개 필드 정규화 (대소문자 모두 처리) =====
            String category = resultList.s("CATEGORY");
            if("".equals(category)) category = resultList.s("category");
            resultList.put("haksa_category", category);

            String deptName = resultList.s("DEPT_NAME");
            if("".equals(deptName)) deptName = resultList.s("dept_name");
            resultList.put("haksa_dept_name", deptName);

            String week = resultList.s("WEEK");
            if("".equals(week)) week = resultList.s("week");
            resultList.put("haksa_week", week);

            String openTerm = resultList.s("OPEN_TERM");
            if("".equals(openTerm)) openTerm = resultList.s("open_term");
            resultList.put("haksa_open_term", openTerm);

            String courseCode = resultList.s("COURSE_CODE");
            if("".equals(courseCode)) courseCode = resultList.s("course_code");
            resultList.put("haksa_course_code", courseCode);

            String visible = resultList.s("VISIBLE");
            if("".equals(visible)) visible = resultList.s("visible");
            resultList.put("haksa_visible", visible);

            String startdate = resultList.s("STARTDATE");
            if("".equals(startdate)) startdate = resultList.s("startdate");
            resultList.put("haksa_startdate", startdate);

            String bunbanCode = resultList.s("BUNBAN_CODE");
            if("".equals(bunbanCode)) bunbanCode = resultList.s("bunban_code");
            resultList.put("haksa_bunban_code", bunbanCode);

            String grade = resultList.s("GRADE");
            if("".equals(grade)) grade = resultList.s("grade");
            resultList.put("haksa_grade", grade);

            String gradName = resultList.s("GRAD_NAME");
            if("".equals(gradName)) gradName = resultList.s("grad_name");
            resultList.put("haksa_grad_name", gradName);

            String dayCd = resultList.s("DAY_CD");
            if("".equals(dayCd)) dayCd = resultList.s("day_cd");
            resultList.put("haksa_day_cd", dayCd);

            String classroom = resultList.s("CLASSROOM");
            if("".equals(classroom)) classroom = resultList.s("classroom");
            resultList.put("haksa_classroom", classroom);

            String curriculumCode = resultList.s("CURRICULUM_CODE");
            if("".equals(curriculumCode)) curriculumCode = resultList.s("curriculum_code");
            resultList.put("haksa_curriculum_code", curriculumCode);

            String courseEname = resultList.s("COURSE_ENAME");
            if("".equals(courseEname)) courseEname = resultList.s("course_ename");
            resultList.put("haksa_course_ename", courseEname);

            String typeSyllabus = resultList.s("TYPE_SYLLABUS");
            if("".equals(typeSyllabus)) typeSyllabus = resultList.s("type_syllabus");
            resultList.put("haksa_type_syllabus", typeSyllabus);

            String openYear = resultList.s("OPEN_YEAR");
            if("".equals(openYear)) openYear = resultList.s("open_year");
            resultList.put("haksa_open_year", openYear);

            String deptCode = resultList.s("DEPT_CODE");
            if("".equals(deptCode)) deptCode = resultList.s("dept_code");
            resultList.put("haksa_dept_code", deptCode);

            String courseName = resultList.s("COURSE_NAME");
            if("".equals(courseName)) courseName = resultList.s("course_name");
            resultList.put("haksa_course_name", courseName);

            String groupCode = resultList.s("GROUP_CODE");
            if("".equals(groupCode)) groupCode = resultList.s("group_code");
            resultList.put("haksa_group_code", groupCode);

            String enddate = resultList.s("ENDDATE");
            if("".equals(enddate)) enddate = resultList.s("enddate");
            resultList.put("haksa_enddate", enddate);

            String english = resultList.s("ENGLISH");
            if("".equals(english)) english = resultList.s("english");
            resultList.put("haksa_english", english);

            String hour1 = resultList.s("HOUR1");
            if("".equals(hour1)) hour1 = resultList.s("hour1");
            resultList.put("haksa_hour1", hour1);

            String curriculumName = resultList.s("CURRICULUM_NAME");
            if("".equals(curriculumName)) curriculumName = resultList.s("curriculum_name");
            resultList.put("haksa_curriculum_name", curriculumName);

            String gradCode = resultList.s("GRAD_CODE");
            if("".equals(gradCode)) gradCode = resultList.s("grad_code");
            resultList.put("haksa_grad_code", gradCode);

            String isSyllabus = resultList.s("IS_SYLLABUS");
            if("".equals(isSyllabus)) isSyllabus = resultList.s("is_syllabus");
            resultList.put("haksa_is_syllabus", isSyllabus);

            // ===== 기존 호환 필드 (목록 화면용) =====
            resultList.put("source_type", "haksa");
            // 왜: 학사 과목은 (강좌코드/연도/학기/분반/그룹) 5종 키로 식별됩니다.
            //     기존에는 course_code + bunban_code만으로 id를 만들어, 연도/학기 등이 다른 과목이 같은 id가 되어
            //     화면(React)에서 행 key가 충돌하고 "다른 행을 눌렀는데 엉뚱한 과목이 열리는" 문제가 생길 수 있습니다.
            resultList.put("id", "H_" + courseCode + "_" + openYear + "_" + openTerm + "_" + bunbanCode + "_" + groupCode);
            resultList.put("course_cd", courseCode);
            resultList.put("course_id_conv", courseCode);
            resultList.put("course_nm", courseName);
            resultList.put("course_nm_conv", courseName);
            resultList.put("program_nm_conv", !"".equals(deptName) ? deptName : "-");
            resultList.put("course_type_conv", !"".equals(category) ? category : "-");
            resultList.put("onoff_type_conv", "학사");
            String openTermLabel = getHaksaOpenTermLabel(openTerm);
            resultList.put("haksa_open_term_conv", openTermLabel);
            resultList.put("period_conv", (!"".equals(openYear) && !"".equals(openTermLabel)) ? (openYear + "-" + openTermLabel) : "-");
            resultList.put("status_label", "Y".equals(visible) ? "학습기간" : "종료");

            // 왜: 교수자는 본인 매핑된 학사 과목만 보이도록 필터링합니다.
            if(!isAdmin) {
                String haksaKey = courseCode + "_" + openYear + "_" + openTerm + "_" + bunbanCode + "_" + groupCode;
                if(!haksaKeySet.contains(haksaKey)) continue;
            }
            filtered.addRow(resultList.getRow());
        }
        if(!isAdmin) {
            resultList = filtered;
            totalCount = resultList.size();
        }
    } else {
        String where = " WHERE 1 = 1 ";
        ArrayList<Object> params = new ArrayList<Object>();

        if(!"".equals(year) && !"전체".equals(year)) {
            where += " AND c.open_year = ? ";
            params.add(year);
        }
        if(!"".equals(keyword)) {
            where += " AND (c.course_name LIKE ? OR c.dept_name LIKE ? OR c.course_code LIKE ?) ";
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if(!"".equals(haksaCategory) && !"전체".equals(haksaCategory)) {
            // 왜: 학사 유형은 대소문자 혼재가 있어 비교 시 소문자로 통일합니다.
            where += " AND LOWER(c.category) = ? ";
            params.add(haksaCategory.toLowerCase());
        }
        if(!"".equals(haksaGrad) && !"전체".equals(haksaGrad)) {
            where += " AND c.grad_name LIKE ? ";
            params.add("%" + haksaGrad + "%");
        }
        if(!"".equals(haksaCurriculum) && !"전체".equals(haksaCurriculum)) {
            where += " AND c.curriculum_name = ? ";
            params.add(haksaCurriculum);
        }

        try {
            String baseSql = " FROM " + polyCourse.table + " c " + where + haksaWhere;
            totalCount = polyCourse.getOneInt("SELECT COUNT(*) " + baseSql, params.toArray());

            ArrayList<Object> pageParams = new ArrayList<Object>(params);
            pageParams.add(offset);
            pageParams.add(pageSize);

            resultList = polyCourse.query(
                " SELECT c.* "
                + " , (SELECT COUNT(*) FROM " + polyStudent.table + " s "
                    + " WHERE s.course_code = c.course_code "
            + " AND s.open_year = c.open_year AND s.open_term = c.open_term "
            + " AND s.bunban_code = c.bunban_code AND s.group_code = c.group_code "
        + " ) student_cnt "
        + baseSql
        + " ORDER BY c.open_year DESC, c.open_term DESC, c.course_code " + ("asc".equals(sortOrder) ? "ASC" : "DESC") + " "
        + " LIMIT ?, ? "
        , pageParams.toArray()
    );
        } catch(Exception e) {
            message = "[HErr:" + e.getMessage() + "]";
            resultList = new DataSet();
        }
    }

    // 학사 데이터 정규화 (LMS_COURSE_VIEW 25개 필드 전체 매핑)
    resultList.first();
    while(resultList.next()) {
        resultList.put("source_type", "haksa");
        
        // ===== LMS_COURSE_VIEW 25개 필드 정규화 (대소문자 모두 처리) =====
        // 1. CATEGORY - 강좌형태
        String category = resultList.s("CATEGORY");
        if("".equals(category)) category = resultList.s("category");
        resultList.put("haksa_category", category);
        
        // 2. DEPT_NAME - 학과/전공 이름
        String deptName = resultList.s("DEPT_NAME");
        if("".equals(deptName)) deptName = resultList.s("dept_name");
        resultList.put("haksa_dept_name", deptName);
        
        // 3. WEEK - 주차
        String week = resultList.s("WEEK");
        if("".equals(week)) week = resultList.s("week");
        resultList.put("haksa_week", week);
        
        // 4. OPEN_TERM - 학기
        String openTerm = resultList.s("OPEN_TERM");
        if("".equals(openTerm)) openTerm = resultList.s("open_term");
        resultList.put("haksa_open_term", openTerm);
        
        // 5. COURSE_CODE - 강좌코드
        String courseCode = resultList.s("COURSE_CODE");
        if("".equals(courseCode)) courseCode = resultList.s("course_code");
        resultList.put("haksa_course_code", courseCode);
        
        // 6. VISIBLE - 강좌 폐강 여부
        String visible = resultList.s("VISIBLE");
        if("".equals(visible)) visible = resultList.s("visible");
        resultList.put("haksa_visible", visible);
        
        // 7. STARTDATE - 강좌시작일
        String startdate = resultList.s("STARTDATE");
        if("".equals(startdate)) startdate = resultList.s("startdate");
        resultList.put("haksa_startdate", startdate);
        
        // 8. BUNBAN_CODE - 분반코드
        String bunbanCode = resultList.s("BUNBAN_CODE");
        if("".equals(bunbanCode)) bunbanCode = resultList.s("bunban_code");
        resultList.put("haksa_bunban_code", bunbanCode);
        
        // 9. GRADE - 학년
        String grade = resultList.s("GRADE");
        if("".equals(grade)) grade = resultList.s("grade");
        resultList.put("haksa_grade", grade);
        
        // 10. GRAD_NAME - 단과대학 이름
        String gradName = resultList.s("GRAD_NAME");
        if("".equals(gradName)) gradName = resultList.s("grad_name");
        resultList.put("haksa_grad_name", gradName);
        
        // 11. DAY_CD - 강의 요일
        String dayCd = resultList.s("DAY_CD");
        if("".equals(dayCd)) dayCd = resultList.s("day_cd");
        resultList.put("haksa_day_cd", dayCd);
        
        // 12. CLASSROOM - 강의실 정보
        String classroom = resultList.s("CLASSROOM");
        if("".equals(classroom)) classroom = resultList.s("classroom");
        resultList.put("haksa_classroom", classroom);
        
        // 13. CURRICULUM_CODE - 과목구분 코드
        String curriculumCode = resultList.s("CURRICULUM_CODE");
        if("".equals(curriculumCode)) curriculumCode = resultList.s("curriculum_code");
        resultList.put("haksa_curriculum_code", curriculumCode);
        
        // 14. COURSE_ENAME - 강좌명(영문)
        String courseEname = resultList.s("COURSE_ENAME");
        if("".equals(courseEname)) courseEname = resultList.s("course_ename");
        resultList.put("haksa_course_ename", courseEname);
        
        // 15. TYPE_SYLLABUS - 강의계획서 구분
        String typeSyllabus = resultList.s("TYPE_SYLLABUS");
        if("".equals(typeSyllabus)) typeSyllabus = resultList.s("type_syllabus");
        resultList.put("haksa_type_syllabus", typeSyllabus);
        
        // 16. OPEN_YEAR - 연도
        String openYear = resultList.s("OPEN_YEAR");
        if("".equals(openYear)) openYear = resultList.s("open_year");
        resultList.put("haksa_open_year", openYear);
        
        // 17. DEPT_CODE - 학과/전공 코드
        String deptCode = resultList.s("DEPT_CODE");
        if("".equals(deptCode)) deptCode = resultList.s("dept_code");
        resultList.put("haksa_dept_code", deptCode);
        
        // 18. COURSE_NAME - 강좌명(한글)
        String courseName = resultList.s("COURSE_NAME");
        if("".equals(courseName)) courseName = resultList.s("course_name");
        resultList.put("haksa_course_name", courseName);
        
        // 19. GROUP_CODE - 학부/대학원 구분
        String groupCode = resultList.s("GROUP_CODE");
        if("".equals(groupCode)) groupCode = resultList.s("group_code");
        resultList.put("haksa_group_code", groupCode);
        
        // 20. ENDDATE - 강좌종료일
        String enddate = resultList.s("ENDDATE");
        if("".equals(enddate)) enddate = resultList.s("enddate");
        resultList.put("haksa_enddate", enddate);
        
        // 21. ENGLISH - 영문 강좌 여부
        String english = resultList.s("ENGLISH");
        if("".equals(english)) english = resultList.s("english");
        resultList.put("haksa_english", english);
        
        // 22. HOUR1 - 강의 시간
        String hour1 = resultList.s("HOUR1");
        if("".equals(hour1)) hour1 = resultList.s("hour1");
        resultList.put("haksa_hour1", hour1);
        
        // 23. CURRICULUM_NAME - 과목구분 이름
        String curriculumName = resultList.s("CURRICULUM_NAME");
        if("".equals(curriculumName)) curriculumName = resultList.s("curriculum_name");
        resultList.put("haksa_curriculum_name", curriculumName);
        
        // 24. GRAD_CODE - 단과대학 코드
        String gradCode = resultList.s("GRAD_CODE");
        if("".equals(gradCode)) gradCode = resultList.s("grad_code");
        resultList.put("haksa_grad_code", gradCode);
        
        // 25. IS_SYLLABUS - 강의계획서 존재여부
        String isSyllabus = resultList.s("IS_SYLLABUS");
        if("".equals(isSyllabus)) isSyllabus = resultList.s("is_syllabus");
        resultList.put("haksa_is_syllabus", isSyllabus);
        
        // ===== 기존 호환 필드 (목록 화면용) =====
        // 왜: 학사 과목은 (강좌코드/연도/학기/분반/그룹) 5종 키로 식별됩니다.
        //     목록 행을 안정적으로 구분하기 위해 id도 5종 키를 포함합니다.
        resultList.put("id", "H_" + courseCode + "_" + openYear + "_" + openTerm + "_" + bunbanCode + "_" + groupCode);
        resultList.put("course_cd", courseCode);
        resultList.put("course_id_conv", courseCode);
        resultList.put("course_nm", courseName);
        resultList.put("course_nm_conv", courseName);
        resultList.put("program_nm_conv", !"".equals(deptName) ? deptName : "-");
        resultList.put("course_type_conv", !"".equals(category) ? category : "-");
        resultList.put("onoff_type_conv", "학사");
        String openTermLabel = getHaksaOpenTermLabel(openTerm);
        resultList.put("haksa_open_term_conv", openTermLabel);
        resultList.put("period_conv", (!"".equals(openYear) && !"".equals(openTermLabel)) ? (openYear + "-" + openTermLabel) : "-");
        resultList.put("status_label", "Y".equals(visible) ? "학습기간" : "종료");
        // 왜: 미러 테이블(LM_POLY_STUDENT)에서 과목별 학생 수를 계산해 같이 내려줍니다.
        resultList.put("student_cnt", resultList.i("student_cnt"));
    }

    // 미러 조회가 성공했을 때만 마지막 동기화 시각을 붙입니다.
    if(mirrorCount > 0 && !message.startsWith("[HErr")) {
        String lastSync = "";
        try { lastSync = polySyncLog.getOne("SELECT last_sync_date FROM " + polySyncLog.table + " WHERE sync_key = 'poly_mirror'"); }
        catch(Exception ignore) {}

        message = "성공 (학사:" + totalCount + "건)"
            + (!"".equals(lastSync) ? (" / 동기화:" + lastSync) : "");
    }
}

// 결과 출력
result.put("rst_code", "0000");
result.put("rst_message", message);
result.put("rst_count", resultList.size());
result.put("rst_total_count", totalCount);
result.put("rst_page", pageNo);
result.put("rst_page_size", pageSize);
result.put("rst_data", resultList);
result.print();

%>
