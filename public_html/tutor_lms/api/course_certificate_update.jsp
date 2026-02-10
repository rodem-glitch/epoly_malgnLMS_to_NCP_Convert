<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목별 증명서(수료증/합격증) 템플릿/번호 규칙은 운영 중에도 변경될 수 있습니다.
//- React 화면의 "과목정보 > 수료증/합격증" 탭에서 설정을 저장할 수 있어야 합니다.

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

//권한
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 증명서 설정을 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

//왜: 환경마다 일부 컬럼이 없을 수는 있으나(커스텀 DB), 기본 설정은 최대한 저장을 시도합니다.
f.addElement("cert_complete_yn", "Y", "hname:'수료증 사용여부'");
f.addElement("cert_template_id", 0, "hname:'수료증 템플릿', option:'number'");
f.addElement("pass_cert_template_id", 0, "hname:'합격증 템플릿', option:'number'");

f.addElement("complete_no_yn", "N", "hname:'수료번호 사용여부'");
f.addElement("complete_prefix", "", "hname:'수료번호 앞자리'");
f.addElement("postfix_cnt", 0, "hname:'수료번호 뒷자리수', option:'number'");
f.addElement("postfix_type", "R", "hname:'수료번호 뒷자리방식'");
f.addElement("postfix_ord", "A", "hname:'수료번호 정렬방식'");

course.item("cert_complete_yn", f.get("cert_complete_yn", "Y"));
course.item("cert_template_id", f.getInt("cert_template_id"));
course.item("pass_cert_template_id", f.getInt("pass_cert_template_id"));

course.item("complete_no_yn", f.get("complete_no_yn", "N"));
course.item("complete_prefix", f.get("complete_prefix"));
course.item("postfix_cnt", f.getInt("postfix_cnt"));
course.item("postfix_type", f.get("postfix_type", "R"));
course.item("postfix_ord", f.get("postfix_ord", "A"));
// 왜: 일부 환경(DB 스키마)에는 LM_COURSE에 mod_date 컬럼이 없어 UPDATE가 통째로 실패합니다.
//     DB를 변경하지 않고 우선 저장이 되도록 mod_date 업데이트는 생략합니다.

boolean ok = false;
try {
	ok = course.update("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
} catch(Exception e) {
	ok = false;
}

if(!ok) {
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

