<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목 소개/학습목표 같은 텍스트는 운영 중에도 수정될 수 있으므로 저장 API가 필요합니다.

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

if(!isAdmin) {
	// 왜: 과정담당자만 지정된 과목도 수정 가능해야 담당과목 노출 기준과 권한 기준이 맞습니다.
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목 정보를 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

f.addElement("content1", "", "hname:'과목소개', allowhtml:'Y'");
f.addElement("content2", "", "hname:'학습목표', allowhtml:'Y'");

String content1 = f.get("content1");
String content2 = f.get("content2");

course.item("content1_title", "과목소개");
course.item("content1", content1);
course.item("content2_title", "학습목표");
course.item("content2", content2);
// 왜: 일부 환경(DB 스키마)에는 LM_COURSE에 mod_date 컬럼이 없어 UPDATE가 통째로 실패합니다.
//     DB를 변경하지 않고 우선 저장이 되도록 mod_date 업데이트는 생략합니다.

if(!course.update("id = " + courseId + " AND site_id = " + siteId + " AND status != -1")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "저장 중 오류가 발생했습니다.");
	result.print();
	return;
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", courseId);
result.print();

%>

