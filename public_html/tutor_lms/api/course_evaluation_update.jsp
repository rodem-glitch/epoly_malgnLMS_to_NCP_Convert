<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 운영 중 평가 비율/수료 기준이 바뀔 수 있으므로 수정 API가 필요합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

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

// 먼저 과목이 존재하는지 확인
DataSet courseInfo = course.find("id = " + courseId + " AND site_id = " + siteId);
if(!courseInfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목(ID: " + courseId + ")이 존재하지 않습니다.");
	result.print();
	return;
}

if(courseInfo.getInt("status") == -1) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 과목은 삭제된 상태입니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 평가설정을 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

//배점(0~100 범위 권장)
f.addElement("assign_progress", 100, "hname:'출석 배점', option:'number'");
f.addElement("assign_exam", 0, "hname:'시험 배점', option:'number'");
f.addElement("assign_homework", 0, "hname:'과제 배점', option:'number'");
f.addElement("assign_forum", 0, "hname:'토론 배점', option:'number'");
f.addElement("assign_etc", 0, "hname:'기타 배점', option:'number'");

//수료 기준 (총점, 진도만 사용)
f.addElement("limit_progress", 60, "hname:'진도 기준', option:'number'");
f.addElement("limit_total_score", 60, "hname:'총점 기준', option:'number'");

//수료(완료)/결석 기준
f.addElement("complete_limit_progress", 60, "hname:'수료 진도 기준', option:'number'");
f.addElement("complete_limit_total_score", 60, "hname:'수료 총점 기준', option:'number'");
f.addElement("limit_absence_yn", "N", "hname:'결석 기준 사용'");
f.addElement("limit_absence_cnt", 0, "hname:'결석 허용 횟수', option:'number'");

//기타 옵션
f.addElement("assign_survey_yn", "N", "hname:'설문참여 포함'");
f.addElement("push_survey_yn", "N", "hname:'설문독려'");
f.addElement("pass_yn", "N", "hname:'합격 상태 사용'");

if("Y".equals(f.get("limit_absence_yn")) && f.getInt("limit_absence_cnt") <= 0) {
	result.put("rst_code", "1002");
	result.put("rst_message", "결석 기준을 사용하면 허용 횟수는 1회 이상이어야 합니다.");
	result.print();
	return;
}

m.log(
	"course_evaluation_update",
	"update_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", limit_absence_yn=" + f.get("limit_absence_yn", "N")
	+ ", limit_absence_cnt=" + f.getInt("limit_absence_cnt")
);

course.item("assign_progress", f.getInt("assign_progress"));
course.item("assign_exam", f.getInt("assign_exam"));
course.item("assign_homework", f.getInt("assign_homework"));
course.item("assign_forum", f.getInt("assign_forum"));
course.item("assign_etc", f.getInt("assign_etc"));

course.item("limit_progress", f.getInt("limit_progress"));
course.item("limit_total_score", f.getInt("limit_total_score"));
course.item("complete_limit_progress", f.getInt("complete_limit_progress"));
course.item("complete_limit_total_score", f.getInt("complete_limit_total_score"));
course.item("limit_absence_yn", f.get("limit_absence_yn", "N"));
course.item("limit_absence_cnt", f.getInt("limit_absence_cnt"));

course.item("assign_survey_yn", f.get("assign_survey_yn", "N"));
course.item("push_survey_yn", f.get("push_survey_yn", "N"));
course.item("pass_yn", f.get("pass_yn", "N"));
// course.item("mod_date", m.time("yyyyMMddHHmmss")); // LM_COURSE 테이블에 mod_date 컬럼 없음

String whereClause = "id = " + courseId + " AND site_id = " + siteId;
try {
	// 왜: DataObject.update()는 "변경 건수 0건"일 때 false를 반환할 수 있습니다.
	//     과목 존재/권한은 위에서 이미 검증했으므로, 예외가 없으면 정상 처리로 봅니다.
	course.update(whereClause);
} catch(Exception e) {
	result.put("rst_code", "2001");
	result.put("rst_message", "DB 오류: " + e.getMessage());
	result.print();
	return;
}

m.log(
	"course_evaluation_update",
	"update_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", courseId);
result.print();

%>
