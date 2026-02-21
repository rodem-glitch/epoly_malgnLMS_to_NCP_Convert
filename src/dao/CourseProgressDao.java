package dao;

import malgnsoft.db.*;
import malgnsoft.util.*;

public class CourseProgressDao extends DataObject {

	public String[] attendList = { "Y=>O", "N=>X" };
	private int siteId = 0;
	private int studyTime = 0;
	private int totalPlayTime = 0;
	private int currTime = 0;
	private String currPage = "";

	public CourseProgressDao() {
		this.table = "LM_COURSE_PROGRESS";
		this.PK = "course_user_id,lesson_id";
	}

	public CourseProgressDao(int siteId) {
		this.table = "LM_COURSE_PROGRESS";
		this.PK = "course_user_id,lesson_id";
		this.siteId = siteId;
	}

	public void setSiteId(int siteId) {
		this.siteId = siteId;
	}
	
	public void setStudyTime(int time) {
		if(time > 0) this.studyTime = time;
	}
	
	public void setTotalPlayTime(int time) {
		if(time > 0) this.totalPlayTime = time;
	}

	public void setCurrTime(int time) {
		if(time > 0) this.currTime = time;
	}

	public void setCurrPage(String page) {
		this.currPage = page;
	}

	public void initProgress(int cuid, int lid, int cid, int uid, int chapter, String type) {
		this.item("site_id", siteId);
		this.item("lesson_type", type);
		this.item("course_user_id", cuid);
		this.item("lesson_id", lid);
		this.item("chapter", chapter);
		this.item("course_id", cid);
		this.item("user_id", uid);
		this.item("reg_date", Malgn.time("yyyyMMddHHmmss"));
		this.item("status", 1);
		this.insert();
	}

