package dao;

import malgnsoft.db.*;
import malgnsoft.util.*;
import java.util.*;

public class CourseUserDao extends DataObject {

	public String[] classroomStatusList = { "W=>대기", "E=>종료", "I=>수강중", "R=>복습중" };
	public String[] progressList = { "W=>대기", "E=>종료", "I=>수강중", "R=>복습중" };
	public String[] statusList = { "1=>정상", "0=>승인대기", "2=>입금대기", "3=>취소요청", "-4=>수강취소" };
	public String[] scoreFields = { "progress", "exam", "homework", "forum", "etc" };
	public String[] scoreFieldNames = { "progress=>진도", "exam=>시험", "homework=>과제", "forum=>토론", "etc=>기타", "total_score=>총점" };
	public String[] completeYn = { "=>-", "Y=>수료", "N=>미수료" };

	public String[] classroomStatusListMsg = { "W=>list.course_user.classroom_status_list.W", "E=>list.course_user.classroom_status_list.E", "I=>list.course_user.classroom_status_list.I", "R=>list.course_user.classroom_status_list.R" };
	public String[] progressListMsg = { "W=>list.course_user.progress_list.W", "E=>list.course_user.progress_list.E", "I=>list.course_user.progress_list.I", "R=>list.course_user.progress_list.R" };
	public String[] statusListMsg = { "1=>list.course_user.status_list.1", "0=>list.course_user.status_list.0", "2=>list.course_user.status_list.2", "3=>list.course_user.status_list.3", "-4=>list.course_user.status_list.-4" };
	public String[] scoreFieldNamesMsg = { "progress=>list.course_user.score_field_names.progress", "exam=>list.course_user.score_field_names.exam", "homework=>list.course_user.score_field_names.homework", "forum=>list.course_user.score_field_names.forum", "etc=>list.course_user.score_field_names.etc", "total_score=>list.course_user.score_field_names.total_score" };
	public String[] completeYnMsg = { "=>-", "Y=>list.course_user.complete_yn.Y", "N=>list.course_user.complete_yn.N" };

	public CourseUserDao() {
		this.table = "LM_COURSE_USER";
	}

	public boolean addUser(DataSet cinfo, int userId, int status) {
		return addUser(cinfo, userId, status, "", "", null);
	}

	public boolean addUser(DataSet cinfo, int userId, int status, DataSet pinfo) {
		return addUser(cinfo, userId, status, "", "", pinfo);
	}

	public boolean addUser(DataSet cinfo, int userId, int status, String startDate, String endDate) {
		return addUser(cinfo, userId, status, startDate, endDate, null);
	}

	public boolean addUser(DataSet cinfo, int userId, int status, String startDate, String endDate, DataSet pinfo) {

		CourseRenewDao courseRenew = new CourseRenewDao();
		boolean result = false;
		int newId = 0;

		if(pinfo == null) {
			pinfo = new DataSet();
			pinfo.addRow();
		}

		if("".equals(cinfo.s("freepass_end_date"))) {
			startDate = Malgn.time("yyyyMMdd", startDate);
			endDate = Malgn.time("yyyyMMdd", endDate);
		} else {
			startDate = Malgn.time("yyyyMMdd");
			endDate = cinfo.s("freepass_end_date");
		}

		//분반처리
		/*
		TutorDao tutor = new TutorDao();
		CourseTutorDao courseTutor = new CourseTutorDao();
		DataSet tutors = courseTutor.query(
			"SELECT t.* "
			+ " FROM " + courseTutor.table + " a "
			+ " JOIN " + tutor.table + " t ON t.user_id = a.user_id "
			+ " WHERE a.course_id = " + cinfo.i("id") + " "
			+ " ORDER BY t.tutor_nm DESC "
		);

		//분반
		int cuCnt = findCount("user_id = " + userId + " AND course_id = " + cinfo.i("id") + " AND status NOT IN (-1, -4)");
		int tutorId = 0;
		int classNo = (cinfo.i("class_member") > 0 ? cuCnt / courses.i("class_member") : 0) + 1;
		while(tutors.next()) {
			if(0 == tutorId || tutors.i("__ord") == classNo) tutorId = tutors.i("user_id");
		}
		*/

		newId = this.getSequence();
		item("id", newId);
		item("site_id", cinfo.i("site_id"));
		item("package_id", pinfo.i("id"));
		item("course_id", cinfo.i("id"));
		item("user_id", userId);
		item("order_id", cinfo.i("order_id"));
		item("order_item_id", cinfo.i("order_item_id"));
		
		//item("class", classNo);
		//item("tutor_id", tutorId);
		item("grade", 1);

		// RENEW_CNT 컬럼이 NOT NULL인 환경에서도 기존 수강신청/관리자등록이 실패하지 않도록
		// 최초 등록 시 연장횟수를 0으로 기본 세팅합니다.
		item("renew_cnt", 0);

		if("A".equals(cinfo.s("course_type"))) {
			if(!"".equals(startDate) && !"".equals(endDate)) {
				item("start_date", startDate);
				item("end_date", endDate);

				//item("fail_reason", "5");
			} else {
				int lessonDay = (0 < pinfo.i("id") ? pinfo.i("lesson_day") : cinfo.i("lesson_day"));

				startDate = Malgn.time("yyyyMMdd");
				endDate = Malgn.time("yyyyMMdd", Malgn.addDate("D", lessonDay > 0 ? lessonDay - 1 : 0, Malgn.time("yyyyMMdd")));

				//item("fail_reason", lessonDay);
			}
		} else {
			startDate = cinfo.s("study_sdate");
			endDate = cinfo.s("study_edate");
		}

		item("start_date", startDate);
		item("end_date", endDate);

		item("progress_ratio", 0);
		item("progress_score", 0);
		item("exam_value", 0);
		item("exam_score", 0);
		item("homework_value", 0);
		item("homework_score", 0);
		item("forum_value", 0);
		item("forum_score", 0);
		item("etc_value", 0);
		item("etc_score", 0);
		item("total_score", 0);

		item("credit", cinfo.i("credit"));
		item("complete_status", "");
		item("complete_yn", "");
		item("complete_no", "");
		item("complete_date", "");
		item("close_yn", "N");
		item("close_date", "");
		item("close_user_id", 0);
		item("mod_date", "");

		item("reg_date", Malgn.time("yyyyMMddHHmmss"));
		item("status", status);

		if(cinfo.b("limit_people_yn")) {
			this.startTrans();
			int userCnt = this.findCount("course_id = " + cinfo.i("course_id") + " AND status NOT IN (-1, -4)");
			if(userCnt >= cinfo.i("limit_people")) {
				this.endTrans();
				return false;
			}
		}

		if(insert()) {
			courseRenew.item("site_id", cinfo.i("site_id"));
			courseRenew.item("course_user_id", newId);
			courseRenew.item("renew_type", "C");
			courseRenew.item("start_date", startDate);
			courseRenew.item("end_date", endDate);
			courseRenew.item("user_id", userId);
			courseRenew.item("order_item_id", cinfo.i("order_item_id"));
			courseRenew.item("reg_date", Malgn.time("yyyyMMddHHmmss"));
			courseRenew.item("status", 1);
			result = courseRenew.insert();
		}

		if(cinfo.b("limit_people_yn")) this.endTrans();

		return result;
	}

