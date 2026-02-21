<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 과제 템플릿 탭에서 더 이상 쓰지 않는 템플릿을 삭제(소프트 삭제)하기 위함입니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int id = m.ri("id");
if(0 == id) {
	result.put("rst_code", "1001");
	result.put("rst_message", "id가 필요합니다.");
	result.print();
	return;
}

HomeworkTemplateDao homeworkTemplate = new HomeworkTemplateDao();

m.log(
	"tutor_homework_template",
	"delete_start id=" + id + ", manager_id=" + userId + ", site_id=" + siteId
);

DataSet info = homeworkTemplate.find(
	"id = " + id + " AND site_id = " + siteId + " AND manager_id = " + userId + " AND status = 1"
);
if(!info.next()) {
	m.log("tutor_homework_template", "delete_not_found id=" + id + ", manager_id=" + userId + ", site_id=" + siteId);
	result.put("rst_code", "4041");
	result.put("rst_message", "삭제할 과제 템플릿이 없습니다.");
	result.print();
	return;
}

homeworkTemplate.item("status", -1);
homeworkTemplate.item("mod_date", m.time("yyyyMMddHHmmss"));

if(!homeworkTemplate.update("id = " + id + " AND site_id = " + siteId)) {
	m.log("tutor_homework_template", "delete_failed id=" + id + ", manager_id=" + userId + ", site_id=" + siteId);
	result.put("rst_code", "2000");
	result.put("rst_message", "과제 템플릿 삭제 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"tutor_homework_template",
	"delete_success id=" + id + ", manager_id=" + userId + ", site_id=" + siteId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", id);
result.print();

%>

