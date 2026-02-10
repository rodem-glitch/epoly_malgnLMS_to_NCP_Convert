<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - "차시 편집"을 누르면 원본 과목을 그대로 수정하지 않고, 복사본을 만들어 안전하게 편집해야 합니다.
// - 복사 시 새 과목명과 담당 교수를 지정할 수 있어야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

if(!isAdmin) {
	result.put("rst_code", "4030");
	result.put("rst_message", "관리자 권한이 필요합니다.");
	result.print();
	return;
}

int sourceCourseId = m.ri("source_course_id");
String courseNm = m.rs("course_nm").trim();
int tutorId = m.ri("tutor_id");

if(0 == sourceCourseId || "".equals(courseNm) || 0 == tutorId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseLessonDao courseLesson = new CourseLessonDao();
CourseSectionDao courseSection = new CourseSectionDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
TutorDao tutor = new TutorDao();

DataSet tinfo = tutor.find("user_id = " + tutorId + " AND site_id = " + siteId + " AND status = 1");
if(!tinfo.next()) {
	result.put("rst_code", "4042");
	result.put("rst_message", "담당 교수/강사 정보를 찾을 수 없습니다.");
	result.print();
	return;
}

DataSet info = course.find("id = " + sourceCourseId + " AND site_id = " + siteId + " AND status != -1");
if(!info.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "원본 과목 정보가 없습니다.");
	result.print();
	return;
}

int newId = 0;
try {
	int year = info.i("year");
	int subjectId = info.i("subject_id");

	// 왜: 동일 연도/과정 내에서 step이 중복되지 않도록 다음 값을 계산합니다.
	int nextStep = 1;
	try {
		int maxStep = course.getOneInt(
			"SELECT MAX(step) FROM " + course.table
			+ " WHERE site_id = " + siteId
			+ " AND status != -1 AND year = '" + year + "'"
			+ (subjectId > 0 ? " AND subject_id = " + subjectId : "")
		);
		if(maxStep > 0) nextStep = maxStep + 1;
	} catch(Exception ignore) {}

	newId = course.getSequence();
	String[] columns = info.getColumns();
	for(int i = 0; i < columns.length; i++) {
		course.item(columns[i], info.s(columns[i]));
	}

	course.item("id", newId);
	course.item("course_nm", courseNm);
	course.item("step", nextStep);
	course.item("course_file", "");
	// 왜: 숫자형 컬럼이 NULL이면 DataSet.s()가 ""로 떨어져서, MySQL strict 모드에서 INSERT가 실패할 수 있습니다.
	//     대표적으로 LM_COURSE.SORT가 NULL인 경우 ""(빈문자열)을 넣으려다 오류가 납니다.
	course.item("sort", info.i("sort"));
	course.item("allsort", info.i("allsort"));
	// 왜: LIMIT_DAY(학습일수 제한)도 숫자 컬럼이므로 동일하게 보정합니다.
	course.item("limit_day", info.i("limit_day"));
	// 왜: 복사본을 목록에서 바로 확인할 수 있어야 합니다.
	course.item("display_yn", "Y");
	course.item("sale_yn", "N");
	course.item("close_yn", "N");
	// 왜: 관리자/강사 변경을 반영하기 위해 담당자를 복사본에 다시 지정합니다.
	course.item("manager_id", tutorId);
	course.item("reg_date", m.time("yyyyMMddHHmmss"));
	course.item("status", 1);

	if(!course.insert()) throw new Exception("과목 복사에 실패했습니다.");

	// 1) 차시(레슨) 먼저 복사
	// 왜: 일부 과목의 tutor_id가 NULL이면 ""로 들어가면서 숫자 컬럼 오류가 나므로, 직접 숫자 보정 후 저장합니다.
	DataSet lessonList = courseLesson.find("course_id = " + sourceCourseId + " AND status != -1");
	String[] lessonColumns = lessonList.getColumns();
	while(lessonList.next()) {
		courseLesson.clear();
		for(int i = 0; i < lessonColumns.length; i++) {
			courseLesson.item(lessonColumns[i], lessonList.s(lessonColumns[i]));
		}
		courseLesson.item("course_id", newId);
		courseLesson.item("tutor_id", lessonList.i("tutor_id"));
		courseLesson.item("host_num", lessonList.i("host_num"));
		if(!courseLesson.insert()) throw new Exception("차시 복사에 실패했습니다.");
	}

	// 2) 섹션 복사 후, 레슨의 section_id 매핑 갱신
	if(!courseSection.copySection(sourceCourseId, newId)) {
		throw new Exception("차시 섹션 복사에 실패했습니다.");
	}

	// 3) 담당 교수/강사 등록
	courseTutor.item("course_id", newId);
	courseTutor.item("user_id", tutorId);
	courseTutor.item("site_id", siteId);
	courseTutor.item("type", "major");
	courseTutor.item("class", "1");
	if(!courseTutor.insert()) throw new Exception("담당 교수 등록에 실패했습니다.");

	// 왜: 관리자 화면의 "과정담당자"와 튜터 담당과목 기준을 맞추기 위해 복사본에도 과정담당자를 함께 등록합니다.
	courseManager.item("course_id", newId);
	courseManager.item("user_id", tutorId);
	courseManager.item("site_id", siteId);
	if(!courseManager.insert()) throw new Exception("과정담당자 등록에 실패했습니다.");

	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_data", newId);
	result.print();
} catch(Exception e) {
	// 왜: 복사 중 오류가 나면 반쪽짜리 과목이 남지 않게 비활성 처리합니다.
	if(newId > 0) {
		course.item("status", -1);
		course.update("id = " + newId + " AND site_id = " + siteId);
	}
	result.put("rst_code", "2000");
	result.put("rst_message", e.getMessage());
	result.print();
}

%>
