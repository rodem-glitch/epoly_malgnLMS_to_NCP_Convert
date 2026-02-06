<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
BookUserDao bookUser = new BookUserDao();
BookDao book = new BookDao();
CourseUserDao courseUser = new CourseUserDao();
CourseDao course = new CourseDao();

String ctype = m.rs("ctype", "1");
String today = m.time("yyyyMMdd");

//수강중인 과정
DataSet list1 = bookUser.query(
	" SELECT a.*, b.book_nm "
	+ " FROM " + bookUser.table + " a "
	+ " INNER JOIN " + book.table + " b ON a.book_id = b.id "
	+ " WHERE a.user_id = " + userId + " AND a.status IN (0, 1, 3) "
	+ " AND (a.permanent_yn = 'Y' OR a.end_date >= '" + today + "') "
	+ " ORDER BY a.start_date ASC, a.id DESC "
);
while(list1.next()) {
	list1.put("start_date_conv", m.time(_message.get("format.date.dot"), list1.s("start_date")));
	list1.put("end_date_conv", m.time(_message.get("format.date.dot"), list1.s("end_date")));
	list1.put("study_date_conv", list1.s("start_date_conv") + " - " + list1.s("end_date_conv"));
	list1.put("book_nm_conv", m.cutString(list1.s("book_nm"), 60));

	String status = "";
	boolean isOpen = false;
	if(list1.i("status") == 0) status = "승인대기";
	else if(0 > m.diffDate("D", list1.s("start_date"), today)) status = "열람대기";
	else {
		if(list1.b("permanent_yn")) status = "영구소장";
		else status = "대여중";

		isOpen = true;
	}

	list1.put("status_conv", status);
	list1.put("open_block", isOpen);
}

//종료된 과정
DataSet list2 = bookUser.query(
	" SELECT a.*, b.book_nm "
	+ " FROM " + bookUser.table + " a "
	+ " INNER JOIN " + book.table + " b ON a.book_id = b.id "
	+ " WHERE a.user_id = " + userId + " AND a.status IN (1, 3) "
	+ " AND a.end_date < '" + today + "' "
	+ " ORDER BY a.end_date DESC, a.id DESC "
);
while(list2.next()) {
	list2.put("start_date_conv", m.time(_message.get("format.date.dot"), list2.s("start_date")));
	list2.put("end_date_conv", m.time(_message.get("format.date.dot"), list2.s("end_date")));
	list2.put("study_date_conv", list2.s("start_date_conv") + " - " + list2.s("end_date_conv"));
	list2.put("book_nm_conv", m.cutString(list2.s("book_nm"), 60));
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.book_list");
p.setVar("p_title", "내서재");

p.setLoop("list1", list1);
p.setLoop("list2", list2);

p.display();

%>