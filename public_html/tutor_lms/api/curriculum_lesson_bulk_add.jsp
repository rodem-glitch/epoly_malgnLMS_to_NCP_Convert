<%@ page pageEncoding="utf-8" %><%@ page import="org.json.*" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 1~15주차/다수 차시 구성을 한 번에 저장할 수 있어야 합니다.
//- 항목을 하나씩 등록하는 반복 작업을 줄이고, 동일 데이터 재전송 시에도 안정적으로 반영되게 합니다.

if(!m.isPost()) {
	result.put("rst_code", "4050");
	result.put("rst_message", "POST 방식만 허용됩니다.");
	result.print();
	return;
}

int courseId = m.ri("course_id");
String lessonsJson = m.rs("lessons_json").trim();
if(0 == courseId || "".equals(lessonsJson)) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id와 lessons_json이 필요합니다.");
	result.print();
	return;
}

JSONArray items = null;
try {
	items = new JSONArray(lessonsJson);
} catch(Exception e) {
	result.put("rst_code", "1002");
	result.put("rst_message", "lessons_json 형식이 올바르지 않습니다.");
	result.print();
	return;
}
if(null == items || 1 > items.length()) {
	result.put("rst_code", "1003");
	result.put("rst_message", "등록할 레슨 항목이 없습니다.");
	result.print();
	return;
}

CourseTutorDao courseTutor = new CourseTutorDao();
CourseLessonDao courseLesson = new CourseLessonDao();
LessonDao lesson = new LessonDao();

if(!isAdmin) {
	if(0 >= courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId)) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 강의목차를 수정할 권한이 없습니다.");
		result.print();
		return;
	}
}

int nextChapter = 1;
try {
	int maxChapter = courseLesson.getOneInt(
		"SELECT MAX(chapter) FROM " + courseLesson.table
		+ " WHERE course_id = " + courseId + " AND site_id = " + siteId + " AND status = 1"
	);
	if(maxChapter > 0) nextChapter = maxChapter + 1;
} catch(Exception ignore) {}

int inserted = 0;
int updated = 0;
int failed = 0;
JSONArray failedIndexes = new JSONArray();