	public boolean updateStudyDate(Hashtable<String, Object> cuinfo, int status, String updateType) {
		DataSet culist = new DataSet();
		culist.addRow(cuinfo);
		return updateStudyDate(culist, status, updateType);
	}

	public boolean updateStudyDate(DataSet culist, int status, String updateType) {
		if(null == culist) return false;
		if("".equals(updateType)) updateType = "N";

		CourseRenewDao courseRenew = new CourseRenewDao();

		this.item("change_date", Malgn.time("yyyyMMddHHmmss"));
		this.item("status", status);
		culist.first();
		while(culist.next()) {
			String startDate = "";
			String endDate = "";

			if("A".equals(culist.s("course_type"))) {
				startDate = Malgn.time("yyyyMMdd");
				if("".equals(culist.s("freepass_end_date"))) {
					endDate = Malgn.time("yyyyMMdd", Malgn.addDate("D", culist.i("lesson_day") > 0 ? culist.i("lesson_day") - 1 : 0, Malgn.time("yyyyMMdd")));
				} else {
					endDate = culist.s("freepass_end_date");
				}
			} else {
				startDate = culist.s("study_sdate");
				endDate = culist.s("study_edate");
			}
			this.item("start_date", startDate);
			this.item("end_date", endDate);
			if(!this.update("id = " + culist.i("course_user_id"))) return false;

			courseRenew.item("site_id", culist.i("site_id"));
			courseRenew.item("course_user_id", culist.i("course_user_id"));
			courseRenew.item("renew_type", updateType);
			courseRenew.item("start_date", startDate);
			courseRenew.item("end_date", endDate);
			courseRenew.item("user_id", culist.i("user_id"));
			courseRenew.item("order_item_id", culist.i("id"));
			courseRenew.item("reg_date", Malgn.time("yyyyMMddHHmmss"));
			courseRenew.item("status", 1);
			if(!courseRenew.insert()) return false;
		}
		return true;
	}

	public boolean setRenewBlock(Hashtable<String, Object> map) {
		if(null == map) return false;
		DataSet info = new DataSet();
		info.addRow(map);
		return info.b("renew_yn") && "A".equals(info.s("course_type")) && "N".equals(info.s("onoff_type"))
			&& (0 == info.i("renew_max_cnt") || info.i("renew_max_cnt") > info.i("renew_cnt"))
			&& (0 <= Malgn.diffDate("D", Malgn.time("yyyyMMdd"), info.s("end_date")))
			//&& (0 >= Malgn.diffDate("D", Malgn.time("yyyyMMdd"), info.s("start_date")))
			//&& 1 > info.i("renew_id")
		;
	}

	public String renewStudyDate(Hashtable<String, Object> map) {
		if(null == map) return "";
		DataSet info = new DataSet();
		info.addRow(map);
/*
		return renewStudyDate(info);
	}

	public String renewStudyDate(DataSet info) {
		if(null == info || 1 > info.size()) return "";
*/
		String renewEndDate = Malgn.time("yyyyMMdd", Malgn.addDate("D", info.i("renew_lesson_day"), info.s("end_date")));

		this.item("renew_cnt", info.i("renew_cnt") + 1);
		this.item("end_date", renewEndDate);

		if(this.update("id = " + info.i("renew_id"))) {
			return renewEndDate;
		} else {
			return "";
		}
	}

