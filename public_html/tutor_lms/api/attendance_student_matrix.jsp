<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자는 "학생별로 각 차시가 출석(Y)/결석(N)인지"를 한 화면에서 보고 바로 수정해야 합니다.
//- 그래서 수강생 × 차시 매트릭스를 서버에서 만들어 내려주면 프론트가 빠르게 표를 구성할 수 있습니다.

int courseId = m.ri("course_id");
int sectionId = m.ri("section_id"); //선택: 특정 주차(섹션)만 조회
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();
CourseLessonDao courseLesson = new CourseLessonDao();
CourseSectionDao courseSection = new CourseSectionDao();
CourseProgressDao courseProgress = new CourseProgressDao(siteId);
LessonDao lesson = new LessonDao();
UserDao user = new UserDao();

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

String sectionWhere = (0 < sectionId ? " AND cl.section_id = " + sectionId + " " : "");

DataSet lessons = courseLesson.query(
	" SELECT cl.section_id, cl.lesson_id, cl.chapter "
	+ " , IFNULL(cs.section_nm, '') section_nm "
	+ " , l.lesson_nm, l.lesson_type, l.total_time "
	+ " FROM " + courseLesson.table + " cl "
	+ " LEFT JOIN " + courseSection.table + " cs "
		+ " ON cs.id = cl.section_id AND cs.course_id = cl.course_id AND cs.site_id = " + siteId + " AND cs.status = 1 "
	+ " INNER JOIN " + lesson.table + " l ON l.id = cl.lesson_id AND l.status = 1 "
	+ " WHERE cl.course_id = " + courseId + " AND cl.site_id = " + siteId + " AND cl.status = 1 "
	+ sectionWhere
	+ " ORDER BY cl.chapter ASC "
);
while(lessons.next()) {
	lessons.put("section_nm", !"".equals(lessons.s("section_nm")) ? lessons.s("section_nm") : "기본");
	lessons.put("duration_conv", lessons.i("total_time") > 0 ? lessons.i("total_time") + "분" : "-");
}

DataSet students = courseUser.query(
	" SELECT cu.id course_user_id, cu.user_id, u.login_id student_id, u.user_nm name "
	+ " FROM " + courseUser.table + " cu "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id "
	+ " WHERE cu.course_id = " + courseId + " AND cu.site_id = " + siteId + " AND cu.status IN (1,3) "
	+ " ORDER BY u.user_nm ASC, cu.id ASC "
);

DataSet matrix = courseUser.query(
	" SELECT cu.id course_user_id, cu.user_id, u.login_id student_id, u.user_nm name "
	+ " , cl.section_id, IFNULL(cs.section_nm, '') section_nm, cl.lesson_id, cl.chapter "
	+ " , l.lesson_nm, l.lesson_type, l.total_time "
	+ " , IFNULL(cp.complete_yn, 'N') attend_yn, IFNULL(cp.complete_date, '') attend_date, IFNULL(cp.last_date, '') last_date "
	+ " FROM " + courseUser.table + " cu "
	+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id "
	+ " CROSS JOIN " + courseLesson.table + " cl "
	+ " LEFT JOIN " + courseSection.table + " cs "
		+ " ON cs.id = cl.section_id AND cs.course_id = cl.course_id AND cs.site_id = " + siteId + " AND cs.status = 1 "
	+ " INNER JOIN " + lesson.table + " l ON l.id = cl.lesson_id AND l.status = 1 "
	+ " LEFT JOIN " + courseProgress.table + " cp "
		+ " ON cp.course_user_id = cu.id AND cp.lesson_id = cl.lesson_id AND cp.site_id = " + siteId + " AND cp.status = 1 "
	+ " WHERE cu.course_id = " + courseId + " AND cu.site_id = " + siteId + " AND cu.status IN (1,3) "
	+ " AND cl.course_id = " + courseId + " AND cl.site_id = " + siteId + " AND cl.status = 1 "
	+ sectionWhere
	+ " ORDER BY u.user_nm ASC, cu.id ASC, cl.chapter ASC "
);

while(matrix.next()) {
	matrix.put("section_nm", !"".equals(matrix.s("section_nm")) ? matrix.s("section_nm") : "기본");
	matrix.put("duration_conv", matrix.i("total_time") > 0 ? matrix.i("total_time") + "분" : "-");
	matrix.put("attend_label", "Y".equals(matrix.s("attend_yn")) ? "출석" : "결석");

	String attendDate = !"".equals(matrix.s("attend_date")) ? matrix.s("attend_date") : matrix.s("last_date");
	matrix.put("attend_date_conv", !"".equals(attendDate) ? m.time("yyyy.MM.dd HH:mm", attendDate) : "-");
}

m.log(
	"attendance_student_matrix",
	"matrix_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", section_id=" + sectionId
	+ ", student_count=" + students.size()
	+ ", lesson_count=" + lessons.size()
	+ ", matrix_count=" + matrix.size()
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_student_count", students.size());
result.put("rst_lesson_count", lessons.size());
result.put("rst_count", matrix.size());
result.put("rst_students", students);
result.put("rst_lessons", lessons);
result.put("rst_data", matrix);
result.print();

%>
