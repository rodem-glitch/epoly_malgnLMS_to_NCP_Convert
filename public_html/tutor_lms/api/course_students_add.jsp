<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과목개설(CreateSubjectWizard) 또는 수강생 관리에서 선택한 학습자를 실제 수강생(LM_COURSE_USER)으로 등록해야 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
String userIdsRaw = m.rs("user_ids"); //예: "12,34,56"
if(0 == courseId || "".equals(userIdsRaw)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id와 user_ids가 필요합니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseDao course = new CourseDao();
CourseUserDao courseUser = new CourseUserDao();
UserDao user = new UserDao();

//권한: 교수자는 본인 과목(주강사)만, 관리자는 전체
if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 수강생을 등록할 권한이 없습니다.");
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

int inserted = 0;
int skipped = 0;
int notFound = 0;
java.util.HashSet<Integer> seenUserIds = new java.util.HashSet<Integer>();

String[] parts = m.split(",", userIdsRaw);
for(int i = 0; i < parts.length; i++) {
	String token = m.replace(parts[i], "\n", "").trim();
	if("".equals(token)) continue;

	int targetUserId = 0;
	boolean isNumeric = token.matches("\\d+");

	// 1) 숫자면 user_id로 먼저 해석
	if(isNumeric) {
		targetUserId = m.parseInt(token);
		if(targetUserId > 0) {
			int exists = user.findCount("id = " + targetUserId + " AND site_id = " + siteId + " AND status = 1 AND user_kind = 'U'");
			if(exists <= 0) targetUserId = 0;
		}
	}

	// 2) 숫자가 아니거나 user_id가 없으면 login_id로 조회
	if(targetUserId <= 0) {
		String safeLoginId = m.replace(token, "'", "''");
		DataSet loginInfo = user.find("login_id = '" + safeLoginId + "' AND site_id = " + siteId + " AND status = 1 AND user_kind = 'U'");
		if(loginInfo.next()) targetUserId = loginInfo.i("id");
	}

	if(targetUserId <= 0) {
		notFound++;
		continue;
	}

	// 입력 중복 방지
	if(seenUserIds.contains(targetUserId)) {
		skipped++;
		continue;
	}
	seenUserIds.add(targetUserId);

	// 중복 방지 (이미 수강생)
	if(0 < courseUser.findCount("course_id = " + courseId + " AND user_id = " + targetUserId + " AND site_id = " + siteId + " AND status NOT IN (-1, -4)")) {
		skipped++;
		continue;
	}

	if(courseUser.addUser(cinfo, targetUserId, 1)) inserted++;
	else skipped++;
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", inserted);
result.put("rst_skipped", skipped);
result.put("rst_not_found", notFound);
result.print();

%>