	public int setCourseUserScore(int id, String field) {

		field = field.toLowerCase();
		if(!Malgn.inArray(field, scoreFields)) return -1;

		CourseModuleDao cm = new CourseModuleDao();
		CourseDao course = new CourseDao();

		DataSet info = query(
			"SELECT a.*, c.assign_progress, c.assign_exam, c.assign_homework, c.assign_forum, c.assign_etc "
			+ " FROM " + this.table + " a "
			+ " INNER JOIN " + course.table + " c ON a.course_id = c.id "
			+ " WHERE a.id = " + id + " "
		);
		if(!info.next()) return -1;

		if("progress".equals(field)) {
			execute(
				"UPDATE " + this.table + " "
				+ " SET " + field + "_score = " + Math.min(info.d("assign_progress"), info.d("assign_progress") * (info.d("progress_ratio") / 100)) + " " 
				+ " WHERE id = " + id);
		} else {
			// 왜: 과목 평가 기준(assign_exam/homework/forum 등)과 실제 배점(모듈별 assign_score 합계)이 다를 수 있습니다.
			//     예) 시험 1개 배점 50점에 70점을 주면, 단순 합계는 35점(=50*70%)이지만,
			//         과목 기준이 "시험 100%"(assign_exam=100)이라면 최종은 70점으로 환산되어야 합니다.
			//     기존 로직은 합계 점수를 그대로 넣어서 "절반(35점)"처럼 보이는 문제가 발생했습니다.
			double userScoreSum = Malgn.parseDouble(getOne(
				"SELECT SUM(a.score) t_score"
				+ " FROM LM_" + field.toUpperCase() + "_USER a"
				+ " INNER JOIN " + cm.table + " b ON a." + field + "_id = b.module_id AND b.module = '" + field + "' AND b.course_id = '" + info.s("course_id") + "' AND b.status = 1"
				+ " WHERE a.course_user_id = " + id + " AND a.status = 1 AND a.confirm_yn = 'Y'"
			));

			int scoreValue = info.i("assign_" + field); // 과목 기준 배점(0~100)
			int sumAssignScore = 0;
			if("exam".equals(field)) {
				sumAssignScore = cm.getOneInt(
					"SELECT SUM(a.assign_score)"
					+ " FROM " + cm.table + " a "
					+ " INNER JOIN " + new ExamDao().table + " e ON e.id = a.module_id AND e.site_id = " + info.i("site_id") + " AND e.status != -1 "
					+ " WHERE a.course_id = " + info.i("course_id") + " AND a.module = 'exam' AND a.status = 1"
				);
			} else if("homework".equals(field)) {
				sumAssignScore = cm.getOneInt(
					"SELECT SUM(a.assign_score)"
					+ " FROM " + cm.table + " a "
					+ " INNER JOIN " + new HomeworkDao().table + " h ON h.id = a.module_id AND h.site_id = " + info.i("site_id") + " AND h.status != -1 "
					+ " WHERE a.course_id = " + info.i("course_id") + " AND a.module = 'homework' AND a.status = 1"
				);
			} else if("forum".equals(field)) {
				sumAssignScore = cm.getOneInt(
					"SELECT SUM(a.assign_score)"
					+ " FROM " + cm.table + " a "
					+ " INNER JOIN " + new ForumDao().table + " fo ON fo.id = a.module_id AND fo.site_id = " + info.i("site_id") + " AND fo.status != -1 "
					+ " WHERE a.course_id = " + info.i("course_id") + " AND a.module = 'forum' AND a.status = 1"
				);
			} else {
				sumAssignScore = cm.getOneInt(
					"SELECT SUM(assign_score)"
					+ " FROM " + cm.table
					+ " WHERE course_id = " + info.i("course_id") + " AND module = '" + field + "' AND status = 1"
				);
			}

			double fieldScore = 0.0;
			double fieldValue = 0.0;
			if(0 < scoreValue && 0 < sumAssignScore) {
				fieldScore = Malgn.round(userScoreSum * scoreValue / sumAssignScore, 2);
				fieldScore = Math.min(scoreValue, fieldScore);
				fieldValue = Math.min(100.0, fieldScore * 100 / scoreValue);
			}

			execute(
				"UPDATE " + this.table + " SET "
				+ field + "_score = " + fieldScore + ", "
				+ field + "_value = " + fieldValue + " "
				+ " WHERE id = " + id
			);
		}

		updateTotalScore(id);
		return 1;
	}