for(int i = 0; i < items.length(); i++) {
	JSONObject item = items.optJSONObject(i);
	if(null == item) {
		failed++;
		failedIndexes.put(i + 1);
		continue;
	}

	int itemSectionId = item.optInt("section_id", 0);
	int itemChapter = item.optInt("chapter", 0);
	if(itemChapter <= 0) itemChapter = nextChapter++;

	String itemStartDate = item.optString("start_date", "").trim();
	String itemEndDate = item.optString("end_date", "").trim();
	String itemTitle = item.optString("title", "").trim();
	String itemUrl = item.optString("url", "").trim();
	int itemCompleteTime = item.optInt("complete_time", 0);
	int itemLessonId = item.optInt("lesson_id", 0);
	if(itemLessonId <= 0) {
		String rawLessonId = item.optString("lesson_id", "").trim();
		if(!"".equals(rawLessonId)) {
			try { itemLessonId = Integer.parseInt(rawLessonId); } catch(Exception ignore) {}
		}
	}

	if(itemLessonId <= 0 && "".equals(itemUrl)) {
		failed++;
		failedIndexes.put(i + 1);
		m.log("curriculum_lesson_bulk_add", "invalid_item course_id=" + courseId + ", index=" + (i + 1) + ", user_id=" + userId);
		continue;
	}

	//외부 URL이면 레슨 신규 생성
	if(itemLessonId <= 0) {
		int newLessonId = lesson.getSequence();
		lesson.clear();
		lesson.item("id", newLessonId);
		lesson.item("site_id", siteId);
		lesson.item("content_id", 0);
		lesson.item("onoff_type", "N");
		lesson.item("lesson_type", "04");
		lesson.item("lesson_nm", !"".equals(itemTitle) ? itemTitle : ("외부링크 " + newLessonId));
		lesson.item("start_url", itemUrl);
		lesson.item("mobile_a", itemUrl);
		lesson.item("mobile_i", itemUrl);
		lesson.item("total_time", itemCompleteTime > 0 ? itemCompleteTime : 0);
		lesson.item("complete_time", itemCompleteTime > 0 ? itemCompleteTime : 0);
		lesson.item("total_page", 0);
		lesson.item("lesson_hour", 0);
		lesson.item("description", "");
		lesson.item("manager_id", userId);
		lesson.item("use_yn", "Y");
		lesson.item("chat_yn", "N");
		lesson.item("ai_chat_yn", "N");
		lesson.item("sort", 0);
		lesson.item("reg_date", m.time("yyyyMMddHHmmss"));
		lesson.item("status", 1);
		if(!lesson.insert()) {
			failed++;
			failedIndexes.put(i + 1);
			m.log("curriculum_lesson_bulk_add", "external_lesson_insert_failed course_id=" + courseId + ", index=" + (i + 1) + ", user_id=" + userId);
			continue;
		}
		itemLessonId = newLessonId;
	} else {
		if(0 == lesson.findCount("id = " + itemLessonId + " AND site_id = " + siteId + " AND status = 1")) {
			failed++;
			failedIndexes.put(i + 1);
			m.log("curriculum_lesson_bulk_add", "lesson_not_found course_id=" + courseId + ", lesson_id=" + itemLessonId + ", index=" + (i + 1) + ", user_id=" + userId);
			continue;
		}
	}

	//왜: 대량 등록 시에는 이미 연결된 레슨도 "현재 배치(section/chapter/기간)"로 덮어써서 재실행 가능하게 만듭니다.
	String where =
		"course_id = " + courseId
		+ " AND lesson_id = " + itemLessonId
		+ " AND site_id = " + siteId
		+ " AND status != -1";

	int exists = courseLesson.findCount(where);

	if(itemCompleteTime > 0 || !"".equals(itemTitle)) {
		lesson.clear();
		boolean hasLessonUpdate = false;
		if(itemCompleteTime > 0) {
			lesson.item("complete_time", itemCompleteTime);
			hasLessonUpdate = true;
			try {
				DataSet linfo = lesson.find("id = " + itemLessonId + " AND site_id = " + siteId + " AND status != -1", "total_time");
				if(linfo.next() && linfo.i("total_time") <= 0) lesson.item("total_time", itemCompleteTime);
			} catch(Exception ignore) {}
		}
		if(!"".equals(itemTitle)) {
			lesson.item("lesson_nm", itemTitle);
			hasLessonUpdate = true;
		}
		if(hasLessonUpdate) lesson.update("id = " + itemLessonId + " AND site_id = " + siteId + " AND status != -1");
	}

	courseLesson.clear();
	courseLesson.item("section_id", itemSectionId);
	courseLesson.item("chapter", itemChapter);
	courseLesson.item("start_day", 0);
	courseLesson.item("period", 0);
	courseLesson.item("start_date", !"".equals(itemStartDate) ? m.time("yyyyMMdd", itemStartDate) : "");
	courseLesson.item("end_date", !"".equals(itemEndDate) ? m.time("yyyyMMdd", itemEndDate) : "");
	courseLesson.item("start_time", "");
	courseLesson.item("end_time", "");
	courseLesson.item("lesson_hour", 1.00);
	courseLesson.item("progress_yn", "Y");
	courseLesson.item("status", 1);

	if(exists > 0) {
		if(courseLesson.update(where)) {
			updated++;
		} else {
			failed++;
			failedIndexes.put(i + 1);
			m.log("curriculum_lesson_bulk_add", "course_lesson_update_failed course_id=" + courseId + ", lesson_id=" + itemLessonId + ", chapter=" + itemChapter + ", index=" + (i + 1) + ", user_id=" + userId);
		}
	} else {
		courseLesson.item("course_id", courseId);
		courseLesson.item("lesson_id", itemLessonId);
		courseLesson.item("site_id", siteId);
		if(courseLesson.insert()) {
			inserted++;
		} else {
			failed++;
			failedIndexes.put(i + 1);
			m.log("curriculum_lesson_bulk_add", "course_lesson_insert_failed course_id=" + courseId + ", lesson_id=" + itemLessonId + ", chapter=" + itemChapter + ", index=" + (i + 1) + ", user_id=" + userId);
		}
	}
}

m.log(
	"curriculum_lesson_bulk_add",
	"bulk_done course_id=" + courseId
	+ ", total=" + items.length()
	+ ", inserted=" + inserted
	+ ", updated=" + updated
	+ ", failed=" + failed
	+ ", user_id=" + userId
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", inserted);
result.put("rst_inserted", inserted);
result.put("rst_updated", updated);
result.put("rst_failed", failed);
result.put("rst_failed_indexes", failedIndexes);
result.print();

%>
