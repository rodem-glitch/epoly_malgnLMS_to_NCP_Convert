<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 과목 복사 모달에서 담당 교수/강사를 전체 목록으로 선택할 수 있어야 합니다.
// - 단, 교수자는 본인 과목만 복사할 수 있으므로 본인 1명만 내려 권한 오남용을 막습니다.

m.log(
	"tutor_list",
	"list_start request_user_id=" + userId
	+ ", site_id=" + siteId
	+ ", is_admin=" + isAdmin
);

TutorDao tutor = new TutorDao();

DataSet list = null;
if(isAdmin) {
	list = tutor.query(
		" SELECT user_id, tutor_nm "
		+ " FROM " + tutor.table
		+ " WHERE site_id = " + siteId + " AND status = 1 "
		+ " ORDER BY sort ASC, tutor_nm ASC "
	);
} else {
	list = tutor.query(
		" SELECT user_id, tutor_nm "
		+ " FROM " + tutor.table
		+ " WHERE site_id = " + siteId + " AND status = 1 "
		+ " AND user_id = " + userId
		+ " ORDER BY tutor_nm ASC "
	);
	if(0 >= list.size()) {
		m.log(
			"tutor_list",
			"list_empty_for_tutor request_user_id=" + userId
			+ ", site_id=" + siteId
		);
		result.put("rst_code", "4041");
		result.put("rst_message", "담당 교수/강사 정보를 찾을 수 없습니다.");
		result.print();
		return;
	}
}

m.log(
	"tutor_list",
	"list_ok request_user_id=" + userId
	+ ", site_id=" + siteId
	+ ", is_admin=" + isAdmin
	+ ", count=" + list.size()
);
result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_count", list.size());
result.put("rst_data", list);
result.print();

%>