	public boolean updateUserScore(DataSet cuinfo, DataSet cinfo) {
		if(!cuinfo.next()) return false;
		if(!cinfo.next()) return false;
		int cuid = cuinfo.i("id");
		int cid = cinfo.i("id");

		//초기화
		this.item("progress_ratio", 0.00);
		this.item("progress_score", 0.00);
		this.item("exam_value", 0.00);
		this.item("exam_score", 0.00);
		this.item("homework_value", 0.00);
		this.item("homework_score", 0.00);
		this.item("forum_value", 0.00);
		this.item("forum_score", 0.00);
		this.item("etc_value", 0.00);
		this.item("total_score", 0.00);

		for(int i = 0; i < scoreFields.length; i++) {

			if("progress".equals(scoreFields[i])) {
				double progressRatio = getProgressRatio(cuid, cid);
				this.item("progress_ratio", progressRatio);
				this.item("progress_score", progressRatio * cinfo.d("assign_progress") / 100.0);
			} else if("exam".equals(scoreFields[i])) {
				int sumAssignScore = 0;
				double sumConvertScore = 0.0;
				DataSet minfo = this.query(
					"SELECT a.assign_score, u.marking_score "
					+ " FROM " + new CourseModuleDao().table + " a "
					+ " INNER JOIN " + new ExamDao().table + " e ON e.id = a.module_id AND e.site_id = " + cinfo.i("site_id") + " AND e.status != -1 "
					+ " LEFT JOIN " + new ExamUserDao().table + " u ON "
						+ " u.exam_id = a.module_id AND u.exam_step = 1 "
						+ " AND u.course_user_id = " + cuid + " AND u.confirm_yn = 'Y' "
					+ " WHERE a.course_id = " + cid
					+ " AND a.module = 'exam' AND a.status = 1 "
				);
				while(minfo.next()) {
					//원점수(100점)
					double markingScore = minfo.d("marking_score");

					//시험별배점
					int assignScore = minfo.i("assign_score");

					//시험별환산점수(시험별배점)
					//	markingScore : 100 = convertScore : assignScore
					//	convertScore = markingScore * assignScore / 100
					double convertScore = markingScore * assignScore / 100.0;

					//합산
					sumAssignScore += assignScore;
					sumConvertScore += convertScore;
				}

				// 왜: 사용자가 이해하기 쉬운 공식으로 정리합니다.
				// 1) 시험별 점수 = (획득점수 * 시험배점) / 100  -> sumConvertScore(시험배점 합계 기준의 점수)
				// 2) 100점 환산값 = sumConvertScore * 100 / 시험배점합계  -> exam_value
				// 3) 성적 반영 점수 = 100점 환산값 * 시험 평가비율(assign_exam) / 100  -> exam_score
				double examValue = 0.0;
				double examScore = 0.0;
				if(0 < sumAssignScore) {
					examValue = Malgn.round(sumConvertScore * 100 / sumAssignScore, 2);
					examScore = Malgn.round(examValue * cinfo.i("assign_exam") / 100.0, 2);
					examScore = Math.min(cinfo.d("assign_exam"), examScore);
				}
				this.item("exam_value", examValue);
				this.item("exam_score", examScore);

			} else if("homework".equals(scoreFields[i])) {
				int sumAssignScore = 0;
				double sumConvertScore = 0.0;
				DataSet minfo = this.query(
					"SELECT a.assign_score, u.marking_score "
					+ " FROM " + new CourseModuleDao().table + " a "
					+ " INNER JOIN " + new HomeworkDao().table + " h ON h.id = a.module_id AND h.site_id = " + cinfo.i("site_id") + " AND h.status != -1 "
					+ " LEFT JOIN " + new HomeworkUserDao().table + " u ON "
						+ " u.homework_id = a.module_id "
						+ " AND u.course_user_id = " + cuid + " AND u.confirm_yn = 'Y' "
					+ " WHERE a.course_id = " + cid
					+ " AND a.module = 'homework' AND a.status = 1 "
				);
				while(minfo.next()) {
					double markingScore = minfo.d("marking_score");
					int assignScore = minfo.i("assign_score");
					double convertScore = markingScore * assignScore / 100.0;

					sumAssignScore += assignScore;
					sumConvertScore += convertScore;
				}

				double homeworkValue = 0.0;
				double homeworkScore = 0.0;
				if(0 < sumAssignScore) {
					homeworkValue = Malgn.round(sumConvertScore * 100 / sumAssignScore, 2);
					homeworkScore = Malgn.round(homeworkValue * cinfo.i("assign_homework") / 100.0, 2);
					homeworkScore = Math.min(cinfo.d("assign_homework"), homeworkScore);
				}
				this.item("homework_value", homeworkValue);
				this.item("homework_score", homeworkScore);

			} else if("forum".equals(scoreFields[i])) {
				int sumAssignScore = 0;
				double sumConvertScore = 0.0;
				DataSet minfo = this.query(
					"SELECT a.assign_score, u.marking_score "
					+ " FROM " + new CourseModuleDao().table + " a "
					+ " INNER JOIN " + new ForumDao().table + " fo ON fo.id = a.module_id AND fo.site_id = " + cinfo.i("site_id") + " AND fo.status != -1 "
					+ " LEFT JOIN " + new ForumUserDao().table + " u ON "
						+ " u.forum_id = a.module_id "
						+ " AND u.course_user_id = " + cuid + " AND u.confirm_yn = 'Y' "
					+ " WHERE a.course_id = " + cid
					+ " AND a.module = 'forum' AND a.status = 1 "
				);
				while(minfo.next()) {
					double markingScore = minfo.d("marking_score");
					int assignScore = minfo.i("assign_score");
					double convertScore = markingScore * assignScore / 100.0;

					sumAssignScore += assignScore;
					sumConvertScore += convertScore;
				}

				double forumValue = 0.0;
				double forumScore = 0.0;
				if(0 < sumAssignScore) {
					forumValue = Malgn.round(sumConvertScore * 100 / sumAssignScore, 2);
					forumScore = Malgn.round(forumValue * cinfo.i("assign_forum") / 100.0, 2);
					forumScore = Math.min(cinfo.d("assign_forum"), forumScore);
				}
				this.item("forum_value", forumValue);
				this.item("forum_score", forumScore);

			} else if("etc".equals(scoreFields[i])) {
				this.item("etc_value", 0 < cinfo.i("assign_etc") ? Malgn.round(cuinfo.d("etc_score") * 100 / cinfo.i("assign_etc"), 2) : 0.0);
			}
		}
		
		if(!this.update("id = " + cuid)) return false;

		//총점계산
		updateTotalScore(cuid);
		return true;
	}
	
