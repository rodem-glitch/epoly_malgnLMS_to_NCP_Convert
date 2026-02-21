<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 담당과목에서 의견수렴용 설문을 직접 만들고 즉시 배치할 수 있어야 운영이 가능합니다.
//- 익명/실명은 과목별로 달라질 수 있으므로, 배치정보(LM_COURSE_MODULE)에 함께 저장합니다.

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
CourseModuleDao courseModule = new CourseModuleDao();
SurveyDao survey = new SurveyDao();
SurveyQuestionDao surveyQuestion = new SurveyQuestionDao();
SurveyItemDao surveyItem = new SurveyItemDao();

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목에 설문을 등록할 권한이 없습니다.");
		result.print();
		return;
	}
}

f.addElement("survey_nm", null, "hname:'설문명', required:'Y'");
f.addElement("content", "", "hname:'설문설명', allowhtml:'Y'");
f.addElement("question_type", "3", "hname:'문항유형'");
f.addElement("question", "강의에 대한 의견을 자유롭게 작성해 주세요.", "hname:'문항', required:'Y'");
f.addElement("question_items", "", "hname:'문항선택지'");
f.addElement("anonymous_yn", SiteConfig.s("course_survey_anonymous_yn"), "hname:'익명여부'");
f.addElement("start_date", m.time("yyyy-MM-dd", cinfo.s("study_sdate")), "hname:'설문시작일'");
f.addElement("end_date", m.time("yyyy-MM-dd", cinfo.s("study_edate")), "hname:'설문종료일'");
f.addElement("chapter", 0, "hname:'차시', option:'number'");
for(int i = 1; i <= 10; i++) {
	f.addElement("item" + i, "", "hname:'선택지" + i + "'");
}

if(!f.validate()) {
	result.put("rst_code", "1000");
	result.put("rst_message", "필수값이 누락되었습니다.");
	result.print();
	return;
}

String questionType = f.get("question_type");
if(!("1".equals(questionType) || "M".equals(questionType) || "2".equals(questionType) || "3".equals(questionType))) {
	result.put("rst_code", "1002");
	result.put("rst_message", "question_type은 1/M/2/3 중 하나여야 합니다.");
	result.print();
	return;
}

String anonymousYn = "Y".equals(f.get("anonymous_yn")) ? "Y" : "N";

String[] questionItems = new String[10];
int itemCnt = 0;
if("1".equals(questionType) || "M".equals(questionType)) {
	for(int i = 0; i < 10; i++) {
		questionItems[i] = "";
	}

	String itemRaw = f.get("question_items");
	if(!"".equals(itemRaw)) {
		String[] tokenItems = itemRaw.split("\\|\\|");
		for(int i = 0; i < tokenItems.length && i < 10; i++) {
			String token = tokenItems[i] != null ? tokenItems[i].trim() : "";
			if(!"".equals(token)) {
				questionItems[itemCnt] = token;
				itemCnt++;
			}
		}
	}

	if(itemCnt < 2) {
		for(int i = 1; i <= 10; i++) {
			String itemVal = f.get("item" + i).trim();
			if(!"".equals(itemVal) && itemCnt < 10) {
				questionItems[itemCnt] = itemVal;
				itemCnt++;
			}
		}
	}

	if(itemCnt < 2) {
		result.put("rst_code", "1003");
		result.put("rst_message", "선택형 문항은 2개 이상의 선택지가 필요합니다.");
		result.print();
		return;
	}
} else {
	for(int i = 0; i < 10; i++) questionItems[i] = "";
	itemCnt = 0;
}

String applyType = "R".equals(cinfo.s("course_type")) ? "1" : "2";
String startDateTime = "";
String endDateTime = "";
int chapter = 0;

if("1".equals(applyType)) {
	String startYmd = m.time("yyyyMMdd", f.get("start_date"));
	String endYmd = m.time("yyyyMMdd", f.get("end_date"));
	if("".equals(startYmd) || "".equals(endYmd)) {
		result.put("rst_code", "1004");
		result.put("rst_message", "정규과정 설문은 시작일/종료일을 올바르게 입력해야 합니다.");
		result.print();
		return;
	}

	startDateTime = !"".equals(startYmd) ? (startYmd + "000000") : "";
	endDateTime = !"".equals(endYmd) ? (endYmd + "235959") : "";
	chapter = 0;
} else {
	chapter = Math.max(0, f.getInt("chapter"));
}

