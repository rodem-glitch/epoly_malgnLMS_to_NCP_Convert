<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자 LMS > 과목관리 > 과제 > 피드백 관리 화면에서,
//  학생이 제출한 "제출 제목/내용/첨부파일"을 교수자가 확인할 수 있어야 합니다.
//- 기존 homework_users.jsp는 목록/채점용 데이터 중심이라 제출 본문/파일을 내려주지 않아서,
//  모달 상세 보기 전용 API를 별도로 제공합니다.

int courseId = m.ri("course_id");
int homeworkId = m.ri("homework_id");
int courseUserId = m.ri("course_user_id");

if(0 == courseId || 0 == homeworkId || 0 == courseUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, homework_id, course_user_id가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
HomeworkUserDao homeworkUser = new HomeworkUserDao();
ClFileDao file = new ClFileDao();

//로그(민감정보 제외) - 에러가 나도 원인을 추적할 수 있게 초반에 남깁니다.
m.log(
	"tutor_homework_submission",
	"start course_id=" + courseId + ", homework_id=" + homeworkId + ", course_user_id=" + courseUserId + ", user_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin
);

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 과제 제출물을 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

//왜: 과제-과목 배치 여부를 확인해야 임의 homework_id 조회를 막을 수 있습니다.
//주의: 여기서는 과제 테이블(LM_HOMEWORK)과 조인하지 않고, 배치 테이블(LM_COURSE_MODULE) 기준으로만 확인합니다.
//      (조인 조건이 데이터/사이트 환경에 따라 오탐을 만들면 "배치 안 됨"으로 잘못 떨어질 수 있어, 원인 분리를 먼저 합니다.)
int moduleCnt = courseModule.findCount(
	"course_id = " + courseId + " AND module = 'homework' AND module_id = " + homeworkId + " AND status = 1"
);
if(0 >= moduleCnt) {
	m.log("tutor_homework_submission", "not_linked course_id=" + courseId + ", homework_id=" + homeworkId + ", site_id=" + siteId + ", user_id=" + userId);
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과제가 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

DataSet cuinfo = courseUser.find("id = " + courseUserId + " AND course_id = " + courseId + " AND site_id = " + siteId + " AND status IN (1,3)");
if(!cuinfo.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 수강 정보가 없습니다.");
	result.print();
	return;
}

DataSet info = homeworkUser.find("homework_id = " + homeworkId + " AND course_user_id = " + courseUserId + " AND status = 1");

//왜: 미제출(레코드 없음)일 수도 있으니, 모달에서 "없음"을 표시할 수 있게 기본값을 내려줍니다.
Hashtable<String, Object> data = new Hashtable<String, Object>();
data.put("course_id", courseId);
data.put("homework_id", homeworkId);
data.put("course_user_id", courseUserId);

boolean submitted = false;
if(info.next()) {
	submitted = "Y".equals(info.s("submit_yn"));
	data.put("submitted", submitted);
	data.put("submitted_at", submitted && !"".equals(info.s("reg_date")) ? m.time("yyyy.MM.dd HH:mm", info.s("reg_date")) : "-");
	data.put("subject", info.s("subject"));
	data.put("content", info.s("content"));
} else {
	data.put("submitted", false);
	data.put("submitted_at", "-");
	data.put("subject", "");
	data.put("content", "");
}

//첨부파일: 학생 제출 파일(클래스룸과 동일한 CL_FILE 모듈 규칙 사용)
//왜: Json.put에 DataSet을 그대로 중첩하면(Hashtable 안에 DataSet) 직렬화가 깨질 수 있어,
//     파일 목록은 "배열(리스트)" 형태로 평탄화해서 내려줍니다.
ArrayList<Hashtable<String, Object>> fileRows = new ArrayList<Hashtable<String, Object>>();
DataSet files = file.find("module = 'homework_" + homeworkId + "' AND module_id = " + courseUserId + " AND status = 1", "*", "id ASC");
while(files.next()) {
	Hashtable<String, Object> row = new Hashtable<String, Object>();
	String fileId = files.s("id");
	String ek = m.encrypt(fileId);
	row.put("id", files.i("id"));
	row.put("filename", files.s("filename"));
	row.put("ek", ek);
	row.put("download_url", "/classroom/download_cl.jsp?id=" + fileId + "&ek=" + ek);
	fileRows.add(row);
}
data.put("files", fileRows);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", data);
result.print();

%>