	public double getProgressRatio(int cuid, int cid) {
		if(0 == cuid || 0 == cid) return -1;

		DataSet rs = this.query(
			" SELECT COUNT(*) progress_cnt, SUM(CASE cp.complete_yn WHEN 'Y' THEN 1 ELSE 0 END) complete_cnt "
			+ " FROM " + new CourseLessonDao().table + " a "
			+ " LEFT JOIN " + new CourseProgressDao().table + " cp ON cp.course_user_id = " + cuid + " AND cp.lesson_id = a.lesson_id AND cp.status = 1 "
			+ " WHERE a.course_id = " + cid + " AND a.progress_yn = 'Y' AND a.status = 1 "
		);
		if(!rs.next()) return 0.0;
		if(0 == rs.i("progress_cnt")) return 0.0;
		return Malgn.round(rs.d("complete_cnt") / rs.d("progress_cnt") * 100, 2);
	}

	public boolean updateProgressRatio(int cuid, int cid) {
		if(0 == cuid || 0 == cid) return false;
		this.item("progress_ratio", this.getProgressRatio(cuid, cid));
		return this.update("id = " + cuid);
	}

	public void updateProgressRatioAll(int cid) {
		DataSet list = this.find("course_id = " + cid + " AND status = 1");
		while(list.next()) {
			updateProgressRatio(list.i("id"), cid);
		}
	}

	public int updateScore(int id, String field) {
		return updateScore(id, field, 1);
	}

	public int updateScore(int id, String field, int examStep) {
		field = field.toLowerCase();
		if(!Malgn.inArray(field, scoreFields)) return -1;

		DataSet info = query(
			"SELECT a.*, c.assign_progress, c.assign_exam, c.assign_homework, c.assign_forum, c.assign_etc "
			+ " FROM " + this.table + " a "
			+ " INNER JOIN " + new CourseDao().table + " c ON a.course_id = c.id "
			+ " WHERE a.id = " + id + " "
		);
		if(!info.next()) return -1;

		if("progress".equals(field)) {
			if(-1 == this.execute(
				"UPDATE " + this.table + " SET "
				+ " progress_score = " + Math.min(info.d("assign_progress"), info.d("assign_progress") * (info.d("progress_ratio") / 100)) + " "
				+ " WHERE id = " + id + ""
			)) return -1;
		} else if("exam".equals(field)) {
			DataSet sinfo = this.query(
				"SELECT SUM(assign_score) assign_score, SUM(u.score) score "
				+ " FROM " + new CourseModuleDao().table + " a "
				+ " LEFT JOIN " + new ExamUserDao().table + " u ON "
					+ " u.exam_id = a.module_id AND u.exam_step = " + examStep + " "
					+ " AND u.course_user_id = " + id + " AND u.confirm_yn = 'Y' "
				+ " WHERE a.course_id = " + info.i("course_id") + " "
				+ " AND a.module = 'exam' AND a.status = 1 "
			);
			sinfo.next();

			// 왜: setCourseUserScore와 동일하게, 모듈 배점 합계(예: 50점)를 과목 배점(예: 100점)으로 환산해야 합니다.
			double score = 0.0;
			if(0 < sinfo.i("assign_score") && 0 < info.i("assign_exam")) {
				score = Malgn.round(sinfo.d("score") * info.d("assign_exam") / sinfo.d("assign_score"), 2);
				score = Math.min(info.d("assign_exam"), score);
			}
			double scoreValue = info.i("assign_exam") > 0 ? Math.min(100.0, score * 100 / info.d("assign_exam")) : 0.0;
			if(-1 == execute(
				"UPDATE " + this.table + " SET "
				+ " exam_score = " + score + " "
				+ ", exam_value = " + scoreValue + " "
				+ " WHERE id = " + id + ""
			)) return -1;
		
		} else if("homework".equals(field)) {
			double scores = Malgn.parseDouble(this.getOne(
				"SELECT SUM(a.score) score "
				+ " FROM " + new HomeworkUserDao().table + " a "
				+ " INNER JOIN " + new CourseModuleDao().table + " m "
					+ " ON m.course_id = " + info.i("course_id") + " AND m.module = 'homework' AND m.module_id = a.homework_id "
				+ " WHERE a.course_user_id = " + id + " AND a.status = 1 AND a.confirm_yn = 'Y' "
			));
			if(-1 == execute(
				"UPDATE " + this.table + " SET "
				+ " homework_score = " + Math.min(info.d("assign_homework"), scores) + " "
				+ " WHERE id = " + id + ""
			)) return -1;
		
		} else if("forum".equals(field)) {
			double scores = Malgn.parseDouble(this.getOne(
				"SELECT SUM(a.score) score "
				+ " FROM " + new ForumUserDao().table + " a "
				+ " INNER JOIN " + new CourseModuleDao().table + " m "
					+ " ON m.course_id = " + info.i("course_id") + " AND m.module = 'forum' AND m.module_id = a.forum_id "
				+ " WHERE a.course_user_id = " + id + " AND a.status = 1 AND a.confirm_yn = 'Y' "
			));
			if(-1 == execute(
				"UPDATE " + this.table + " SET "
				+ " forum_score = " + Math.min(info.d("assign_forum"), scores) + " "
				+ " WHERE id = " + id + ""
			)) return -1;
		}

		updateTotalScore(id);
		return 1;
	}

	
	public void updateTotalScore(int id) {
		double totalScore = 0.0;

		DataSet info = find("id = " + id + "");
		if(info.next()) {
			//if("Y".equals(info.s("end_yn"))) return;			//마감된 과정
			for(int i = 0; i < scoreFields.length; i++) {
				totalScore += info.d(scoreFields[i] + "_score");
			}
			this.execute("UPDATE " + this.table + " SET total_score = " + Math.min(totalScore, 100.0) + " WHERE id = " + id + "");
		}

	}

