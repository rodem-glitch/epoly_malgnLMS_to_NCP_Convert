<%@ page contentType="text/html; charset=utf-8" %>
<%@ page import="java.net.*" %>
<%@ include file="../../init.jsp" %><%

// -------------------------------------------------------------------
// 목적: 신규 메인 헤더/푸터 안에서 "채용" 화면을 보여주는 전용 페이지
// -------------------------------------------------------------------

//객체
UserDao user = new UserDao();
UserDeptDao userDept = new UserDeptDao();
PolyStudentDao polyStudent = new PolyStudentDao();
PolyCourseDao polyCourse = new PolyCourseDao();
PolyMemberKeyDao polyMemberKey = new PolyMemberKeyDao();

// 사용자 정보 (로그인 상태에서만)
DataSet uinfo = null;
if(userId > 0) {
	uinfo = user.find("id = " + userId + " AND status = 1");
	if(!uinfo.next()) {
		uinfo = null;
	}
}

// 학과명 추출 (왜: 채용 페이지에서 학과에 맞는 직무 기본값을 자동 세팅하기 위해 필요합니다)
String deptName = "";
if(userId > 0 && uinfo != null) {
	if(0 < uinfo.i("dept_id")) {
		deptName = userDept.getNames(uinfo.i("dept_id"));
	}
}

if("".equals(deptName) && userId > 0 && uinfo != null) {
	// 왜: 소속(부서) 정보가 없을 때는 학사 수강정보에서 최근 학과명을 가져옵니다.
	String memberKey = "";
	DataSet memberKeyInfo = polyMemberKey.find("alias_key = '" + uinfo.s("login_id") + "'");
	if(memberKeyInfo.next()) {
		memberKey = memberKeyInfo.s("member_key");
	} else {
		memberKey = uinfo.s("login_id");
	}

	DataSet deptRow = polyStudent.query(
		" SELECT c.dept_name "
		+ " FROM " + polyStudent.table + " s "
		+ " INNER JOIN " + polyCourse.table + " c ON s.course_code = c.course_code "
		+ "   AND s.open_year = c.open_year AND s.open_term = c.open_term "
		+ "   AND s.bunban_code = c.bunban_code AND s.group_code = c.group_code "
		+ " WHERE s.member_key = '" + memberKey + "' "
		+ " ORDER BY c.startdate DESC, c.course_name ASC "
		, 1
	);
	if(deptRow.next()) {
		deptName = deptRow.s("dept_name");
	}
}

// 채용 페이지 URL (같은 도메인 프록시를 사용해서 외부 PC에서도 열리게 합니다)
String jobUrl = "/api/job_proxy.jsp/job-test.html";

// 권한 파라미터: 관리자(S/A) 또는 교수자(tutor_yn=Y)인 경우 staff=Y
boolean isStaff = "S".equals(userKind) || "A".equals(userKind);
if(!isStaff && uinfo != null && "Y".equals(uinfo.s("tutor_yn"))) {
	isStaff = true;
}
jobUrl += "?staff=" + (isStaff ? "Y" : "N");

if(!"".equals(deptName)) {
	jobUrl += "&dept=" + URLEncoder.encode(deptName, "UTF-8");
}

// 레이아웃: blank (전역 네비게이션 제외)
p.setLayout("new_main");
p.setBody("mypage.new_main_job");

// 로그인 상태 및 사용자 정보
p.setVar("login_block", userId > 0 && uinfo != null);
if(userId > 0 && uinfo != null) {
	p.setVar("user", uinfo);
	p.setVar("SYS_USERNAME", uinfo.s("user_nm"));
	String userNameForHeader = uinfo.s("user_nm");
	p.setVar("SYS_USERNAME_INITIAL", userNameForHeader.length() > 0 ? userNameForHeader.substring(0, 1) : "?");
} else {
	p.setVar("SYS_USERNAME", "");
	p.setVar("SYS_USERNAME_INITIAL", "");
}

p.setVar("job_iframe_url", jobUrl);
p.setVar("dept_name", deptName);

p.display();

%>
