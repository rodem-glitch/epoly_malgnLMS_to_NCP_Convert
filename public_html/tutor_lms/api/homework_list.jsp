<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목관리 > 과제 탭에서, 과목에 배치된 과제 목록과 제출/채점 현황을 운영 DB 기준으로 보여주기 위함입니다.

int courseId = m.ri("course_id");
if(0 == courseId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkDao homework = new HomeworkDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();

//권한
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 정보를 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

DataSet list = courseModule.query(
	" SELECT a.module_id homework_id, a.module_nm, a.apply_type, a.start_date, a.end_date, a.chapter, a.assign_score "
	+ " , h.homework_nm, h.onoff_type, h.content, h.homework_file, h.submit_file_ext_mode, h.submit_file_exts "
	+ " , (SELECT COUNT(*) FROM " + courseUser.table + " cu "
		+ " WHERE cu.site_id = " + siteId + " AND cu.course_id = a.course_id AND cu.status IN (1,3)) total_cnt "
	+ " , (SELECT COUNT(*) FROM " + homeworkUser.table + " hu "
		+ " INNER JOIN " + courseUser.table + " cu ON cu.id = hu.course_user_id AND cu.status IN (1,3) "
		+ " WHERE hu.homework_id = a.module_id AND hu.course_id = a.course_id "
		+ " AND hu.status = 1 AND hu.submit_yn = 'Y') submitted_cnt "
	+ " , (SELECT COUNT(*) FROM " + homeworkUser.table + " hu "
		+ " INNER JOIN " + courseUser.table + " cu ON cu.id = hu.course_user_id AND cu.status IN (1,3) "
		+ " WHERE hu.homework_id = a.module_id AND hu.course_id = a.course_id "
		+ " AND hu.status = 1 AND hu.submit_yn = 'Y' AND hu.confirm_yn = 'Y') confirmed_cnt "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + homework.table + " h ON a.module_id = h.id AND h.site_id = " + siteId + " AND h.status != -1 "
	+ " WHERE a.course_id = " + courseId + " AND a.module = 'homework' AND a.status = 1 "
	+ " ORDER BY a.start_date ASC, a.end_date ASC, a.period ASC, a.module_id ASC "
);

while(list.next()) {
	list.put("start_date_conv", !"".equals(list.s("start_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("start_date")) : "");
	list.put("end_date_conv", !"".equals(list.s("end_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("end_date")) : "");

	list.put("total_cnt", list.i("total_cnt"));
	list.put("submitted_cnt", list.i("submitted_cnt"));
	list.put("confirmed_cnt", list.i("confirmed_cnt"));

	// 왜: 교수자가 과제 첨부를 바로 확인/다운로드할 수 있어야 하므로,
	//      목록 응답에 다운로드 링크 계산값을 함께 내려줍니다.
	String homeworkFile = list.s("homework_file");
	if(!"".equals(homeworkFile)) {
		String homeworkFileConv = m.encode(homeworkFile);
		String homeworkFileEk = m.encrypt(homeworkFile + m.time("yyyyMMdd"));
		list.put("homework_file_conv", homeworkFileConv);
		list.put("homework_file_ek", homeworkFileEk);
		list.put("homework_file_download_url", "/main/download_file.jsp?file=" + homeworkFileConv + "&ek=" + homeworkFileEk);
	} else {
		list.put("homework_file_conv", "");
		list.put("homework_file_ek", "");
		list.put("homework_file_download_url", "");
	}

	String submitFileExtMode = homework.normalizeSubmitFileExtMode(list.s("submit_file_ext_mode"));
	if("".equals(submitFileExtMode)) submitFileExtMode = "ALL";
	String submitFileExts = homework.normalizeSubmitFileExts(list.s("submit_file_exts"));
	if(!"CUSTOM".equals(submitFileExtMode)) submitFileExts = "";
	String submitFileAllowExt = homework.resolveSubmitFileExts(submitFileExtMode, submitFileExts);
	if("".equals(submitFileAllowExt)) submitFileAllowExt = homework.resolveSubmitFileExts("ALL", "");
	list.put("submit_file_ext_mode", submitFileExtMode);
	list.put("submit_file_exts", submitFileExts);
	list.put("submit_file_allow_ext", submitFileAllowExt);
	list.put("submit_file_allow_ext_conv", homework.toCommaSeparatedExts(submitFileAllowExt));
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>

