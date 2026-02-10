<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목개설(CreateSubjectWizard)에서 대표이미지를 먼저 업로드하고, 그 파일명을 LM_COURSE.COURSE_FILE에 저장해야 합니다.
//- 업로드와 DB 반영을 한 엔드포인트로 제공하면, 화면은 "업로드 → 파일명 저장" 흐름을 단순하게 만들 수 있습니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

//파일 업로드(이미지만 허용)
f.addElement("course_file", "", "hname:'메인이미지', required:'Y', allow:'jpg|jpeg|gif|png|webp'");
if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", f.errMsg);
	result.print();
	return;
}

File uploaded = f.saveFile("course_file");
if(uploaded == null || null == f.getFileName("course_file")) {
	result.put("rst_code", "2000");
	result.put("rst_message", "파일 업로드 중 오류가 발생했습니다.");
	result.print();
	return;
}

String fileName = f.getFileName("course_file");
String fileUrl = m.getUploadUrl(fileName);

//(선택) course_id가 넘어오면, 바로 LM_COURSE.COURSE_FILE까지 업데이트합니다.
int courseId = m.ri("course_id");
if(courseId > 0) {
	CourseDao course = new CourseDao();
	CourseTutorDao courseTutor = new CourseTutorDao();
	CourseManagerDao courseManager = new CourseManagerDao();

	if(!isAdmin) {
		int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
		int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
		int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
		if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
			result.put("rst_code", "4031");
			result.put("rst_message", "해당 과목의 이미지를 수정할 권한이 없습니다.");
			result.print();
			return;
		}
	}

	course.item("course_file", fileName);
	// 왜: 일부 환경(DB 스키마)에는 LM_COURSE에 mod_date 컬럼이 없어 UPDATE가 통째로 실패합니다.
	//     DB를 변경하지 않고 우선 저장이 되도록 mod_date 업데이트는 생략합니다.
	course.update("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
}

DataSet data = new DataSet();
data.addRow();
data.put("file_name", fileName);
data.put("file_url", fileUrl);
data.put("course_id", courseId);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", data);
result.print();

%>

