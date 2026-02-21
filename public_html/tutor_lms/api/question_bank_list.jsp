<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 교수자 LMS > 시험관리 > 문제은행에서 문제 목록을 조회하기 위함입니다.

QuestionDao question = new QuestionDao();
QuestionCategoryDao category = new QuestionCategoryDao();

// 필터 파라미터
int categoryId = m.ri("category_id");
int questionType = m.ri("question_type");
String keyword = m.rs("s_keyword");
boolean mineOnly = "Y".equalsIgnoreCase(m.rs("mine_only"));
int limit = m.ri("limit") > 0 ? m.ri("limit") : 50;
int pageNum = m.ri("page") > 0 ? m.ri("page") : 1;

// 목록 조회 (ListManager 사용 - 페이징 지원)
ListManager lm = new ListManager();
lm.setRequest(request);
lm.setListNum(limit);
lm.setTable(
	question.table + " a " +
	" LEFT JOIN " + category.table + " c ON a.category_id = c.id "
);
lm.setFields("a.*, c.category_nm");
lm.addWhere("a.site_id = " + siteId);
lm.addWhere("a.status = 1");

// 교수자는 본인 문제 + 공개된 문제를 조회
if(mineOnly) {
	lm.addWhere("a.manager_id = " + userId);
} else if(!isAdmin) {
	lm.addWhere("(a.manager_id = " + userId + " OR a.open_yn = 'Y')");
}

m.log("tutor_question_bank_list", "user_id=" + userId + ", is_admin=" + (isAdmin ? "Y" : "N") + ", mine_only=" + (mineOnly ? "Y" : "N"));

// 카테고리 필터
if(categoryId > 0) {
	DataSet categories = category.getList(siteId);
	String[] childNodes = category.getChildNodes(String.valueOf(categoryId), categories);
	if(childNodes.length > 0) {
		lm.addWhere("a.category_id IN ('" + m.join("','", childNodes) + "')");
	}
}

// 문제 유형 필터
if(questionType > 0) {
	lm.addSearch("a.question_type", String.valueOf(questionType));
}

// 키워드 검색
if(!"".equals(keyword)) {
	lm.addSearch("a.question,a.question_text", keyword, "LIKE");
}

lm.setOrderBy("a.id DESC");

// 포맷팅
DataSet list = lm.getDataSet();
while(list.next()) {
	// 문제 유형 변환
	int qType = list.i("question_type");
	String typeLabel = "";
	if(qType == 1) typeLabel = "단일선택";
	else if(qType == 2) typeLabel = "다중선택";
	else if(qType == 3) typeLabel = "단답형";
	else if(qType == 4) typeLabel = "서술형";
	list.put("question_type_conv", typeLabel);
	
	// 난이도 변환
	list.put("grade_conv", m.getItem(list.s("grade"), question.grades));
	
	// 등록일 변환
	list.put("reg_date_conv", m.time("yyyy.MM.dd", list.s("reg_date")));

	// 배점 (score 필드가 없으면 기본값)
	if(list.i("score") <= 0) list.put("score", 5);
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_total", lm.getTotalNum());
result.put("rst_page", pageNum);
result.put("rst_limit", limit);
result.put("rst_data", list);
result.print();

%>
