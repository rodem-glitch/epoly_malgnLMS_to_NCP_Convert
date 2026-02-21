<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 부분 출결(차시별 Y/N)을 빠르게 처리하려면, 먼저 "주차(섹션)별 차시 목록"을 한 번에 불러와야 합니다.
//- 이 API는 프론트가 주차 그룹 UI를 만들 수 있게 최소 정보(주차/차시/시간)를 제공합니다.

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseLessonDao courseLesson = new CourseLessonDao();
CourseSectionDao courseSection = new CourseSectionDao();
LessonDao lesson = new LessonDao();

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type IN ('major','minor') AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 출결 정보를 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet lessons = courseLesson.query(
	" SELECT cl.course_id, cl.section_id, cl.lesson_id, cl.chapter, cl.start_date, cl.end_date "
	+ " , IFNULL(cs.section_nm, '') section_nm "
	+ " , l.lesson_nm, l.lesson_type, l.total_time "
	+ " FROM " + courseLesson.table + " cl "
	+ " LEFT JOIN " + courseSection.table + " cs "
		+ " ON cs.id = cl.section_id AND cs.course_id = cl.course_id AND cs.site_id = " + siteId + " AND cs.status = 1 "
	+ " INNER JOIN " + lesson.table + " l ON l.id = cl.lesson_id AND l.status = 1 "
	+ " WHERE cl.course_id = " + courseId + " AND cl.site_id = " + siteId + " AND cl.status = 1 "
	+ " ORDER BY cl.section_id ASC, cl.chapter ASC "
);

DataSet sections = new DataSet();
HashSet<String> sectionSet = new HashSet<String>();

while(lessons.next()) {
	String sectionKey = lessons.s("section_id");
	String sectionNm = !"".equals(lessons.s("section_nm")) ? lessons.s("section_nm") : "기본";

	lessons.put("section_nm", sectionNm);
	lessons.put("duration_conv", lessons.i("total_time") > 0 ? lessons.i("total_time") + "분" : "-");

	if(!sectionSet.contains(sectionKey)) {
		sectionSet.add(sectionKey);
		sections.addRow();
		sections.put("section_id", lessons.i("section_id"));
		sections.put("section_nm", sectionNm);
	}
}

m.log(
	"attendance_week_lessons",
	"list_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", section_count=" + sections.size()
	+ ", lesson_count=" + lessons.size()
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_section_count", sections.size());
result.put("rst_count", lessons.size());
result.put("rst_sections", sections);
result.put("rst_data", lessons);
result.print();

%>
