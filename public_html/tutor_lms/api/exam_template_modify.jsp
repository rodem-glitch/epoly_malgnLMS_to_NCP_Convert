<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 교수자 LMS > 시험관리에서 시험 템플릿을 수정하기 위함입니다.

ExamDao exam = new ExamDao();
QuestionDao question = new QuestionDao();

// 왜: 시험 배점 계산은 LM_QUESTION.SCORE를 기준으로 하므로, 컬럼이 없으면 저장 자체가 불가능합니다.
boolean hasScoreColumn = false;
try {
	hasScoreColumn = question.getOneInt(
		" SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
		+ " WHERE TABLE_SCHEMA = DATABASE() "
		+ " AND TABLE_NAME = 'LM_QUESTION' "
		+ " AND COLUMN_NAME = 'SCORE' "
	) > 0;
} catch(Exception e) {
	m.log("exam_template_modify", "score_column_check_failed manager_id=" + userId + ", message=" + e.getMessage());
}
if(!hasScoreColumn) {
	int altered = -1;
	try {
		altered = question.execute(
			"ALTER TABLE " + question.table + " "
			+ " ADD COLUMN score INT NOT NULL DEFAULT 5 COMMENT '문제 배점' "
		);
	} catch(Exception e) {
		m.log("exam_template_modify", "score_column_alter_failed manager_id=" + userId + ", message=" + e.getMessage());
	}
	if(altered == -1) {
		result.put("rst_code", "5001");
		result.put("rst_message", "문제 배점 컬럼이 없어 시험을 저장할 수 없습니다. 관리자에게 DB 점검을 요청해 주세요.");
		result.print();
		return;
	}
	m.log("exam_template_modify", "score_column_alter_ok manager_id=" + userId);
}

// 파라미터
int examId = m.ri("id");
String examName = m.rs("exam_nm");
int examTime = m.ri("exam_time");
String shuffleYn = m.rs("shuffle_yn");
int passingScore = m.ri("passing_score");
String questionIds = m.rs("question_ids");
String content = m.rs("content");

// 검증
if(examId <= 0) {
	result.put("rst_code", "1001");
	result.put("rst_message", "시험 ID가 필요합니다.");
	result.print();
	return;
}

// 기존 시험 확인
DataSet info = exam.find("id = " + examId + " AND site_id = " + siteId + " AND status != -1");
if(!info.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 시험이 존재하지 않습니다.");
	result.print();
	return;
}

// 권한 확인
if(!isAdmin && info.i("manager_id") != userId && info.i("manager_id") != -99) {
	result.put("rst_code", "4030");
	result.put("rst_message", "해당 시험을 수정할 권한이 없습니다.");
	result.print();
	return;
}

// 수정
if(!"".equals(examName)) exam.item("exam_nm", examName);
if(examTime > 0) exam.item("exam_time", examTime);
if(!"".equals(shuffleYn)) exam.item("shuffle_yn", "Y".equals(shuffleYn) ? "Y" : "N");

// 왜: 문제 목록이 바뀌면 난이도별 문항수/배점도 같이 맞춰야 합니다.
ArrayList<String> questionIdList = new ArrayList<String>();
if(!"".equals(questionIds)) {
	String[] rawIds = questionIds.split(",");
	for(int i = 0; i < rawIds.length; i++) {
		String qid = rawIds[i].trim();
		if(qid.matches("\\d+")) questionIdList.add(qid);
	}
}
if(questionIdList.size() == 0) {
	result.put("rst_code", "1002");
	result.put("rst_message", "출제할 문제를 1개 이상 선택해 주세요.");
	result.print();
	return;
}

int[] mcnt = new int[7];
int[] tcnt = new int[7];
int[] assigns = new int[7];
boolean[] assignedByGrade = new boolean[7];
int questionCntFinal = 0;
int totalScore = 0;
Vector<String> invalidScoreLogs = new Vector<String>();
Vector<String> gradeScoreMismatchLogs = new Vector<String>();
if(questionIdList.size() > 0) {
	String[] idArr = (String[]) questionIdList.toArray(new String[0]);
	// 왜: 공개/비공개 정책이 있어도 직접 question_ids를 넣어 비공개 문제를 우회 선택하지 못하게 차단합니다.
	String questionWhere = "site_id = " + siteId + " AND status != -1 AND id IN (" + m.join(",", idArr) + ")";
	if(!isAdmin) questionWhere += " AND (manager_id = " + userId + " OR open_yn = 'Y')";
	DataSet qlist = question.find(questionWhere);
	while(qlist.next()) {
		questionCntFinal++;
		int grade = qlist.i("grade");
		if(grade < 1 || grade > 6) grade = 1;
		int qscore = qlist.i("score");

		String qtype = qlist.s("question_type");
		if("1".equals(qtype) || "2".equals(qtype)) mcnt[grade]++;
		else tcnt[grade]++;

		// 왜: 수정 시에도 배점 합계/난이도별 배점 규칙이 깨지면 학생 응시가 막히므로 즉시 차단합니다.
		if(qscore <= 0) {
			invalidScoreLogs.add("question_id=" + qlist.i("id") + ", grade=" + grade + ", score=" + qscore);
			continue;
		}
		if(!assignedByGrade[grade]) {
			assigns[grade] = qscore;
			assignedByGrade[grade] = true;
		} else if(assigns[grade] != qscore) {
			gradeScoreMismatchLogs.add("grade=" + grade + ", question_id=" + qlist.i("id") + ", score=" + qscore + ", expected=" + assigns[grade]);
		}
		totalScore += qscore;
	}
}