	public boolean setProgressRatio(int id) {
		DataSet info = find("id = " + id  + "");
		if(!info.next()) return false;

		int lessonCnt = new CourseLessonDao().findCount("status = 1 AND course_id = " + info.i("course_id") + " AND status = 1"); //전체 차시
		int completedCnt = new CourseProgressDao().findCount("course_user_id = " + id + " AND complete_yn = 'Y' AND status = 1");
		
		this.item("progress_ratio", Math.min(100.00, Malgn.round(lessonCnt > 0 ? completedCnt * 100.00 / lessonCnt : 0.00, 2)));
		return this.update("id = " + id + "");
	}

	public int getAbsenceCount(int courseUserId, int courseId) {
		if(courseUserId <= 0 || courseId <= 0) return 0;

		int lessonCnt = new CourseLessonDao().findCount(
			"course_id = " + courseId + " AND progress_yn = 'Y' AND status = 1"
		);
		if(lessonCnt <= 0) return 0;

		int completedCnt = new CourseProgressDao().findCount(
			"course_user_id = " + courseUserId + " AND complete_yn = 'Y' AND status = 1"
		);
		return Math.max(0, lessonCnt - completedCnt);
	}

	private boolean isAbsenceFail(DataSet cinfo, int absenceCnt) {
		return cinfo.b("limit_absence_yn")
			&& cinfo.i("limit_absence_cnt") > 0
			&& absenceCnt >= cinfo.i("limit_absence_cnt");
	}

	private String getCompleteStatus(DataSet cuinfo, DataSet cinfo, boolean meetModulePass, boolean absenceFail) {

		double progress = cuinfo.d("progress_ratio");
		double totalScore = cuinfo.d("total_score");

		boolean usePass = cinfo.b("pass_yn");
		if(absenceFail) return "F"; //결석 기준 초과 시 자동 미수료

		boolean meetProgComplete = progress >= cinfo.d("complete_limit_progress");
		boolean meetProgPass = progress >= cinfo.d("limit_progress");

		boolean meetScoreComplete = cinfo.i("complete_limit_total_score") == 0
			|| totalScore >= cinfo.d("complete_limit_total_score");
		boolean meetScorePass = cinfo.i("limit_total_score") == 0
			|| totalScore >= cinfo.d("limit_total_score");

		if(usePass && meetProgPass && meetScorePass && meetModulePass) return "P"; //합격
		if(meetProgComplete && meetScoreComplete) return "C"; //수료
		return "F"; //미수료
	}

	private String getFailReason(DataSet cuinfo, DataSet cinfo, boolean absenceFail) {
		if(absenceFail) {
			// 왜: 정규과정은 교수자 화면에서 F로 명확히 보여야 하므로 사유코드를 별도로 남깁니다.
			return "R".equals(cinfo.s("course_type")) ? "absence_f" : "absence";
		}
		if(cuinfo.d("progress_ratio") < cinfo.d("complete_limit_progress")) return "progress";
		if(cinfo.i("complete_limit_total_score") > 0 && cuinfo.d("total_score") < cinfo.d("complete_limit_total_score")) return "total_score";

		for(int i = 0; i < scoreFields.length; i++) {
			if(cinfo.d("limit_" + scoreFields[i]) > cuinfo.d(scoreFields[i] + "_value")) {
				return scoreFields[i];
			}
		}

		if(cinfo.i("limit_total_score") > 0 && cuinfo.d("total_score") < cinfo.d("limit_total_score")) return "total_score";

		return "";
	}


	public int closeUser(int id, int userId) {
		return closeUser(id, userId, "N");
	}

