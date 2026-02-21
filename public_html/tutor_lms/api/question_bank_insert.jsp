<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 교수자 LMS > 문제은행에서 새 문제를 등록하기 위함입니다.
// - 객관식(단일/다중선택), 주관식(단답형/서술형) 유형을 지원합니다.

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
	m.log("question_bank_insert", "score_column_check_failed manager_id=" + userId + ", message=" + e.getMessage());
}
if(!hasScoreColumn) {
	int altered = -1;
	try {
		altered = question.execute(
			"ALTER TABLE " + question.table + " "
			+ " ADD COLUMN score INT NOT NULL DEFAULT 5 COMMENT '문제 배점' "
		);
	} catch(Exception e) {
		m.log("question_bank_insert", "score_column_alter_failed manager_id=" + userId + ", message=" + e.getMessage());
	}
	if(altered == -1) {
		result.put("rst_code", "5001");
		result.put("rst_message", "문제 배점 컬럼이 없어 저장할 수 없습니다. 관리자에게 DB 점검을 요청해 주세요.");
		result.print();
		return;
	}
	m.log("question_bank_insert", "score_column_alter_ok manager_id=" + userId);
}

// 파라미터 수집
int categoryId = m.ri("category_id");
int questionType = m.ri("question_type"); // 1=단일선택, 2=다중선택, 3=단답형, 4=서술형
String questionTitle = m.rs("question");
String questionText = m.rs("question_text");
int grade = m.ri("grade") > 0 ? m.ri("grade") : 3; // 기본 난이도 C
String answer = m.rs("answer");
String description = m.rs("description");
String pointsRaw = m.rs("points");
int score = "".equals(pointsRaw) ? 5 : m.parseInt(pointsRaw);
// 왜: 요구사항이 "기본 공유"이므로 별도 선택이 없으면 공개(Y)로 저장합니다.
String openYn = "N".equals(m.rs("open_yn")) ? "N" : "Y";

// 검증
if(questionType < 1 || questionType > 4) {
	result.put("rst_code", "1001");
	result.put("rst_message", "문제 유형이 올바르지 않습니다. (1=단일선택, 2=다중선택, 3=단답형, 4=서술형)");
	result.print();
	return;
}

if("".equals(questionTitle)) {
	result.put("rst_code", "1002");
	result.put("rst_message", "문제 제목이 필요합니다.");
	result.print();
	return;
}
if(score <= 0) {
	result.put("rst_code", "1003");
	result.put("rst_message", "문제 배점은 1점 이상이어야 합니다.");
	result.print();
	return;
}

// 등록
int newId = question.getSequence();
question.item("id", newId);
question.item("site_id", siteId);
question.item("category_id", categoryId);
question.item("question_type", questionType);
question.item("question", questionTitle);
question.item("question_text", questionText);
question.item("grade", grade);
question.item("score", score);
question.item("answer", answer);
question.item("description", description);
question.item("manager_id", userId);
question.item("open_yn", openYn);
question.item("reg_date", m.time("yyyyMMddHHmmss"));
question.item("status", 1);

// 객관식 보기 처리 (item1~item5)
boolean isChoice = (questionType == 1 || questionType == 2);
if(isChoice) {
	int itemCnt = 0;
	for(int i = 1; i <= 5; i++) {
		String itemText = m.rs("item" + i);
		if(!"".equals(itemText)) {
			question.item("item" + i, itemText);
			itemCnt = i;
		} else {
			question.item("item" + i, "");
		}
		question.item("item" + i + "_file", "");
	}
	// 왜: 객관식인데 보기가 비어있으면 기본 4개로 잡아 저장 오류를 피합니다.
	question.item("item_cnt", itemCnt > 0 ? itemCnt : 4);
} else {
	// 주관식인 경우 보기 컬럼 비움
	question.item("item_cnt", 1);
	for(int i = 1; i <= 5; i++) {
		question.item("item" + i, "");
		question.item("item" + i + "_file", "");
	}
}

if(!question.insert()) {
	m.log("question_bank_insert", "insert_failed id=" + newId + ", manager_id=" + userId + ", category_id=" + categoryId + ", grade=" + grade + ", score=" + score + ", open_yn=" + openYn);
	result.put("rst_code", "5000");
	result.put("rst_message", "문제 등록 중 오류가 발생했습니다.");
	result.print();
	return;
}
m.log("question_bank_insert", "insert_ok id=" + newId + ", manager_id=" + userId + ", category_id=" + categoryId + ", grade=" + grade + ", score=" + score + ", open_yn=" + openYn);

result.put("rst_code", "0000");
result.put("rst_message", "문제가 등록되었습니다.");
result.put("rst_data", newId);
result.print();

%>