	public int updateProgress(int cuid, int lid, int chapter) {

		CourseDao course = new CourseDao();
		CourseLessonDao courseLesson = new CourseLessonDao();
		CourseUserDao courseUser = new CourseUserDao();
		LessonDao lesson = new LessonDao();

		//수강생정보
		DataSet cuinfo = courseUser.query(
			" SELECT a.course_id, a.user_id, a.end_date, c.period_yn "
			+ " FROM " + courseUser.table + " a "
			+ " INNER JOIN " + course.table + " c ON a.course_id = c.id "
			+ " WHERE a.id = " + cuid + " AND a.status IN (1, 3) "
		);
		if(!cuinfo.next()) return -1;

		//제한-학습종료일 경우 진도율을 저장하지 않음
		if(0 < Malgn.diffDate("D", cuinfo.s("end_date"), Malgn.time("yyyyMMdd"))) return -2;

		//제한-차시별학습기간을 벗어날 경우 진도율을 저장하지 않음
		if("Y".equals(cuinfo.s("period_yn"))) {
			DataSet clinfo = courseLesson.find("course_id = ? AND lesson_id = ? AND chapter = ?", new Integer[] {cuinfo.i("course_id"), lid, chapter}, "start_date, end_date, start_time, end_time");
			if(!clinfo.next()) return -7;
			
			String startDateTime = clinfo.s("start_date") + (clinfo.s("start_time").length() == 6 ? clinfo.s("start_time") : "000000");
			String endDateTime = clinfo.s("end_date") + (clinfo.s("end_time").length() == 6 ? clinfo.s("end_time") : "235959");
			String now = Malgn.time("yyyyMMddHHmmss");

			if(0 > Malgn.diffDate("S", startDateTime, now)) return -8;
			else if(0 < Malgn.diffDate("S", endDateTime, now)) return -9;
		}

		//정보-차시
		DataSet linfo = lesson.query(
			"SELECT a.lesson_type, a.total_time, a.complete_time, a.total_page "
			+ " FROM " + lesson.table + " a "
			+ " WHERE a.id = " + lid + " AND a.status = 1 "
			+ " AND EXISTS (SELECT 1 FROM " + courseLesson.table + " WHERE course_id = " + cuinfo.i("course_id") + " AND lesson_id = " + lid + " AND status = 1)"
		);
		if(!linfo.next()) return -3;
		linfo.put("total_time", linfo.i("total_time") * 60);
		linfo.put("complete_time", linfo.i("complete_time") * 60);

		//진도저장
		int viewCnt = 1;
		int lastTime = 0;
		int studyPage = 0;
		String paragraph = "";
		String completeYN = "N";
		String preCompleteYN = "N";

		//정보-진도
		boolean exists = false;
		DataSet cpinfo = this.find("course_user_id = " + cuid + " AND lesson_id = " + lid + " AND status = 1", "study_time, last_time, paragraph, view_cnt, complete_yn, reg_date");
		if(cpinfo.next()) {
			exists = true;
			viewCnt += cpinfo.i("view_cnt");
			paragraph = cpinfo.s("paragraph");
			preCompleteYN = cpinfo.s("complete_yn");
			lastTime = currTime > cpinfo.i("last_time") ? currTime : cpinfo.i("last_time");

			if(1 > totalPlayTime) {
				//일반
				studyTime = cpinfo.i("study_time") + studyTime;
				//if(studyTime > (Malgn.getUnixTime() - Malgn.getUnixTime(cpinfo.s("reg_date")) + 60)) return -6; //너무 짮은 시간에 학습시간이 많은 경우
			} else {
				//콜러스 플레이타임 이용
				studyTime = cpinfo.i("study_time") > totalPlayTime ? cpinfo.i("study_time") : totalPlayTime;
			}
		}

		//진도율 계산
		double ratio = 100.0;
		if("02".equals(linfo.s("lesson_type"))) {
			// WBT Contents
			if(paragraph.indexOf("'" + currPage + "'") == -1) {
				paragraph += ("".equals(paragraph) ? "" : ",") + "'" + currPage + "'";
			}
			studyPage = paragraph.split(",").length;
			if(linfo.i("total_time") > 0) {
				double ratio1 = linfo.i("total_page") > 0 ? Math.min(100.0, (studyPage / linfo.d("total_page")) * 100) : 100.0;
				double ratio2 = studyTime >= linfo.i("complete_time") ? 100.0 : Math.min(100.0, (studyTime / linfo.d("total_time")) * 100);
				ratio = Math.min(ratio1, ratio2);
			} else if(linfo.i("total_page") > 0) {
				ratio = Math.min(100.0, (studyPage / linfo.d("total_page")) * 100);
			}
		} else if(linfo.i("complete_time") == 0) {
			// Direct Complete
			ratio = 100.0;
			completeYN = "Y";
		} else if("04".equals(linfo.s("lesson_type"))) {
			// Link
			if(linfo.i("total_time") > 0 && studyTime < linfo.i("complete_time")) {
				ratio = Math.min(100.0, (studyTime / linfo.d("total_time")) * 100);
			}
		} else if("15".equals(linfo.s("lesson_type"))){
			//ktRemote
			if(linfo.i("total_time") > 0 && studyTime < linfo.i("complete_time")) {
				ratio = Math.min(100.0, (studyTime / linfo.d("total_time")) * 100);
			}
		} else {
			// Wecandeo, Kollus, MP4
			int ratioTime = Math.min(studyTime, lastTime);
			if(linfo.i("total_time") > 0 && ratioTime < linfo.i("complete_time")) {
				ratio = Math.min(100.0, (ratioTime / linfo.d("total_time")) * 100);
			}
		}
		if(ratio >= 100.0) completeYN = "Y";

		// DB 등록 또는 수정
		this.item("chapter", chapter);
		this.item("study_time", studyTime);
		this.item("curr_time", currTime);
		this.item("last_time", lastTime);
		this.item("ratio", ratio);
		this.item("view_cnt", viewCnt);
		this.item("last_date", Malgn.time("yyyyMMddHHmmss"));
		this.item("status", 1);

		if(!"".equals(currPage)) {
			this.item("curr_page", currPage);
			this.item("study_page", studyPage);
			this.item("paragraph", paragraph);
		}
		if("N".equals(preCompleteYN) && "Y".equals(completeYN)) {
			this.item("ratio", 100.0);
			this.item("complete_yn", "Y");
			this.item("complete_date", Malgn.time("yyyyMMddHHmmss"));
		}
		if(exists) {
			if(!this.update("course_user_id = " + cuid + " AND lesson_id = " + lid)) return -4;
		} else {
			this.item("course_user_id", cuid);
			this.item("lesson_id", lid);
			this.item("lesson_type", linfo.s("lesson_type"));
			this.item("course_id", cuinfo.i("course_id"));
			this.item("user_id", cuinfo.i("user_id"));
			this.item("complete_yn", "N");
			this.item("complete_date", "");
			this.item("reg_date", Malgn.time("yyyyMMddHHmmss"));
			this.item("site_id", siteId);
			if(!this.insert()) return -5;
		}

		return "N".equals(preCompleteYN) && "Y".equals(completeYN) ? 2 : 1;
	}
	
