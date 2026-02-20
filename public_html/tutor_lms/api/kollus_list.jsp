<%@ page pageEncoding="utf-8" %><%@ page import="java.util.*" %><%@ page import="malgnsoft.json.*" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- sysop/video/kollus_list.jsp 기준으로 콜러스 목록(썸네일 포함)을 project 화면에 내려줘야 합니다.
//- 레슨(DB) 기준이 아니라 콜러스 채널 기준으로 Total 3005 같은 집계를 그대로 맞춥니다.

KollusDao kollus = new KollusDao(siteId);
KollusMediaDao kollusMedia = new KollusMediaDao();
WishlistDao wishlist = new WishlistDao(siteId);
if(2 == m.ri("version")) kollus.setApiVersion("api-vod");

DataSet channels = null;
try { channels = kollus.getChannels(); }
catch(Exception ex) {
	result.put("rst_code", "5001");
	result.put("rst_message", "채널 조회 중 오류가 발생했습니다.");
	result.print();
	return;
}

if(channels == null || 1 > channels.size()) {
	result.put("rst_code", "5002");
	result.put("rst_message", "채널을 불러올 수 없습니다.");
	result.print();
	return;
}

//채널키 결정(=sysop/video/kollus_list.jsp 로직 그대로 유지)
String channelKey = "";
if("user".equals(siteinfo.s("kollus_channel"))) {
	channelKey = kollus.getChannelKey(channels, loginId);
} else {
	channelKey = kollus.getChannelKey(channels, "폴리텍대학");
	channelKey = m.rs("s_channel", channelKey);

	if("".equals(channelKey)) {
		if(!"".equals(siteinfo.s("kollus_channel"))) {
			channelKey = siteinfo.s("kollus_channel");
		} else {
			channels.first();
			channels.next();
			channelKey = channels.s("key");
		}
	}
}

if("".equals(channelKey)) {
	result.put("rst_code", "5003");
	result.put("rst_message", "유효한 채널이 없습니다.");
	result.print();
	return;
}

//카테고리
DataSet categories = null;
try { categories = kollus.getCategories(); }
catch(Exception ex) {
	result.put("rst_code", "5004");
	result.put("rst_message", "카테고리 조회 중 오류가 발생했습니다.");
	result.print();
	return;
}

if(categories == null || 1 > categories.size()) {
	result.put("rst_code", "5005");
	result.put("rst_message", "카테고리를 불러올 수 없습니다.");
	result.print();
	return;
}

HashMap<String, String> categoryMap = new HashMap<String, String>();
while(categories.next()) {
	categoryMap.put(categories.s("key"), categories.s("name"));
}
categories.first();

String categoryKey = "";
if("user".equals(siteinfo.s("kollus_channel"))) {
	categoryKey = kollus.getCategoryKey(categories, loginId);
} else {
	categoryKey = m.rs("s_category");
}
kollus.setCategoryKey(categoryKey);

int pg = m.ri("page") > 0 ? m.ri("page") : 1;
int limit = m.ri("limit") > 0 ? m.ri("limit") : 20;
if(limit > 200) limit = 200;

DataSet list = null;
try { list = kollus.getContents(channelKey, m.rs("s_keyword"), pg, limit); }
catch(Exception ex) {
	result.put("rst_code", "5006");
	result.put("rst_message", "콜러스 목록 조회 중 오류가 발생했습니다.");
	result.print();
	return;
}

int totalNum = kollus.getTotalNum();
int i = 1;
while(list.next()) {
	list.put("__ord", i++);
	list.put("category_nm", categoryMap.containsKey(list.s("category_key")) ? categoryMap.get(list.s("category_key")) : "-");
	list.put("use_encryption_conv", "1".equals(list.s("use_encryption")) ? "Y" : "N");

	if(!"".equals(list.s("duration")) && -1 < list.s("duration").indexOf(":")) {
		String[] duration = m.split(":", list.s("duration"));
		list.put("total_time", m.parseInt(duration[0]) * 60 + m.parseInt(duration[1]));
	} else {
		list.put("duration", "-");
		list.put("total_time", "0");
	}

	list.put("content_width", "0");
	list.put("content_height", "0");
	if(!"".equals(list.s("transcoding_files"))) {
		DataSet tfinfo = Json.decode(list.s("transcoding_files"));
		while(tfinfo.next()) {
			if(-1 < tfinfo.s("media_profile_group_key").toLowerCase().indexOf("pc")) {
				DataSet minfo = Json.decode(tfinfo.s("media_information"));
				while(minfo.next()) {
					DataSet vinfo = Json.decode(minfo.s("video"));
					if(vinfo.next() && !"".equals(vinfo.s("video_screen_size")) && -1 < vinfo.s("video_screen_size").indexOf("x")) {
						String[] size = m.split("x", vinfo.s("video_screen_size"));
						list.put("content_width", size[0]);
						list.put("content_height", size[1]);
					}
				}
			}
		}
	}

	list.put("thumbnail", list.s("snapshot_url"));
	list.put("id", list.s("media_content_key"));

	//왜: 찜 목록 여부는 TB_WISHLIST(숫자 module_id) 기반이므로 매핑 테이블에서 id를 찾아야 합니다.
	DataSet minfo = kollusMedia.find(
		"site_id = " + siteId + " AND media_content_key = ?",
		new String[] { list.s("media_content_key") }
	);
	if(minfo.next()) {
		// 왜: 교수자가 콘텐츠라이브러리에서 제목을 구분용으로 수정한 경우, 콜러스 원본 제목보다 DB 제목을 우선 보여줘야 합니다.
		if(!"".equals(minfo.s("title"))) list.put("title", minfo.s("title"));
		if(!"".equals(minfo.s("snapshot_url"))) {
			list.put("snapshot_url", minfo.s("snapshot_url"));
			list.put("thumbnail", minfo.s("snapshot_url"));
		}
		if(!"".equals(minfo.s("category_key"))) list.put("category_key", minfo.s("category_key"));
		if(!"".equals(minfo.s("category_nm"))) list.put("category_nm", minfo.s("category_nm"));
		if(!"".equals(minfo.s("original_file_name"))) list.put("original_file_name", minfo.s("original_file_name"));
		if(minfo.i("total_time") > 0) list.put("total_time", minfo.i("total_time"));
		if(minfo.i("content_width") > 0) list.put("content_width", minfo.i("content_width"));
		if(minfo.i("content_height") > 0) list.put("content_height", minfo.i("content_height"));

		int mediaId = minfo.i("id");
		list.put("media_id", mediaId);
		list.put("is_favorite", wishlist.isAdded(userId, "kollus", mediaId));
	} else {
		list.put("media_id", 0);
		list.put("is_favorite", false);
	}
}

DataSet channelList = new DataSet();
channels.first();
while(channels.next()) {
	channelList.addRow();
	channelList.put("key", channels.s("key"));
	channelList.put("name", channels.s("name"));
	channelList.put("count", channels.i("count_of_media_contents"));
}

DataSet categoryList = new DataSet();
categories.first();
while(categories.next()) {
	categoryList.addRow();
	categoryList.put("key", categories.s("key"));
	categoryList.put("name", categories.s("name"));
}

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_total", totalNum);
result.put("rst_page", pg);
result.put("rst_limit", limit);
result.put("rst_channel", channelKey);
result.put("rst_category", categoryKey);
result.put("rst_channels", channelList);
result.put("rst_categories", categoryList);
result.put("rst_data", list);
result.print();

%>
