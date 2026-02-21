<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 교수자 LMS > 문제은행에서 문제를 삭제하기 위함입니다.

QuestionDao question = new QuestionDao();

// 파라미터 검증
int questionId = m.ri("id");

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
	m.log("question_bank_delete", "forbidden id=" + questionId + ", manager_id=" + info.i("manager_id") + ", user_id=" + userId);
	result.put("rst_code", "4030");
	result.put("rst_message", "해당 문제를 삭제할 권한이 없습니다.");
	result.print();
	return;
}

// TODO: 시험에서 사용 중인 문제인지 확인 (추후 필요시)

// 소프트 삭제 (status = -1)
question.item("status", -1);

if(!question.update("id = " + questionId + " AND site_id = " + siteId)) {
	m.log("question_bank_delete", "delete_failed id=" + questionId + ", user_id=" + userId);
	result.put("rst_code", "5000");
	result.put("rst_message", "문제 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}
m.log("question_bank_delete", "delete_ok id=" + questionId + ", user_id=" + userId);

result.put("rst_code", "0000");
result.put("rst_message", "문제가 삭제되었습니다.");
result.put("rst_data", questionId);
result.print();

%>