m.log(
	"survey_insert",
	"insert_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", question_type=" + questionType
	+ ", item_cnt=" + itemCnt
	+ ", anonymous_yn=" + anonymousYn
	+ ", apply_type=" + applyType
);

int surveyId = survey.getSequence();
int questionId = surveyQuestion.getSequence();

survey.item("id", surveyId);
survey.item("site_id", siteId);
survey.item("category_id", cinfo.i("category_id"));
survey.item("survey_nm", f.get("survey_nm"));
survey.item("content", f.get("content"));
survey.item("item_cnt", 1);
survey.item("manager_id", userId);
survey.item("reg_date", m.time("yyyyMMddHHmmss"));
survey.item("status", 1);
if(!survey.insert()) {
	result.put("rst_code", "2000");
	result.put("rst_message", "설문 등록 중 오류가 발생했습니다.");
	result.print();
	return;
}

surveyQuestion.item("id", questionId);
surveyQuestion.item("site_id", siteId);
surveyQuestion.item("category_id", 0);
surveyQuestion.item("question_type", questionType);
surveyQuestion.item("question", f.get("question"));
surveyQuestion.item("item_cnt", itemCnt);
for(int i = 0; i < 10; i++) {
	surveyQuestion.item("item" + (i + 1), questionItems[i]);
}
surveyQuestion.item("manager_id", userId);
surveyQuestion.item("reg_date", m.time("yyyyMMddHHmmss"));
surveyQuestion.item("status", 1);
if(!surveyQuestion.insert()) {
	survey.item("status", -1);
	survey.update("id = " + surveyId + " AND site_id = " + siteId);

	result.put("rst_code", "2001");
	result.put("rst_message", "설문 문항 등록 중 오류가 발생했습니다.");
	result.print();
	return;
}

surveyItem.item("survey_id", surveyId);
surveyItem.item("question_id", questionId);
surveyItem.item("site_id", siteId);
surveyItem.item("sort", 1);
surveyItem.item("post_yn", "N");
surveyItem.item("status", 1);
if(!surveyItem.insert()) {
	surveyQuestion.item("status", -1);
	surveyQuestion.update("id = " + questionId + " AND site_id = " + siteId);

	survey.item("status", -1);
	survey.update("id = " + surveyId + " AND site_id = " + siteId);

	result.put("rst_code", "2002");
	result.put("rst_message", "설문 문항 연결 중 오류가 발생했습니다.");
	result.print();
	return;
}

courseModule.item("course_id", courseId);
courseModule.item("site_id", siteId);
courseModule.item("module", "survey");
courseModule.item("module_id", surveyId);
courseModule.item("module_nm", f.get("survey_nm"));
courseModule.item("parent_id", 0);
courseModule.item("item_type", "1");
courseModule.item("assign_score", 0);
courseModule.item("apply_type", applyType);
courseModule.item("start_day", 0);
courseModule.item("period", 0);
courseModule.item("start_date", startDateTime);
courseModule.item("end_date", endDateTime);
courseModule.item("chapter", chapter);
courseModule.item("retry_yn", "N");
courseModule.item("retry_score", 0);
courseModule.item("retry_cnt", 0);
courseModule.item("review_yn", "N");
courseModule.item("result_yn", anonymousYn);
courseModule.item("status", 1);

if(!courseModule.insert()) {
	surveyItem.delete("survey_id = " + surveyId + " AND question_id = " + questionId + " AND site_id = " + siteId);

	surveyQuestion.item("status", -1);
	surveyQuestion.update("id = " + questionId + " AND site_id = " + siteId);

	survey.item("status", -1);
	survey.update("id = " + surveyId + " AND site_id = " + siteId);

	result.put("rst_code", "2003");
	result.put("rst_message", "과목 설문 배치 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"survey_insert",
	"insert_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
	+ ", question_id=" + questionId
	+ ", anonymous_yn=" + anonymousYn
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", surveyId);
result.print();

%>
