<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 콜러스 목록의 media_content_key는 문자열이라서, 강의목차에는 레슨 ID(숫자)로 변환/등록이 필요합니다.
//- 이미 등록된 레슨이 있으면 그대로 쓰고, 없으면 새로 생성합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

String mediaKey = m.rs("media_content_key").trim();
String title = m.rs("title").trim();
int totalTime = m.ri("total_time");
int contentWidth = m.ri("content_width");
int contentHeight = m.ri("content_height");

if("".equals(mediaKey)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "media_content_key가 필요합니다.");
	result.print();
	return;
}

// 왜: 추천 데이터는 TB_KOLLUS_TRANSCRIPT 기준으로 들어오는데, 프론트에서 total_time이 비면 0으로 전달될 수 있습니다.
//     이때 업서트 단계에서 한 번 더 보강해야 기존/신규 레슨 모두 인정시간 자동 세팅이 일관됩니다.
if(totalTime <= 0) {
	DataObject transcript = new DataObject("TB_KOLLUS_TRANSCRIPT");
	DataSet tinfo = transcript.find(
		"site_id = " + siteId + " AND media_content_key = ?",
		new Object[] { mediaKey }
	);
	if(tinfo.next() && tinfo.i("duration_seconds") > 0) {
		totalTime = (int)Math.ceil(tinfo.i("duration_seconds") / 60.0d);
	}
}

LessonDao lesson = new LessonDao();
DataSet info = lesson.find(
	"site_id = " + siteId + " AND start_url = ? AND lesson_type = '05' AND status != -1",
	new Object[] { mediaKey }
);
if(info.next()) {
	// 왜: 과거에 total_time 없이 등록된 레슨이 있으면 추천/차시 추가에서 인정시간 자동 세팅이 계속 비게 됩니다.
	//     기존 레슨 재사용 시에도 "비어 있는 메타"만 최소 보정해 동일 키(media_key)의 동작을 안정화합니다.
	boolean needUpdate = false;

	if(!"".equals(title) && "".equals(info.s("lesson_nm"))) {
		lesson.item("lesson_nm", title);
		needUpdate = true;
	}
	if(totalTime > 0) {
		if(info.i("total_time") <= 0) {
			lesson.item("total_time", totalTime);
			needUpdate = true;
		}
		if(info.i("complete_time") <= 0) {
			lesson.item("complete_time", totalTime);
			needUpdate = true;
		}
	}
	if(contentWidth > 0 && info.i("content_width") <= 0) {
		lesson.item("content_width", contentWidth);
		needUpdate = true;
	}
	if(contentHeight > 0 && info.i("content_height") <= 0) {
		lesson.item("content_height", contentHeight);
		needUpdate = true;
	}

	if(needUpdate) {
		if(!lesson.update("id = " + info.i("id") + " AND site_id = " + siteId)) {
			result.put("rst_code", "2001");
			result.put("rst_message", "기존 콜러스 레슨 보정 중 오류가 발생했습니다.");
			result.print();
			return;
		}
	}

	m.log(
		"kollus_lesson_upsert",
		"mode=existing, lesson_id=" + info.i("id")
		+ ", media_key=" + mediaKey
		+ ", input_total_time=" + totalTime
		+ ", db_total_time=" + info.i("total_time")
		+ ", updated=" + (needUpdate ? "Y" : "N")
	);

	result.put("rst_code", "0000");
	result.put("rst_message", "성공");
	result.put("rst_data", info.i("id"));
	result.print();
	return;
}

int lessonId = lesson.getSequence();
lesson.item("id", lessonId);
lesson.item("site_id", siteId);
lesson.item("content_id", 0);
lesson.item("lesson_nm", !"".equals(title) ? title : ("콜러스 " + mediaKey));
lesson.item("onoff_type", "N");
lesson.item("lesson_type", "05");
lesson.item("author", "");
lesson.item("start_url", mediaKey);
lesson.item("mobile_a", mediaKey);
lesson.item("mobile_i", mediaKey);
lesson.item("total_page", 0);
lesson.item("total_time", totalTime);
lesson.item("complete_time", totalTime);
lesson.item("content_width", contentWidth);
lesson.item("content_height", contentHeight);
lesson.item("description", "");
lesson.item("manager_id", userId);
lesson.item("use_yn", "Y");
lesson.item("sort", 0);
lesson.item("reg_date", m.time("yyyyMMddHHmmss"));
lesson.item("status", 1);

if(!lesson.insert()) {
	result.put("rst_code", "2000");
	result.put("rst_message", "콜러스 레슨 생성 중 오류가 발생했습니다.");
	result.print();
	return;
}

m.log(
	"kollus_lesson_upsert",
	"mode=inserted, lesson_id=" + lessonId
	+ ", media_key=" + mediaKey
	+ ", input_total_time=" + totalTime
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", lessonId);
result.print();

%>
