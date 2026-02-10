<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- `project` 화면의 "과목개설(CreateSubjectWizard)"은 실제로 LM_COURSE(기수)를 생성해야 합니다.
//- 또한 교수자는 본인 과목만 관리해야 하므로, 생성 시점에 LM_COURSE_TUTOR(주강사)도 같이 등록합니다.
//- (선택) 화면에서 고른 학습자/차시 정보를 함께 저장하면, 개설 직후 바로 운영이 가능합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();
CourseSectionDao courseSection = new CourseSectionDao();
CourseLessonDao courseLesson = new CourseLessonDao();
LessonDao lesson = new LessonDao();
ClBoardDao clBoard = new ClBoardDao(siteId);

//왜: 교수자는 본인 명의로만 생성, 관리자는 특정 교수자(tutor_id) 명의로 생성할 수 있어야 합니다.
int ownerId = userId;
int tutorId = m.ri("tutor_id"); //관리자용(선택)
if(isAdmin && 0 < tutorId) ownerId = tutorId;

//필수값
f.addElement("course_nm", null, "hname:'과목명', required:'Y'");
f.addElement("year", m.time("yyyy"), "hname:'년도', required:'Y'");
f.addElement("study_sdate", null, "hname:'수업시작일', required:'Y'");
f.addElement("study_edate", null, "hname:'학습기한', required:'Y'");

//선택값
f.addElement("program_id", 0, "hname:'소속과정ID'");
//왜: sysop(관리자)과 동일한 LM_CATEGORY를 쓰도록, 선택된 카테고리 ID를 같이 받아 저장합니다.
f.addElement("category_id", 0, "hname:'카테고리ID'");
f.addElement("semester", "", "hname:'학기'");
f.addElement("credit", 0, "hname:'학점'");
f.addElement("lesson_day", 0, "hname:'차시'");
f.addElement("lesson_time", 0, "hname:'시수'");
f.addElement("content1", "", "hname:'과목소개', allowhtml:'Y'");
f.addElement("content2", "", "hname:'학습목표', allowhtml:'Y'");
//왜: 대표이미지는 업로드 후 파일명만 받아 LM_COURSE.COURSE_FILE에 저장합니다. (실제 파일 업로드는 course_image_upload.jsp가 담당)
f.addElement("course_file", "", "hname:'대표이미지 파일명'");

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

String courseNm = f.get("course_nm").trim();
String year = f.get("year").trim();
String studySdate = m.time("yyyyMMdd", f.get("study_sdate"));
String studyEdate = m.time("yyyyMMdd", f.get("study_edate"));
int programId = f.getInt("program_id");
int categoryId = f.getInt("category_id");
double lessonTime = f.getDouble("lesson_time");
if(lessonTime <= 0) {
	result.put("rst_code", "1004");
	result.put("rst_message", "시수는 1 이상으로 입력해 주세요.");
	result.print();
	return;
}

// 왜: 학사 정규 과목 개설은 관리자 화면처럼 차시/학점을 같이 저장해야 DB 제약과 충돌하지 않습니다.
//     차시가 비어오면(프론트 미전달) 교수자가 입력한 시수를 차시로 동일 적용합니다.
int lessonDay = f.getInt("lesson_day");
if(lessonDay <= 0) lessonDay = (int)Math.ceil(lessonTime);

// 왜: 학사 정규 기본 학점은 2학점으로 운영 요청이 있어, 미입력 시 2로 고정합니다.
int credit = f.getInt("credit");
if(credit <= 0) credit = 2;

// 왜: DB 컬럼 길이/형식 제약과 화면 입력값이 다르면 INSERT 단계에서만 실패해 원인 파악이 어려워집니다.
//     그래서 저장 전에 형식을 먼저 막아, 사용자에게 정확한 이유를 바로 안내합니다.
if(courseNm.length() > 255) {
	result.put("rst_code", "1001");
	result.put("rst_message", "과목명은 255자 이내로 입력해 주세요.");
	result.print();
	return;
}
if(!year.matches("\\d{4}")) {
	result.put("rst_code", "1002");
	result.put("rst_message", "년도는 4자리 숫자(예: 2026)로 입력해 주세요.");
	result.print();
	return;
}
if(!studySdate.matches("\\d{8}") || !studyEdate.matches("\\d{8}")) {
	result.put("rst_code", "1003");
	result.put("rst_message", "수업 기간 날짜 형식이 올바르지 않습니다.");
	result.print();
	return;
}

//왜: 다른 사이트/다른 모듈의 카테고리를 억지로 넣으면, 화면/관리자에서 "카테고리가 안 맞는다" 문제가 생깁니다.
//     그래서 현재 사이트의 course 카테고리만 허용하고, 아니면 0(미지정)으로 안전하게 처리합니다.
if(categoryId > 0) {
	LmCategoryDao lmCategory = new LmCategoryDao("course");
	DataSet cinfo = lmCategory.find("id = " + categoryId + " AND site_id = " + siteId + " AND module = 'course' AND status = 1");
	if(!cinfo.next()) categoryId = 0;
}

