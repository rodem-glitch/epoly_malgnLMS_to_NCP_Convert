<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 차시 편집에서 인정시간(complete_time), 순서(chapter) 등을 업데이트할 수 있어야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
int lessonId = m.ri("lesson_id");
if(0 == courseId || 0 == lessonId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id와 lesson_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseLessonDao courseLesson = new CourseLessonDao();
LessonDao lesson = new LessonDao();

//권한 체크
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 강의목차를 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

//업데이트할 필드들
int chapter = m.ri("chapter");
int sectionId = m.ri("section_id");
int completeTime = m.ri("complete_time");
int tutorId = m.ri("tutor_id");
String startDate = m.rs("start_date");
String endDate = m.rs("end_date");
int sourceChapter = m.ri("source_chapter");
int sourceSectionId = m.ri("source_section_id");

boolean hasSourceChapter = !"".equals(m.rs("source_chapter"));
boolean hasSourceSectionId = !"".equals(m.rs("source_section_id"));

boolean hasUpdate = false;

if(chapter > 0) {
	courseLesson.item("chapter", chapter);
	hasUpdate = true;
}
if(sectionId >= 0 && !"".equals(m.rs("section_id"))) {
	courseLesson.item("section_id", sectionId);
	hasUpdate = true;
}
if(completeTime >= 0 && !"".equals(m.rs("complete_time"))) {
	//인정시간은 레슨 테이블에 저장
	lesson.item("complete_time", completeTime);
	if(!lesson.update("id = " + lessonId + " AND site_id = " + siteId + " AND status != -1")) {
		m.log("curriculum_lesson_update", "complete_time_update_failed course_id=" + courseId + ", lesson_id=" + lessonId + ", user_id=" + userId);
		result.put("rst_code", "2000");
		result.put("rst_message", "인정시간 업데이트 중 오류가 발생했습니다.");
		result.print();
		return;
	}
	m.log("curriculum_lesson_update", "complete_time_update_ok course_id=" + courseId + ", lesson_id=" + lessonId + ", complete_time=" + completeTime + ", user_id=" + userId);
}
if(tutorId > 0) {
	courseLesson.item("tutor_id", tutorId);
	hasUpdate = true;
}
if(!"".equals(startDate)) {
	courseLesson.item("start_date", m.time("yyyyMMdd", startDate));
	hasUpdate = true;
}
if(!"".equals(endDate)) {
	courseLesson.item("end_date", m.time("yyyyMMdd", endDate));
	hasUpdate = true;
}

if(hasUpdate) {
	String updateWhere =
		"course_id = " + courseId
		+ " AND lesson_id = " + lessonId
		+ " AND site_id = " + siteId
		+ " AND status != -1";
	if(hasSourceChapter) updateWhere += " AND chapter = " + sourceChapter;
	if(hasSourceSectionId) updateWhere += " AND section_id = " + sourceSectionId;

	if(!courseLesson.update(updateWhere)) {
		m.log("curriculum_lesson_update", "update_failed course_id=" + courseId + ", lesson_id=" + lessonId + ", source_chapter=" + sourceChapter + ", source_section_id=" + sourceSectionId + ", user_id=" + userId);
		result.put("rst_code", "2000");
		result.put("rst_message", "차시 업데이트 중 오류가 발생했습니다.");
		result.print();
		return;
	}
	m.log("curriculum_lesson_update", "update_ok course_id=" + courseId + ", lesson_id=" + lessonId + ", chapter=" + chapter + ", section_id=" + sectionId + ", source_chapter=" + sourceChapter + ", source_section_id=" + sourceSectionId + ", user_id=" + userId);
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.print();

%>