	public int completeUser(int id) {
		
		CourseDao course = new CourseDao();
		CourseModuleDao courseModule = new CourseModuleDao();
		SurveyDao survey = new SurveyDao();
		SurveyUserDao surveyUser = new SurveyUserDao();

		DataSet info = query(
			"SELECT a.*, a.progress_ratio progress_value "
			+ ", c.assign_survey_yn, c.limit_progress, c.limit_exam, c.limit_homework, c.limit_forum, c.limit_etc, c.limit_total_score, c.complete_auto_yn "
			+ ", c.complete_limit_progress, c.complete_limit_total_score "
			+ ", c.limit_absence_yn, c.limit_absence_cnt "
			+ ", c.year, c.step, c.course_type, c.complete_no_yn, c.complete_prefix, c.postfix_cnt, c.postfix_type, c.postfix_ord "
			+ ", c.pass_yn "
			+ " FROM " + this.table + " a "
			+ " INNER JOIN " + course.table + " c ON a.course_id = c.id "
			+ " WHERE a.id = " + id + " "
		);
		if(!info.next()) return -1;

		String now = Malgn.time("yyyyMMddHHmmss");
		String completeDate = info.s("end_date") + "235959";
		if("A".equals(info.s("course_type")) && 0 < Malgn.diffDate("D", now, completeDate)) completeDate = now;

		//설문검사(합격 판단에 포함)
		boolean meetModulePass = true;
		for(int i = 0; i < scoreFields.length; i++) {
			if(info.d("limit_" + scoreFields[i]) > info.d(scoreFields[i] + "_value")) {
				meetModulePass = false;
				break;
			}
		}
		if(info.b("assign_survey_yn") && meetModulePass) {
			DataSet sulist = courseModule.query(
				" SELECT a.module_id, su.reg_date "
				+ " FROM " + courseModule.table + " a "
				+ " INNER JOIN " + survey.table + " s ON a.module_id = s.id AND s.status = 1 "
				+ " LEFT JOIN " + surveyUser.table + " su ON su.survey_id = s.id AND su.course_user_id = " + id + " AND su.status = 1 "
				+ " WHERE a.module = 'survey' AND a.course_id = " + info.i("course_id") + " AND a.site_id = " + info.i("site_id") + " AND a.status = 1 "
			);
			while(sulist.next()) {
				if("".equals(sulist.s("reg_date"))) {
					meetModulePass = false;
					break;
				}
			}
		}
		info.put("meet_module_pass", meetModulePass);

		int absenceCnt = getAbsenceCount(id, info.i("course_id"));
		boolean absenceFail = isAbsenceFail(info, absenceCnt);
		info.put("absence_cnt", absenceCnt);
		info.put("absence_fail", absenceFail);
		if(absenceFail) {
			Malgn.errorLog(
				"{CourseUser.completeUser.absence} cuid:" + id
				+ ", course_id:" + info.i("course_id")
				+ ", absence_cnt:" + absenceCnt
				+ ", limit_absence_cnt:" + info.i("limit_absence_cnt")
				+ ", course_type:" + info.s("course_type")
			);
		}

		String status = getCompleteStatus(info, info, meetModulePass, absenceFail);
		String failStr = "F".equals(status) ? getFailReason(info, info, absenceFail) : "";

		item("complete_status", status);
		item("complete_yn", "F".equals(status) ? "N" : "Y");

		String compNo = Malgn.time("yyyy", completeDate) + "-" + id;
		if("Y".equals(info.s("complete_no_yn"))) {
			if("R".equals(info.s("postfix_type"))) {
				compNo = getCompNo(getUserRank(id, info.i("course_id"), info.s("postfix_ord")), info.s("complete_prefix"), info.i("postfix_cnt"));
			} else {
				compNo = info.s("complete_prefix") + "-" + Malgn.strrpad("" + id, info.i("postfix_cnt"), "0");
			}
		}

		item("complete_no", compNo);
		item("complete_date", completeDate);
		item("fail_reason", failStr);
		item("mod_date", Malgn.time("yyyyMMddHHmmss"));

		if(!update("id = " + id)) { return -1; }
		return 1;
	}

	public int closeUser(int id, int userId, String endYn) {

		CourseDao course = new CourseDao();
		CourseModuleDao courseModule = new CourseModuleDao();
		SurveyDao survey = new SurveyDao();
		SurveyUserDao surveyUser = new SurveyUserDao();

		DataSet info = query(
			"SELECT a.*, a.progress_ratio progress_value "
			+ ", c.assign_survey_yn, c.limit_progress, c.limit_exam, c.limit_homework, c.limit_forum, c.limit_etc, c.limit_total_score, c.complete_auto_yn "
			+ ", c.complete_limit_progress, c.complete_limit_total_score "
			+ ", c.limit_absence_yn, c.limit_absence_cnt "
			+ ", c.year, c.step, c.course_type, c.complete_no_yn, c.complete_prefix, c.postfix_cnt, c.postfix_type, c.postfix_ord  "
			+ ", c.pass_yn "
			+ " FROM " + this.table + " a "
			+ " INNER JOIN " + course.table + " c ON a.course_id = c.id "
			+ " WHERE a.id = " + id + " AND a.close_yn != 'Y' "
		);
		if(!info.next()) return -1;

		//과정모듈검사 + 설문검사 (합격 판단용)
		boolean meetModulePass = true;
		for(int i = 0; i < scoreFields.length; i++) {
			if(info.d("limit_" + scoreFields[i]) > info.d(scoreFields[i] + "_value")) {
				meetModulePass = false;
				break;
			}
		}
		if(info.b("assign_survey_yn") && meetModulePass) {
			DataSet sulist = courseModule.query(
				" SELECT a.module_id, su.reg_date "
				+ " FROM " + courseModule.table + " a "
				+ " INNER JOIN " + survey.table + " s ON a.module_id = s.id AND s.status = 1 "
				+ " LEFT JOIN " + surveyUser.table + " su ON su.survey_id = s.id AND su.course_user_id = " + id + " AND su.status = 1 "
				+ " WHERE a.module = 'survey' AND a.course_id = " + info.i("course_id") + " AND a.site_id = " + info.i("site_id") + " AND a.status = 1 "
			);
			while(sulist.next()) {
				if("".equals(sulist.s("reg_date"))) {
					meetModulePass = false;
					break;
				}
			}
		}
		info.put("meet_module_pass", meetModulePass);

		int absenceCnt = getAbsenceCount(id, info.i("course_id"));
		boolean absenceFail = isAbsenceFail(info, absenceCnt);
		info.put("absence_cnt", absenceCnt);
		info.put("absence_fail", absenceFail);
		if(absenceFail) {
			Malgn.errorLog(
				"{CourseUser.closeUser.absence} cuid:" + id
				+ ", course_id:" + info.i("course_id")
				+ ", absence_cnt:" + absenceCnt
				+ ", limit_absence_cnt:" + info.i("limit_absence_cnt")
				+ ", course_type:" + info.s("course_type")
			);
		}

		String status = getCompleteStatus(info, info, meetModulePass, absenceFail);
		String failStr = "F".equals(status) ? getFailReason(info, info, absenceFail) : "";

//		boolean isEnd = "Y".equals(endYn) && isComplete ? true : (info.i("end_date") < Malgn.parseInt(Malgn.time("yyyyMMdd")));
//		boolean isEnd = "Y".equals(endYn) ? true : (info.b("complete_auto_yn") && isComplete);

		//if(!"Y".equals(endYn) && !(info.b("complete_auto_yn") && isComplete)) return -1;

		String compNo = info.s("year") + "-" + info.i("step") + "-" + id;
		if("Y".equals(info.s("complete_no_yn"))) {
			if("R".equals(info.s("postfix_type"))) {
				compNo = getCompNo(getUserRank(id, info.i("course_id"), info.s("postfix_ord")), info.s("complete_prefix"), info.i("postfix_cnt"));
			} else {
				compNo = info.s("complete_prefix") + "-" + Malgn.strrpad("" + id, info.i("postfix_cnt"), "0");
			}
		}

		if(!"Y".equals(endYn) && info.b("complete_auto_yn") && "P".equals(status)) {
			item("complete_status", status);
			item("complete_yn", "F".equals(status) ? "N" : "Y");
			item("complete_no", compNo);
			item("complete_date", Malgn.time("yyyyMMddHHmmss"));
			item("fail_reason", failStr);
			if(!update("id = " + id)) { return -1; }
		} else if("Y".equals(endYn)) {
			item("close_yn", "Y");
			item("complete_status", status);
			item("complete_yn", "F".equals(status) ? "N" : "Y");
			item("complete_no", compNo);
			item("complete_date", Malgn.time("yyyyMMddHHmmss"));
			item("close_date", Malgn.time("yyyyMMddHHmmss"));
			item("close_user_id", userId);
			item("fail_reason", failStr);
			if(!update("id = " + id)) { return -1; }
		}
		
		return 1;
	}