if(questionCntFinal != questionIdList.size()) {
	m.log("exam_template_modify", "invalid_question_selection exam_id=" + examId + ", manager_id=" + userId + ", selected_cnt=" + questionIdList.size() + ", found_cnt=" + questionCntFinal + ", visibility_checked=Y, question_ids=" + m.join(",", (String[]) questionIdList.toArray(new String[0])));
	result.put("rst_code", "1003");
	result.put("rst_message", "선택한 문제 중 사용할 수 없는 문제가 포함되어 있습니다. 문제 목록을 다시 확인해 주세요.");
	result.print();
	return;
}
if(invalidScoreLogs.size() > 0) {
	m.log("exam_template_modify", "invalid_question_score exam_id=" + examId + ", manager_id=" + userId + ", detail=" + m.join(" | ", invalidScoreLogs.toArray()));
	result.put("rst_code", "1004");
	result.put("rst_message", "선택한 문제의 배점이 비어 있습니다. 문제은행에서 배점을 먼저 설정해 주세요.");
	result.print();
	return;
}
if(gradeScoreMismatchLogs.size() > 0) {
	m.log("exam_template_modify", "grade_score_mismatch exam_id=" + examId + ", manager_id=" + userId + ", detail=" + m.join(" | ", gradeScoreMismatchLogs.toArray()));
	result.put("rst_code", "1005");
	result.put("rst_message", "같은 난이도 문제의 배점이 서로 다릅니다. 난이도별 배점을 동일하게 맞춰 주세요.");
	result.print();
	return;
}
if(totalScore != 100) {
	m.log("exam_template_modify", "invalid_total_score exam_id=" + examId + ", manager_id=" + userId + ", total_score=" + totalScore + ", passing_score=" + passingScore + ", question_ids=" + m.join(",", (String[]) questionIdList.toArray(new String[0])));
	result.put("rst_code", "1006");
	result.put("rst_message", "선택한 문제 배점의 합계가 100점이어야 합니다.");
	result.print();
	return;
}

String rangeIdx = questionIdList.size() > 0 ? m.join(",", (String[]) questionIdList.toArray(new String[0])) : "";
exam.item("range_idx", rangeIdx);
exam.item("question_cnt", questionCntFinal);

exam.item("mcnt1", mcnt[1]);
exam.item("mcnt2", mcnt[2]);
exam.item("mcnt3", mcnt[3]);
exam.item("mcnt4", mcnt[4]);
exam.item("mcnt5", mcnt[5]);
exam.item("mcnt6", mcnt[6]);
exam.item("tcnt1", tcnt[1]);
exam.item("tcnt2", tcnt[2]);
exam.item("tcnt3", tcnt[3]);
exam.item("tcnt4", tcnt[4]);
exam.item("tcnt5", tcnt[5]);
exam.item("tcnt6", tcnt[6]);

exam.item("assign1", assigns[1]);
exam.item("assign2", assigns[2]);
exam.item("assign3", assigns[3]);
exam.item("assign4", assigns[4]);
exam.item("assign5", assigns[5]);
exam.item("assign6", assigns[6]);
exam.item("content", content);

if(!exam.update("id = " + examId + " AND site_id = " + siteId)) {
	m.log("exam_template_modify", "update_failed exam_id=" + examId + ", manager_id=" + userId + ", question_cnt=" + questionCntFinal + ", total_score=" + totalScore);
	result.put("rst_code", "5000");
	result.put("rst_message", "시험 수정 중 오류가 발생했습니다.");
	result.print();
	return;
}
m.log("exam_template_modify", "update_ok exam_id=" + examId + ", manager_id=" + userId + ", question_cnt=" + questionCntFinal + ", total_score=" + totalScore);

result.put("rst_code", "0000");
result.put("rst_message", "시험이 수정되었습니다.");
result.put("rst_data", examId);
result.print();

%>