	public int reupdateProgress(DataSet cuinfo, DataSet cllist) {
		if(cuinfo == null || cllist == null) return 0;

		int success = 0;
		cllist.first();
		while(cllist.next()) {
			//진도저장
			int studyPage = 0;
			String completeYN = "N";

			//정보-진도
			DataSet cpinfo = this.find("course_user_id = " + cuinfo.i("id") + " AND chapter = " + cllist.i("chapter") + " AND complete_yn != 'Y'", "study_time, last_time, paragraph, view_cnt, complete_yn, reg_date");
			if(!cpinfo.next()) continue;

			//진도율 계산
			double ratio = 100.0;
			if("02".equals(cllist.s("lesson_type"))) {
				// WBT Contents
				studyPage = cpinfo.s("paragraph").split(",").length;
				if(cllist.i("total_time") > 0) {
					double ratio1 = cllist.i("total_page") > 0 ? Math.min(100.0, (studyPage / cllist.d("total_page")) * 100) : 100.0;
					double ratio2 = cpinfo.i("study_time") >= cllist.i("complete_time") ? 100.0 : Math.min(100.0, (cpinfo.i("study_time") / cllist.d("total_time")) * 100);
					ratio = Math.min(ratio1, ratio2);
				} else if(cllist.i("total_page") > 0) {
					ratio = Math.min(100.0, (studyPage / cllist.d("total_page")) * 100);
				}
			} else if(cllist.i("complete_time") == 0) {
				// Direct Complete
				ratio = 100.0;
				completeYN = "Y";
			} else if("04".equals(cllist.s("lesson_type"))) {
				// Link
				if(cllist.i("total_time") > 0 && cpinfo.i("study_time") < cllist.i("complete_time")) {
					ratio = Math.min(100.0, (cpinfo.i("study_time") / cllist.d("total_time")) * 100);
				}
			} else if("15".equals(cllist.s("lesson_type"))){
				//ktRemote
				if(cllist.i("total_time") > 0 && studyTime < cllist.i("complete_time")) {
					ratio = Math.min(100.0, (studyTime / cllist.d("total_time")) * 100);
				}
			} else {
				// Wecandeo, Kollus, MP4
				int ratioTime = Math.min(cpinfo.i("study_time"), cpinfo.i("last_time"));
				if(cllist.i("total_time") > 0 && ratioTime < cllist.i("complete_time")) {
					ratio = Math.min(100.0, (ratioTime / cllist.d("total_time")) * 100);
				}
			}
			if(ratio >= 100.0) completeYN = "Y";

			// DB 등록 또는 수정
			this.item("ratio", ratio);
			if("Y".equals(completeYN)) {
				this.item("ratio", 100.0);
				this.item("complete_yn", "Y");
				this.item("complete_date", Malgn.time("yyyyMMddHHmmss"));
			}
			
			if(this.update("course_user_id = " + cuinfo.i("id") + " AND chapter = " + cllist.i("chapter"))) success++;

		}
		return success;
	}
	
	//완료처리
	public boolean completeProgress(int cuid, int lessonId, int chapter) {
		
		//객체
		CourseUserDao courseUser = new CourseUserDao();

		DataSet cuinfo = courseUser.find("id = " + cuid + "");
		if(!cuinfo.next()) return false;

		DataSet linfo = new LessonDao().find("id = " + lessonId + "");
		if(!linfo.next()) return false;
		
		DataSet cpinfo = this.find("course_user_id = " + cuid + " AND lesson_id = " + lessonId);
		boolean exists = cpinfo.next();
		
		this.item("course_id", cuinfo.i("course_id"));
		this.item("lesson_id", lessonId);
		this.item("chapter", chapter);
		this.item("course_user_id", cuid);

		this.item("user_id", cuinfo.i("user_id"));
		this.item("lesson_type", linfo.s("lesson_type"));

		this.item("ratio", 100.0);
		this.item("complete_yn", "Y");
		
		this.item("view_cnt", 1);
		this.item("last_date", Malgn.time("yyyyMMddHHmmss"));
		this.item("status", 1);
		
		if("".equals(cpinfo.s("complete_date"))) this.item("complete_date", Malgn.time("yyyyMMddHHmmss"));

		if(exists) {
			this.item("last_time", cpinfo.i("last_time") > (linfo.i("total_time") * 60) ? cpinfo.i("last_time") : (linfo.i("total_time") * 60));
			if(!this.update("course_user_id = " + cuid + " AND lesson_id = " + lessonId)) return false;
		} else {
			this.item("paragraph", "");
			this.item("study_page", 0);
			this.item("study_time", 0);
			this.item("curr_page", "");
			this.item("curr_time", 0);
			this.item("last_time", linfo.i("total_time") * 60);
			this.item("reg_date", Malgn.time("yyyyMMddHHmmss"));
			this.item("site_id", siteId);
			if(!this.insert()) return false;
		}

		courseUser.setProgressRatio(cuid);
		courseUser.updateScore(cuid, "progress");
		courseUser.closeUser(cuid, cuinfo.i("user_id"));

		return true;
	}

