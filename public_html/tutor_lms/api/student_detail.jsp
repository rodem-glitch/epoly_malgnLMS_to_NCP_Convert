<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 담당과목 탭/수강생 추가 화면에서 학생 1명을 눌렀을 때, 목록보다 자세한 정보를 조회해야 합니다.
//- 개인정보 로그는 기존 "가려진 정보 보기"(privacy_log.jsp)에서 남기고,
//  여기서는 상세 데이터 조회만 담당해 과도한 로그 누적을 방지합니다.

int targetUserId = m.ri("user_id");
int courseId = m.ri("course_id"); // 선택값: 과목 문맥이 있으면 권한을 과목 기준으로 검증

if(0 == targetUserId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "user_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseUserDao courseUser = new CourseUserDao();
UserDao user = new UserDao();
UserDeptDao userDept = new UserDeptDao();

m.log(
	"tutor_lms_student_detail",
	"request manager_id=" + userId + ", course_id=" + courseId + ", target_user_id=" + targetUserId
);

if(courseId > 0) {
	DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
	if(!cinfo.next()) {
		result.put("rst_code", "4040");
		result.put("rst_message", "해당 과목이 없습니다.");
		result.print();
		return;
	}

	//왜: 수강생 목록과 같은 권한 규칙(주강사/과정담당자/개설자/관리자)으로 맞춰야,
	//     목록은 보이는데 상세만 막히는 불일치를 예방할 수 있습니다.
	if(!isAdmin) {
		int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
		int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
		int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
		if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
			result.put("rst_code", "4031");
			result.put("rst_message", "해당 과목의 수강생을 조회할 권한이 없습니다.");
			result.print();
			return;
		}
	}
}

String courseJoin = courseId > 0
	? " LEFT JOIN " + courseUser.table + " cu ON cu.user_id = u.id AND cu.course_id = " + courseId + " AND cu.site_id = " + siteId + " AND cu.status NOT IN (-1, -4) "
	: " LEFT JOIN " + courseUser.table + " cu ON 1 = 0 ";

DataSet info = user.query(
	" SELECT "
	+ " u.id user_id, u.login_id student_id, u.login_id, u.user_nm name, u.user_nm, u.email, u.mobile, u.dept_id "
	+ " , IFNULL(d.dept_nm, '') dept_nm "
	+ " , IFNULL(cu.id, 0) course_user_id "
	+ " , IFNULL(cu.progress_ratio, 0) progress_ratio "
	+ " , IFNULL(cu.total_score, 0) total_score "
	+ " , IFNULL(cu.complete_yn, 'N') complete_yn "
	+ " , IFNULL(cu.complete_status, 0) complete_status "
	+ " , IFNULL(cu.complete_no, '') complete_no "
	+ " , IFNULL(cu.start_date, '') start_date "
	+ " , IFNULL(cu.end_date, '') end_date "
	+ " FROM " + user.table + " u "
	+ " LEFT JOIN " + userDept.table + " d ON d.id = u.dept_id AND d.site_id = " + siteId + " AND d.status = 1 "
	+ courseJoin
	+ " WHERE u.id = " + targetUserId + " AND u.site_id = " + siteId + " AND u.user_kind = 'U' AND u.status = 1 "
);

if(!info.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 학습자 정보가 없습니다.");
	result.print();
	return;
}

int deptId = info.i("dept_id");
String deptPath = "";
if(deptId > 0) {
	try {
		deptPath = userDept.getTreeNames(deptId);
	} catch(Exception e) {
		m.errorLog("수강생 상세 부서경로 조회 오류 - " + e.getMessage(), e);
		result.put("rst_code", "5000");
		result.put("rst_message", "수강생 상세를 처리하는 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

info.put("dept_path", deptPath);
info.put("progress", Malgn.round(info.d("progress_ratio"), 1));

m.log(
	"tutor_lms_student_detail",
	"success manager_id=" + userId + ", course_id=" + courseId + ", target_user_id=" + targetUserId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", info);
result.print();

%>
