<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과제 템플릿 탭에서 생성/수정한 값을 서버 DB에 저장하기 위함입니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int id = m.ri("id");
String templateNm = m.rs("template_nm");
String description = m.rs("description");
String submissionType = m.rs("submission_type");
String fileTypes = m.rs("file_types");
String allowLateSubmissionYn = m.rs("allow_late_submission_yn");

if("".equals(templateNm)) templateNm = m.rs("title");
if("".equals(submissionType)) submissionType = m.rs("submissionType");
if("".equals(fileTypes)) fileTypes = m.rs("fileTypes");
if("".equals(allowLateSubmissionYn)) allowLateSubmissionYn = m.rs("allowLateSubmission");

int totalScore = m.ri("total_score");
int maxFileSize = m.ri("max_file_size");
int latePenalty = m.ri("late_penalty");

if("".equals(m.rs("total_score")) && !"".equals(m.rs("totalScore"))) totalScore = m.ri("totalScore");
if("".equals(m.rs("max_file_size")) && !"".equals(m.rs("maxFileSize"))) maxFileSize = m.ri("maxFileSize");
if("".equals(m.rs("late_penalty")) && !"".equals(m.rs("latePenalty"))) latePenalty = m.ri("latePenalty");

if("true".equalsIgnoreCase(allowLateSubmissionYn) || "Y".equalsIgnoreCase(allowLateSubmissionYn)) allowLateSubmissionYn = "Y";
else allowLateSubmissionYn = "N";

HomeworkTemplateDao homeworkTemplate = new HomeworkTemplateDao();

if("".equals(templateNm)) {
	result.put("rst_code", "1101");
	result.put("rst_message", "template_nm은 필수입니다.");
	result.print();
	return;
}
if(templateNm.length() > 200) {
	result.put("rst_code", "1102");
	result.put("rst_message", "template_nm은 200자 이하로 입력해 주세요.");
	result.print();
	return;
}
if("".equals(description)) {
	result.put("rst_code", "1103");
	result.put("rst_message", "description은 필수입니다.");
	result.print();
	return;
}
if(description.length() > 4000) {
	result.put("rst_code", "1104");
	result.put("rst_message", "description은 4000자 이하로 입력해 주세요.");
	result.print();
	return;
}
if(!homeworkTemplate.isValidTotalScore(totalScore)) {
	result.put("rst_code", "1105");
	result.put("rst_message", "total_score는 0~1000 사이여야 합니다.");
	result.print();
	return;
}
if(!homeworkTemplate.isValidSubmissionType(submissionType)) {
	result.put("rst_code", "1106");
	result.put("rst_message", "submission_type은 file/text/both만 허용됩니다.");
	result.print();
	return;
}
if(fileTypes.length() > 1000) {
	result.put("rst_code", "1107");
	result.put("rst_message", "file_types는 1000자 이하로 입력해 주세요.");
	result.print();
	return;
}
if(!homeworkTemplate.isValidMaxFileSize(maxFileSize)) {
	result.put("rst_code", "1108");
	result.put("rst_message", "max_file_size는 0~2048 사이여야 합니다.");
	result.print();
	return;
}
if(!homeworkTemplate.isValidLatePenalty(latePenalty)) {
	result.put("rst_code", "1109");
	result.put("rst_message", "late_penalty는 0~100 사이여야 합니다.");
	result.print();
	return;
}

if("N".equals(allowLateSubmissionYn)) latePenalty = 0;

String now = m.time("yyyyMMddHHmmss");
int templateId = id;

m.log(
	"tutor_homework_template",
	"save_start id=" + id + ", manager_id=" + userId + ", site_id=" + siteId
	+ ", template_len=" + templateNm.length() + ", desc_len=" + description.length()
	+ ", total_score=" + totalScore + ", submission_type=" + submissionType
	+ ", max_file_size=" + maxFileSize + ", allow_late=" + allowLateSubmissionYn + ", late_penalty=" + latePenalty
);

if(0 < id) {
	DataSet info = homeworkTemplate.find(
		"id = " + id + " AND site_id = " + siteId + " AND manager_id = " + userId + " AND status = 1"
	);
	if(!info.next()) {
		m.log("tutor_homework_template", "save_not_found id=" + id + ", manager_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "4041");
		result.put("rst_message", "수정할 과제 템플릿이 없습니다.");
		result.print();
		return;
	}

	homeworkTemplate.item("template_nm", templateNm);
	homeworkTemplate.item("description", description);
	homeworkTemplate.item("total_score", totalScore);
	homeworkTemplate.item("submission_type", submissionType);
	homeworkTemplate.item("file_types", fileTypes);
	homeworkTemplate.item("max_file_size", maxFileSize);
	homeworkTemplate.item("allow_late_submission_yn", allowLateSubmissionYn);
	homeworkTemplate.item("late_penalty", latePenalty);
	homeworkTemplate.item("mod_date", now);

	if(!homeworkTemplate.update("id = " + id + " AND site_id = " + siteId)) {
		m.log("tutor_homework_template", "save_update_failed id=" + id + ", manager_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "2000");
		result.put("rst_message", "과제 템플릿 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
} else {
	templateId = homeworkTemplate.getSequence();

	homeworkTemplate.item("id", templateId);
	homeworkTemplate.item("site_id", siteId);
	homeworkTemplate.item("manager_id", userId);
	homeworkTemplate.item("template_nm", templateNm);
	homeworkTemplate.item("description", description);
	homeworkTemplate.item("total_score", totalScore);
	homeworkTemplate.item("submission_type", submissionType);
	homeworkTemplate.item("file_types", fileTypes);
	homeworkTemplate.item("max_file_size", maxFileSize);
	homeworkTemplate.item("allow_late_submission_yn", allowLateSubmissionYn);
	homeworkTemplate.item("late_penalty", latePenalty);
	homeworkTemplate.item("reg_date", now);
	homeworkTemplate.item("mod_date", now);
	homeworkTemplate.item("status", 1);

	if(!homeworkTemplate.insert()) {
		m.log("tutor_homework_template", "save_insert_failed id=" + templateId + ", manager_id=" + userId + ", site_id=" + siteId);
		result.put("rst_code", "2000");
		result.put("rst_message", "과제 템플릿 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

m.log(
	"tutor_homework_template",
	"save_success id=" + templateId + ", manager_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", templateId);
result.print();

%>