	//권한검사
	public boolean accessible(int cuid, int userId, int siteId) {
		if(cuid == 0 || userId == 0 || siteId == 0) return false;

		String today = Malgn.time("yyyyMMdd");

		DataSet cuinfo = this.query(
			" SELECT a.end_date, c.restudy_yn, c.restudy_day "
			+ " FROM " + this.table + " a "
			+ " INNER JOIN " + new CourseDao().table + " c ON c.id = a.course_id AND c.status != -1 " //중지과정도 기존수강생은 들을 수 있음
			+ " WHERE a.id = " + cuid + " AND a.user_id = " + userId + " AND a.status IN (1, 3) AND a.start_date <= '" + today + "'"
		);
		if(!cuinfo.next()) return false;

		if(0 >= Malgn.diffDate("D", cuinfo.s("end_date"), today)) return true;
		else if(cuinfo.b("restudy_yn") && 0 >= Malgn.diffDate("D", Malgn.addDate("D", cuinfo.i("restudy_day"), cuinfo.s("end_date"), "yyyyMMdd"), today)) return true;
		
		return false;
	}

	public int getCourseUserId(int courseId, int userId, int siteId) {
		if(courseId == 0 || userId == 0 || siteId == 0) return -1;

		String today = Malgn.time("yyyyMMdd");

		return this.getOneInt(
			" SELECT id FROM " + this.table + " "
			+ " WHERE course_id = " + courseId + " AND user_id = " + userId + " AND status IN (1, 3) "
			+ " AND start_date <= '" + today + "' AND end_date >= '" + today + "' "
			+ " ORDER BY id DESC "
		);
	}

	public int getUserRank (int cuid, int cid, String sortType) {
		DataSet info = this.query(
				"SELECT id, reg_date, @rank:= @rank + 1 ranks"
						+ " FROM " + this.table + ", (SELECT @rank := 0) s "
						+ " WHERE course_id = " + cid + ""
						+ " ORDER BY reg_date " + ("A".equals(sortType) ? "asc" : "desc"));

		while(info.next()) {
			if(cuid == info.i("id")) return info.i("ranks");
		}

		return -1;
	}

	public String getCompNo(int rank, String prefix, int postfixCnt) {
		return prefix + "-" + Malgn.strrpad("" + rank, postfixCnt, "0");
	}

	public boolean setCompleteNo(int cid, int siteId) {
		CourseDao course = new CourseDao();

		DataSet info = course.find(" id = ? AND site_id = ? AND status != ? ", new Object[] { cid, siteId, -1 });
		if(!info.next()) { return false; }

		String sortType = info.s("postfix_type");
		String prefix = info.s("complete_prefix");
		String postfixSort = info.s("postfix_ord");
		int postfixCnt = info.i("postfix_cnt");

		DataSet culist = this.find(" course_id = " + cid + " AND site_id = " + siteId +  " AND status IN (1,3) ", " * " , " reg_date " + ("A".equals(postfixSort) ? "asc" : "desc"));

		while(culist.next()) {
			this.clear();
			String compno;
			if("R".equals(sortType)) {
				compno = prefix + "-" + Malgn.strrpad("" + culist.i("__ord"), postfixCnt, "0");
			} else {
				compno = prefix + "-" + Malgn.strrpad("" + culist.i("id"), postfixCnt, "0");
			}
			this.item("complete_no", compno);
			if(!this.update(" id = " + culist.i("id") + " AND site_id = " + siteId + " ")) { return false; }
		}

		return true;
	}
}
