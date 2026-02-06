<%@ page contentType="text/html; charset=utf-8" %><%@ include file="init.jsp" %><%

//객체
WebtvDao webtv = new WebtvDao();
WishlistDao wishlist = new WishlistDao(siteId);

//기본키
String module = m.rs("module");
if("".equals(module)) { m.jsError(_message.get("alert.common.required_key")); return; }

//폼입력
String style = m.rs("s_style", "webzine");
f.addElement("s_style", style, null);

//목록
ListManager lm = new ListManager();
//lm.d(out);
lm.setRequest(request);
lm.setListNum(10);
lm.setTable(
	wishlist.table + " a "
	+ ("webtv".equals(module) ? " INNER JOIN " + webtv.table + " m ON a.module_id = m.id AND m.site_id = " + siteId + " AND m.display_yn = 'Y' AND m.status = 1 " : "")
);
lm.setFields("a.module_id, a.reg_date, m.*");
lm.addWhere("a.user_id = " + userId);
lm.addWhere("a.module = '" + module + "'");
lm.setOrderBy("a.reg_date DESC");

//정보
DataSet list = lm.getDataSet();
while(list.next()) {
	list.put("reg_date_conv", m.time(_message.get("format.datetime.dot"), list.s("reg_date")));
	if("webtv".equals(module)) {
		list.put("webtv_nm_conv", m.cutString(list.s("webtv_nm"), 70));
		
		list.put("subtitle_conv", m.nl2br(list.s("subtitle")));
		list.put("subtitle_conv2", m.nl2br(m.stripTags(list.s("subtitle"))));
		list.put("length_conv", m.strrpad(list.s("length_min"), 2, "0") + ":" + m.strrpad(list.s("length_sec"), 2, "0"));

		if(!"".equals(list.s("webtv_file"))) {
			list.put("webtv_file_url", m.getUploadUrl(list.s("webtv_file")));
		} else if("".equals(list.s("webtv_file_url"))) {
			list.put("webtv_file_url", "/common/images/default/noimage_webtv.jpg");
		}
		
		list.put("hit_cnt_conv", m.nf(list.i("hit_cnt")));
		list.put("open_date_conv", m.time(_message.get("format.datetime.dot"), list.s("open_date")));
		list.put("open_day_conv", m.time(_message.get("format.date.dot"), list.s("open_date")));
	}
}

//출력
p.setLayout("mypage_newmain");
p.setBody("mypage.wishlist_" + module);

p.setLoop("list", list);
p.setVar("pagebar", lm.getPaging());

p.setVar("list_type", "list".equals(style));
p.setVar("webzine_type", "webzine".equals(style));
p.setVar("gallery_type", "gallery".equals(style));

p.setVar("style", style);
p.setVar("module", module);
p.display();

%>