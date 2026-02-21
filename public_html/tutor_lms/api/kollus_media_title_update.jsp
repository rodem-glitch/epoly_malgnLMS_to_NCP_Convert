<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

// 왜 필요한가:
// - 교수자가 콘텐츠라이브러리에서 예전 영상을 빠르게 구분하려면, 콜러스 원본 제목과 별도로 제목을 수정/저장할 수 있어야 합니다.
// - 수정 제목은 TB_KOLLUS_MEDIA에 저장해 전체/찜 목록에서 동일하게 재사용합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

String mediaKey = m.rs("media_content_key").trim();
String title = m.rs("title").trim();
String snapshotUrl = m.rs("snapshot_url").trim();
String categoryKey = m.rs("category_key").trim();
String categoryNm = m.rs("category_nm").trim();
String originalFileName = m.rs("original_file_name").trim();
int totalTime = m.ri("total_time");
int contentWidth = m.ri("content_width");
int contentHeight = m.ri("content_height");

if("".equals(mediaKey)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "media_content_key가 필요합니다.");
	result.print();
	return;
}

if("".equals(title)) {
	result.put("rst_code", "1002");
	result.put("rst_message", "title이 필요합니다.");
	result.print();
	return;
}

if(title.length() > 255) {
	result.put("rst_code", "1003");
	result.put("rst_message", "title은 255자 이하여야 합니다.");
	result.print();
	return;
}

KollusMediaDao kollusMedia = new KollusMediaDao();
DataSet info = kollusMedia.find(
	"site_id = " + siteId + " AND media_content_key = ?",
	new Object[] { mediaKey }
);

int mediaId = 0;
String now = m.time("yyyyMMddHHmmss");

if(info.next()) {
	mediaId = info.i("id");

	kollusMedia.item("title", title);
	// 왜: 수정 API는 "제목 변경"이 주목적이므로, 함께 온 메타값만 선택적으로 반영해 기존 캐시를 지웁니다.
	if(!"".equals(snapshotUrl)) kollusMedia.item("snapshot_url", snapshotUrl);
	if(!"".equals(categoryKey)) kollusMedia.item("category_key", categoryKey);
	if(!"".equals(categoryNm)) kollusMedia.item("category_nm", categoryNm);
	if(!"".equals(originalFileName)) kollusMedia.item("original_file_name", originalFileName);
	if(totalTime > 0) kollusMedia.item("total_time", totalTime);
	if(contentWidth > 0) kollusMedia.item("content_width", contentWidth);
	if(contentHeight > 0) kollusMedia.item("content_height", contentHeight);
	kollusMedia.item("mod_date", now);

	if(!kollusMedia.update("id = " + mediaId + " AND site_id = " + siteId)) {
		result.put("rst_code", "2001");
		result.put("rst_message", "제목 수정 중 오류가 발생했습니다.");
		result.print();
		return;
	}
} else {
	mediaId = kollusMedia.getSequence();

	kollusMedia.item("id", mediaId);
	kollusMedia.item("site_id", siteId);
	kollusMedia.item("media_content_key", mediaKey);
	kollusMedia.item("title", title);
	kollusMedia.item("snapshot_url", snapshotUrl);
	kollusMedia.item("category_key", categoryKey);
	kollusMedia.item("category_nm", categoryNm);
	kollusMedia.item("original_file_name", originalFileName);
	kollusMedia.item("total_time", totalTime > 0 ? totalTime : 0);
	kollusMedia.item("content_width", contentWidth > 0 ? contentWidth : 0);
	kollusMedia.item("content_height", contentHeight > 0 ? contentHeight : 0);
	kollusMedia.item("reg_date", now);
	kollusMedia.item("mod_date", now);

	if(!kollusMedia.insert()) {
		result.put("rst_code", "2000");
		result.put("rst_message", "제목 수정용 미디어 저장 중 오류가 발생했습니다.");
		result.print();
		return;
	}
}

m.log(
	"kollus_media_title_update",
	"site_id=" + siteId
	+ ", user_id=" + userId
	+ ", media_id=" + mediaId
	+ ", media_key=" + mediaKey
	+ ", title_len=" + title.length()
	+ ", has_snapshot=" + (!"".equals(snapshotUrl) ? "Y" : "N")
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", mediaId);
result.print();

%>
