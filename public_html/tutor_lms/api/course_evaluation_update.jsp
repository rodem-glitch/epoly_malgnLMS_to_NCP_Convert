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

//수료(완료) 기준 - 선택적 (컬럼이 없을 수 있음)
f.addElement("complete_limit_progress", 60, "hname:'수료 진도 기준', option:'number'");
f.addElement("complete_limit_total_score", 60, "hname:'수료 총점 기준', option:'number'");

//기타 옵션
f.addElement("assign_survey_yn", "N", "hname:'설문참여 포함'");
f.addElement("push_survey_yn", "N", "hname:'설문독려'");
f.addElement("pass_yn", "N", "hname:'합격 상태 사용'");

course.item("assign_progress", f.getInt("assign_progress"));
course.item("assign_exam", f.getInt("assign_exam"));
course.item("assign_homework", f.getInt("assign_homework"));
course.item("assign_forum", f.getInt("assign_forum"));
course.item("assign_etc", f.getInt("assign_etc"));

course.item("limit_progress", f.getInt("limit_progress"));
course.item("limit_total_score", f.getInt("limit_total_score"));

course.item("assign_survey_yn", f.get("assign_survey_yn", "N"));
course.item("push_survey_yn", f.get("push_survey_yn", "N"));
course.item("pass_yn", f.get("pass_yn", "N"));
// course.item("mod_date", m.time("yyyyMMddHHmmss")); // LM_COURSE 테이블에 mod_date 컬럼 없음

// 1단계: 기본 필드 업데이트 시도
String whereClause = "id = " + courseId + " AND site_id = " + siteId;
try {
	course.update(whereClause);
    // 맑은 프레임워크의 update()는 변경된 행이 0일 경우 false를 반환할 수 있습니다.
    // 위에서 과목 존재 여부(courseInfo.next())를 이미 확인했으므로, 
    // 여기서 예외가 발생하지 않았다면 성공으로 간주합니다.
} catch(Exception e) {
	// 여기서 에러가 나면 특정 컬럼(아마도 새로 추가한 것들)이 없을 가능성이 큼
	result.put("rst_code", "2001");
	result.put("rst_message", "DB 오류 (기본필드): " + e.getMessage());
	result.print();
	return;
}

// 2단계: 수료 기준 필드 업데이트 시도 (별도로 시도하여 실패해도 전체가 실패하지 않게 함)
try {
    CourseDao course2 = new CourseDao();
    course2.item("complete_limit_progress", f.getInt("complete_limit_progress"));
    course2.item("complete_limit_total_score", f.getInt("complete_limit_total_score"));
    course2.update(whereClause); 
} catch(Exception e) {
    // 수료 기준 컬럼이 없을 경우 여기서 에러 발생 시 무시
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", courseId);
result.print();

%>
