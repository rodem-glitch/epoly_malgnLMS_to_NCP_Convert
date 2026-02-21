<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 교수자 LMS > 문제은행에서 기존 문제를 수정하기 위함입니다.

QuestionDao question = new QuestionDao();

// 왜: 정규과정 운영 DB 중 일부는 LM_QUESTION.SCORE 컬럼이 아직 없어서,
//      배점 저장 시 Unknown column 오류가 발생합니다.
boolean hasScoreColumn = false;
try {
	hasScoreColumn = question.getOneInt(
		" SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
		+ " WHERE TABLE_SCHEMA = DATABASE() "
		+ " AND TABLE_NAME = 'LM_QUESTION' "
		+ " AND COLUMN_NAME = 'SCORE' "
	) > 0;
} catch(Exception e) {
	m.log("question_bank_modify", "score_column_check_failed manager_id=" + userId + ", message=" + e.getMessage());
}
if(!hasScoreColumn) {
	int altered = -1;
	try {
		altered = question.execute(
			"ALTER TABLE " + question.table + " "
			+ " ADD COLUMN score INT NOT NULL DEFAULT 5 COMMENT '문제 배점' "
		);
	} catch(Exception e) {
		m.log("question_bank_modify", "score_column_alter_failed manager_id=" + userId + ", message=" + e.getMessage());
	}
	if(altered == -1) {
		result.put("rst_code", "5001");
		result.put("rst_message", "문제 배점 컬럼이 없어 저장할 수 없습니다. 관리자에게 DB 점검을 요청해 주세요.");
		result.print();
		return;
	}
	m.log("question_bank_modify", "score_column_alter_ok manager_id=" + userId);
}

// 파라미터 수집
int questionId = m.ri("id");
int categoryId = m.ri("category_id");
int questionType = m.ri("question_type");
String questionTitle = m.rs("question");
String questionText = m.rs("question_text");
int grade = m.ri("grade");
String answer = m.rs("answer");
String description = m.rs("description");
String pointsRaw = m.rs("points");
String openYn = m.rs("open_yn");

// 검증
if(questionId <= 0) {
	result.put("rst_code", "1001");
	result.put("rst_message", "문제 ID가 필요합니다.");
	result.print();
	return;
}

// 기존 문제 확인
DataSet info = question.find("id = " + questionId + " AND site_id = " + siteId + " AND status = 1");
if(!info.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 문제가 존재하지 않습니다.");
	result.print();
	return;
}

// 권한 확인
if(!isAdmin && info.i("manager_id") != userId) {
	result.put("rst_code", "4030");
	result.put("rst_message", "해당 문제를 수정할 권한이 없습니다.");
	result.print();
	return;
}

// 왜: 문제은행 배점이 저장되지 않으면 시험 배점 합계(100점) 계산이 틀어져 응시가 막힐 수 있습니다.
if(!"".equals(pointsRaw)) {
	int score = m.parseInt(pointsRaw);
	if(score <= 0) {
		result.put("rst_code", "1002");
		result.put("rst_message", "문제 배점은 1점 이상이어야 합니다.");
		result.print();
		return;
	}
	question.item("score", score);
}
if(!"".equals(openYn)) {
	if(!"Y".equals(openYn) && !"N".equals(openYn)) {
		result.put("rst_code", "1003");
		result.put("rst_message", "공개 여부는 Y 또는 N만 입력할 수 있습니다.");
		result.print();
		return;
	}
	question.item("open_yn", openYn);
}

// 수정
if(categoryId > 0) question.item("category_id", categoryId);
if(questionType > 0) question.item("question_type", questionType);
if(!"".equals(questionTitle)) question.item("question", questionTitle);
question.item("question_text", questionText);
if(grade > 0) question.item("grade", grade);
question.item("answer", answer);
question.item("description", description);
// 객관식 보기 처리
int checkType = questionType > 0 ? questionType : info.i("question_type");
boolean isChoice = (checkType == 1 || checkType == 2);
if(isChoice) {
	int itemCnt = 0;
	for(int i = 1; i <= 5; i++) {
		String itemText = m.rs("item" + i);
		question.item("item" + i, itemText);
		if(!"".equals(itemText)) itemCnt = i;
	}
	// 왜: 보기 수가 비어도 최소 4개를 유지해서 화면/DB 불일치를 막습니다.
	question.item("item_cnt", itemCnt > 0 ? itemCnt : 4);
} else {
	// 왜: 주관식으로 바뀌면 기존 보기 정보를 비워서 문제 유형과 맞춥니다.
	question.item("item_cnt", 1);
	for(int i = 1; i <= 5; i++) {
		question.item("item" + i, "");
		question.item("item" + i + "_file", "");
	}
}

if(!question.update("id = " + questionId + " AND site_id = " + siteId)) {
	m.log("question_bank_modify", "update_failed id=" + questionId + ", manager_id=" + userId + ", points_raw=" + pointsRaw + ", open_yn=" + openYn);
	result.put("rst_code", "5000");
	result.put("rst_message", "문제 수정 중 오류가 발생했습니다.");
	result.print();
	return;
}
m.log("question_bank_modify", "update_ok id=" + questionId + ", manager_id=" + userId + ", points_raw=" + pointsRaw + ", open_yn=" + openYn);

result.put("rst_code", "0000");
result.put("rst_message", "문제가 수정되었습니다.");
result.put("rst_data", questionId);
result.print();

%>