if(0 > m.diffDate("D", studySdate, studyEdate)) {
	result.put("rst_code", "1100");
	result.put("rst_message", "학습기한은 수업시작일보다 빠를 수 없습니다.");
	result.print();
	return;
}

//STEP 자동 부여(왜: 같은 년도/소속과정 내에서 기수가 누적될 수 있습니다)
int nextStep = 1;
try {
	int maxStep = course.getOneInt(
		"SELECT MAX(step) FROM " + course.table
		+ " WHERE site_id = " + siteId
		+ " AND status != -1 AND year = '" + year + "'"
		+ (programId > 0 ? " AND subject_id = " + programId : "")
	);
	if(maxStep > 0) nextStep = maxStep + 1;
} catch(Exception ignore) {}

int newId = course.getSequence();
course.item("id", newId);
course.item("site_id", siteId);
course.item("subject_id", programId);
course.item("category_id", categoryId);
course.item("course_nm", courseNm);
course.item("year", year);
course.item("step", nextStep);
//왜: LM_COURSE.TERM은 원래 "학기" 용도로 쓰이므로, 여기로 저장하는 게 가장 안전합니다.
String semester = f.get("semester");
String term = "";
if(!"".equals(semester)) {
	if(-1 < semester.indexOf("1")) term = "1";
	else if(-1 < semester.indexOf("2")) term = "2";
	else if(-1 < semester.toLowerCase().indexOf("s") || -1 < semester.indexOf("여름")) term = "S";
	else if(-1 < semester.toLowerCase().indexOf("w") || -1 < semester.indexOf("겨울")) term = "W";
}
course.item("term", term);
course.item("course_type", "R");
course.item("onoff_type", "N");

course.item("request_sdate", "");
course.item("request_edate", "");
course.item("study_sdate", studySdate);
course.item("study_edate", studyEdate);

course.item("lesson_day", lessonDay);
course.item("credit", credit);
course.item("lesson_time", lessonTime);
course.item("list_price", 0);
course.item("price", 0);
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
course.item("limit_total_score", 60);

course.item("content1_title", "과목소개");
course.item("content1", f.get("content1"));
course.item("content2_title", "학습목표");
course.item("content2", f.get("content2"));

//대표이미지(선택)
String courseFile = f.get("course_file").trim();
if(!"".equals(courseFile)) course.item("course_file", courseFile);

//왜: MANAGER_ID가 NOT NULL인 환경에서 INSERT 실패를 막기 위해 기본 담당자를 세팅합니다.
course.item("manager_id", ownerId);

course.item("reg_date", m.time("yyyyMMddHHmmss"));
course.item("status", 1);

if(!course.insert()) {
	m.log(
		"course_insert",
		"insert_failed user_id=" + userId
		+ ", owner_id=" + ownerId
		+ ", site_id=" + siteId
		+ ", course_nm_len=" + courseNm.length()
		+ ", year=" + year
		+ ", study_sdate=" + studySdate
		+ ", study_edate=" + studyEdate
		+ ", lesson_day=" + lessonDay
		+ ", lesson_time=" + lessonTime
		+ ", credit=" + credit
		+ ", program_id=" + programId
		+ ", category_id=" + categoryId
	);
	result.put("rst_code", "2000");
	result.put("rst_message", "과목 저장 중 오류가 발생했습니다.");
	result.print();
	return;
}
m.log(
	"course_insert",
	"insert_ok course_id=" + newId
	+ ", user_id=" + userId
	+ ", owner_id=" + ownerId
	+ ", year=" + year
	+ ", lesson_day=" + lessonDay
	+ ", lesson_time=" + lessonTime
	+ ", credit=" + credit
	+ ", program_id=" + programId
	+ ", category_id=" + categoryId
);

//주강사 등록
courseTutor.item("course_id", newId);
courseTutor.item("user_id", ownerId);
courseTutor.item("site_id", siteId);
courseTutor.item("type", "major");
courseTutor.item("class", "1");
courseTutor.insert();

// 왜: 관리자 화면의 "과정담당자"와 교수자 담당과목 기준을 맞추기 위해, 개설자도 과정담당자로 같이 등록합니다.
if(0 >= courseManager.findCount("course_id = " + newId + " AND user_id = " + ownerId + " AND site_id = " + siteId)) {
	courseManager.item("course_id", newId);
	courseManager.item("user_id", ownerId);
	courseManager.item("site_id", siteId);
	if(!courseManager.insert()) {
		m.log("course_insert", "course_manager_insert_failed course_id=" + newId + ", owner_id=" + ownerId + ", site_id=" + siteId);
	}
}

//게시판(공지/Q&A/후기/자유) 기본 생성 - 실패해도 과목 생성은 유지합니다.
try { clBoard.insertBoard(newId); } catch(Exception ignore) {}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", newId);
result.print();

%>
