<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과제 템플릿 탭에서 교수자 본인이 저장한 템플릿 목록을 불러오기 위함입니다.

int limit = m.ri("limit");
if(limit < 0) {
	result.put("rst_code", "1100");
	result.put("rst_message", "limit은 0 이상이어야 합니다.");
	result.print();
	return;
}

HomeworkTemplateDao homeworkTemplate = new HomeworkTemplateDao();

m.log(
	"tutor_homework_template",
	"list_start manager_id=" + userId + ", site_id=" + siteId + ", is_admin=" + isAdmin + ", limit=" + limit
);

String limitClause = 0 < limit ? " LIMIT " + limit : "";
DataSet list = homeworkTemplate.query(
	" SELECT id, template_nm, description, total_score, submission_type, file_types, max_file_size "
	+ " , allow_late_submission_yn, late_penalty, reg_date, mod_date "
	+ " FROM " + homeworkTemplate.table + " "
	+ " WHERE site_id = " + siteId + " AND manager_id = " + userId + " AND status = 1 "
	+ " ORDER BY id DESC "
	+ limitClause
);

while(list.next()) {
	list.put("reg_date_conv", !"".equals(list.s("reg_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("reg_date")) : "-");
	list.put("mod_date_conv", !"".equals(list.s("mod_date")) ? m.time("yyyy.MM.dd HH:mm", list.s("mod_date")) : "-");
}

m.log(
	"tutor_homework_template",
	"list_success manager_id=" + userId + ", site_id=" + siteId + ", count=" + list.size()
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>

