<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
CourseDao course = new CourseDao();
TutorDao tutor = new TutorDao();
CourseLessonDao courseLesson = new CourseLessonDao();
CourseTutorDao courseTutor = new CourseTutorDao();
LessonDao lesson = new LessonDao();

DataSet tinfo = tutor.query(
    " SELECT a.user_id "
    + " FROM " + tutor.table + " a "
    + " INNER JOIN " + user.table + " u ON a.user_id = u.id AND u.site_id = " + siteId + " AND u.status = 1 "
    + " WHERE u.tutor_yn = 'Y' AND a.user_id = " + userId + " AND a.site_id = " + siteId
);
if(!tinfo.next()) { m.jsError("해당 강사 정보가 없습니다."); return; }

//수강중인 과정
DataSet list = course.query(
    " SELECT c.id course_id, c.course_nm, c.onoff_type, cl.chapter, cl.twoway_url, cl.start_date, cl.start_time, cl.end_time, l.id lesson_id, l.lesson_nm, l.lesson_type, l.content_width, l.content_height "
    + " FROM " + courseLesson.table + " cl "
    + " INNER JOIN " + course.table + " c ON cl.course_id = c.id AND c.site_id = " + siteId
    + " INNER JOIN " + lesson.table + " l ON l.id = cl.lesson_id AND l.site_id = " + siteId
    + " INNER JOIN " + courseTutor.table + " ct ON ct.course_id = c.id AND user_id = " + tinfo.i("user_id") + " AND ct.site_id = " + siteId
    + " WHERE c.course_type = 'R' AND c.onoff_type IN ('N', 'F', 'B') AND l.onoff_type IN ('F', 'T') "
    + " AND l.lesson_type = '15'"
    + " AND cl.site_id = " + siteId
    + " AND cl.start_date <= '" +  sysToday + "' AND cl.end_date >= '" + sysToday + "' "
    + " AND cl.status = 1 "
    + " AND cl.tutor_id = " + userId
    + " AND c.site_id = " + siteId
);

while(list.next()){
    list.put("type_conv", m.getValue(list.s("onoff_type"), course.onoffTypes));
    list.put("course_nm_conv", m.cutString(list.s("course_nm"), 40));
    list.put("lesson_nm_conv", m.cutString(list.s("lesson_nm"), 40));
    list.put("chapter_conv", list.i("chapter"));
    list.put("start_time_conv", list.s("start_time").substring(0,2) + ":" + list.s("start_time").substring(2,4));
    list.put("end_time_conv", list.s("end_time").substring(0,2) + ":" + list.s("end_time").substring(2,4));
    list.put("study_time_conv", m.time("yyyy-MM-dd", list.s("start_date")) + " " + list.s("start_time_conv") + " - " + list.s("end_time_conv"));
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.course_lesson_list");
p.setVar("query", m.qs());
p.setVar("list_query", m.qs(""));
p.setVar("form_script", f.getScript());

p.setLoop("list", list);

p.display();

%>