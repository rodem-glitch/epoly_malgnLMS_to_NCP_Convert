<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 담당과목 > 설문관리 화면에서, 과목에 배치된 설문 목록/참여현황/익명설정을 한번에 조회해야 합니다.

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
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
SurveyDao survey = new SurveyDao();
SurveyUserDao surveyUser = new SurveyUserDao();

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 설문 정보를 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

m.log(
	"survey_list",
	"list_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
);

DataSet list = courseModule.query(
	" SELECT a.module_id survey_id, a.module_nm, a.apply_type, a.start_date, a.end_date, a.chapter, a.assign_score, a.result_yn "
	+ " , s.survey_nm, s.item_cnt "
	+ " , (SELECT COUNT(*) FROM " + courseUser.table + " cu "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = a.course_id AND cu.status IN (1,3)) total_cnt "
	+ " , (SELECT COUNT(*) FROM " + surveyUser.table + " su "
		+ " INNER JOIN " + courseUser.table + " cu ON cu.id = su.course_user_id AND cu.site_id = " + siteId + " AND cu.status IN (1,3) "
		+ " WHERE su.site_id = " + siteId + " AND su.survey_id = a.module_id AND su.course_id = a.course_id AND su.status = 1) submitted_cnt "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + survey.table + " s ON a.module_id = s.id AND s.site_id = " + siteId + " AND s.status != -1 "
	+ " WHERE a.course_id = " + courseId + " AND a.site_id = " + siteId + " AND a.module = 'survey' AND a.status = 1 "
	+ " ORDER BY a.apply_type ASC, a.start_date ASC, a.end_date ASC, a.chapter ASC, a.module_id ASC "
);

while(list.next()) {
	String anonymousYn = "Y".equals(list.s("result_yn")) ? "Y" : "N";
	list.put("anonymous_yn", anonymousYn);
	list.put("anonymous_type_conv", "Y".equals(anonymousYn) ? "익명" : "실명");

	list.put("start_date_conv", "".equals(list.s("start_date")) ? "" : m.time("yyyy.MM.dd HH:mm", list.s("start_date")));
	list.put("end_date_conv", "".equals(list.s("end_date")) ? "" : m.time("yyyy.MM.dd HH:mm", list.s("end_date")));
	list.put("apply_type_conv", "1".equals(list.s("apply_type")) ? "기간" : "차시");
	list.put("apply_conv", "1".equals(list.s("apply_type"))
		? (list.s("start_date_conv") + " ~ " + list.s("end_date_conv"))
		: (list.i("chapter") == 0 ? "학습시작 전" : list.i("chapter") + "차시 학습 후")
	);

	list.put("total_cnt", list.i("total_cnt"));
	list.put("submitted_cnt", list.i("submitted_cnt"));
	list.put("survey_rate", m.nf(list.i("total_cnt") > 0 ? (double)list.i("submitted_cnt") / (double)list.i("total_cnt") * 100.0 : 0.0, 1));
}

m.log(
	"survey_list",
	"list_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", row_count=" + list.size()
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