	public int attendUser(DataSet llist, DataSet ulist, int changeUserId) {
		if(null == llist || null == ulist || 0 == changeUserId) return -1;
		
		CourseUserDao courseUser = new CourseUserDao();
		String now = Malgn.time("yyyyMMddHHmmss");
		int success = 0;

		llist.first();
		while(llist.next()) {
			String where = "course_id = " + llist.i("course_id") + " AND lesson_id = " + llist.i("lesson_id") + " AND course_user_id = ";
			
			this.item("course_id", llist.i("course_id"));
			this.item("lesson_id", llist.i("lesson_id"));
			this.item("chapter", llist.i("chapter"));
			this.item("lesson_type", llist.s("lesson_type"));
			this.item("study_page", 0);
			this.item("study_time", 0);
			this.item("curr_page", "");
			this.item("curr_time", 0);
			this.item("last_time", 0);
			this.item("paragraph", "");
			this.item("view_cnt", 1);
			this.item("change_user_id", changeUserId);
			this.item("reg_date", now);
			this.item("status", 1);

			ulist.first();
			while(ulist.next()) {
				this.item("course_user_id", ulist.i("course_user_id"));
				this.item("user_id", ulist.i("user_id"));

				if(ulist.b("attend_status")) {
					this.item("ratio", 100);
					this.item("complete_yn", "Y");
					this.item("last_date", now);
					this.item("complete_date", now);
				} else {
					this.item("ratio", 0);
					this.item("complete_yn", "N");
					this.item("last_date", "");
					this.item("complete_date", "");
				}

				if(0 < this.findCount(where + ulist.i("course_user_id"))) {
					if(this.update(where + ulist.i("course_user_id"))) success++;
				} else {
					this.item("site_id", siteId);
					if(this.insert()) success++;
				}

				courseUser.setProgressRatio(ulist.i("course_user_id"));
				courseUser.setCourseUserScore(ulist.i("course_user_id"), "progress"); //점수일괄업데이트
				// 왜: 출석 수동변경 직후 결석 기준(자동 F/미수료)도 즉시 반영되어야
				//     교수자 출석 탭과 수료 탭의 상태가 서로 어긋나지 않습니다.
				courseUser.completeUser(ulist.i("course_user_id"));
			}
		}

		return success;
	}

	public boolean getLimitFlag(int cuid, DataSet cinfo) {
		String today = Malgn.time("yyyyMMdd");
		int limitLessonCnt = this.findCount(
			"course_user_id = " + cuid + " AND (last_date BETWEEN '" + today + "000000' AND '" + Malgn.time("yyyyMMdd") + "235959')"
			+ " AND (complete_yn = 'N' OR complete_date >= '" + today + "000000')"
		);
		return (cinfo.i("limit_lesson") <= 0 ? 0 : cinfo.i("limit_lesson")) <= limitLessonCnt;
	}

	public boolean getExamReadyFlag(int cuid, String cid, int chapter) {
		int completeCnt = this.getOneInt(
			" SELECT COUNT(a.chapter) "
			+ " FROM " + new CourseLessonDao().table + " a "
			+ " LEFT JOIN " + this.table + " cp ON cp.course_user_id = " + cuid + " AND cp.lesson_id = a.lesson_id "
			+ " WHERE a.course_id = " + cid + " AND a.chapter <= " + chapter + " AND cp.complete_yn = 'Y' "
			+ " ORDER BY a.chapter ASC "
		);
		return chapter > completeCnt;
	}
}
