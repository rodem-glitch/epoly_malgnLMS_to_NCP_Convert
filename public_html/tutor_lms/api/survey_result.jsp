<%@ page pageEncoding="utf-8" %><%@ include file="init.jsp" %><%

//왜 필요한가:
//- 교수자가 설문 응답 통계를 확인하고, 익명/실명 설정에 맞춰 상세 응답을 조회해야 개선 피드백이 가능합니다.

int courseId = m.ri("course_id");
int surveyId = m.ri("survey_id");
int questionId = m.ri("question_id");
if(0 == courseId || 0 == surveyId) {
	result.put("rst_code", "1001");
	result.put("rst_message", "course_id, survey_id가 필요합니다.");
	result.print();
	return;
}

CourseDao course = new CourseDao();
CourseTutorDao courseTutor = new CourseTutorDao();
CourseManagerDao courseManager = new CourseManagerDao();
CourseModuleDao courseModule = new CourseModuleDao();
CourseUserDao courseUser = new CourseUserDao();
SurveyDao survey = new SurveyDao();
SurveyItemDao surveyItem = new SurveyItemDao();
SurveyQuestionDao surveyQuestion = new SurveyQuestionDao();
SurveyUserDao surveyUser = new SurveyUserDao();
SurveyResultDao surveyResult = new SurveyResultDao();
UserDao user = new UserDao();

DataSet cinfo = course.find("id = " + courseId + " AND site_id = " + siteId + " AND status != -1");
if(!cinfo.next()) {
	result.put("rst_code", "4040");
	result.put("rst_message", "해당 과목이 없습니다.");
	result.print();
	return;
}

if(!isAdmin) {
	int tutorAccessCount = courseTutor.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND type = 'major' AND site_id = " + siteId);
	int managerAccessCount = courseManager.findCount("course_id = " + courseId + " AND user_id = " + userId + " AND site_id = " + siteId);
	int ownerAccessCount = course.findCount("id = " + courseId + " AND manager_id = " + userId + " AND site_id = " + siteId + " AND status != -1");
	if(0 >= tutorAccessCount && 0 >= managerAccessCount && 0 >= ownerAccessCount) {
		result.put("rst_code", "4031");
		result.put("rst_message", "해당 과목의 설문 결과를 조회할 권한이 없습니다.");
		result.print();
		return;
	}
}

DataSet info = courseModule.query(
	" SELECT a.course_id, a.module_id survey_id, a.module_nm, a.apply_type, a.start_date, a.end_date, a.chapter, a.result_yn "
	+ " , s.survey_nm, s.content, s.item_cnt, s.manager_id "
	+ " FROM " + courseModule.table + " a "
	+ " INNER JOIN " + survey.table + " s ON a.module_id = s.id AND s.site_id = " + siteId + " AND s.status != -1 "
	+ " WHERE a.course_id = " + courseId + " AND a.site_id = " + siteId + " AND a.module = 'survey' AND a.module_id = " + surveyId + " AND a.status = 1 "
);
if(!info.next()) {
	result.put("rst_code", "4041");
	result.put("rst_message", "해당 설문이 과목에 배치되어 있지 않습니다.");
	result.print();
	return;
}

String anonymousYn = "Y".equals(info.s("result_yn")) ? "Y" : "N";
boolean anonymousBlock = "Y".equals(anonymousYn);

if("1".equals(info.s("apply_type"))) {
	info.put("start_date_conv", "".equals(info.s("start_date")) ? "" : m.time("yyyy.MM.dd HH:mm", info.s("start_date")));
	info.put("end_date_conv", "".equals(info.s("end_date")) ? "" : m.time("yyyy.MM.dd HH:mm", info.s("end_date")));
	info.put("apply_conv", info.s("start_date_conv") + " ~ " + info.s("end_date_conv"));
} else {
	info.put("apply_conv", info.i("chapter") == 0 ? "학습시작 전" : info.i("chapter") + "차시 학습 후");
}
info.put("anonymous_yn", anonymousYn);

m.log(
	"survey_result",
	"result_start manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
	+ ", question_id=" + questionId
	+ ", anonymous_yn=" + anonymousYn
);

DataSet stat = courseModule.query(
	" SELECT COUNT(*) u_cnt "
	+ " , SUM(CASE WHEN su.course_user_id IS NOT NULL THEN 1 ELSE 0 END) s_cnt "
	+ " FROM " + courseUser.table + " cu "
	+ " LEFT JOIN " + surveyUser.table + " su ON su.site_id = " + siteId + " AND su.survey_id = " + surveyId + " AND su.course_user_id = cu.id AND su.status = 1 "
	+ " WHERE cu.course_id = " + courseId + " AND cu.site_id = " + siteId + " AND cu.status IN (1,3) "
);
if(!stat.next()) stat.addRow();
stat.put("u_cnt", stat.i("u_cnt"));
stat.put("s_cnt", stat.i("s_cnt"));
stat.put("survey_rate", m.nf(stat.i("u_cnt") > 0 ? (double)stat.i("s_cnt") / (double)stat.i("u_cnt") * 100.0 : 0.0, 1));

DataSet questions = surveyItem.query(
	" SELECT a.question_id, a.sort "
	+ " , q.question_type, q.question, q.item_cnt "
	+ " , q.item1, q.item2, q.item3, q.item4, q.item5, q.item6, q.item7, q.item8, q.item9, q.item10 "
	+ " FROM " + surveyItem.table + " a "
	+ " INNER JOIN " + surveyQuestion.table + " q ON q.id = a.question_id AND q.site_id = " + siteId + " AND q.status = 1 "
	+ " WHERE a.survey_id = " + surveyId + " AND a.status = 1 "
	+ " ORDER BY a.sort ASC "
);

while(questions.next()) {
	String qtype = questions.s("question_type");
	boolean choiceBlock = "1".equals(qtype) || "M".equals(qtype);
	questions.put("choice_block", choiceBlock);
	questions.put("question_type_conv", m.getItem(qtype, surveyQuestion.types));

	if(choiceBlock) {
		java.util.Hashtable<String, Integer> itemCountMap = new java.util.Hashtable<String, Integer>();
		DataSet answers = surveyResult.query(
			" SELECT r.answer, COUNT(*) cnt "
			+ " FROM " + surveyResult.table + " r "
			+ " INNER JOIN " + surveyUser.table + " su ON su.site_id = " + siteId + " AND su.survey_id = r.survey_id AND su.course_user_id = r.course_user_id AND su.status = 1 "
			+ " INNER JOIN " + courseUser.table + " cu ON cu.id = r.course_user_id AND cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status IN (1,3) "
			+ " WHERE r.survey_id = " + surveyId + " AND r.survey_question_id = " + questions.i("question_id") + " AND r.status = 1 "
			+ " AND r.answer IS NOT NULL AND r.answer <> '' "
			+ " GROUP BY r.answer "
		);

		while(answers.next()) {
			String answerText = answers.s("answer");
			if("".equals(answerText)) continue;

			String[] tokenAnswers = answerText.split("\\|\\|");
			for(int i = 0; i < tokenAnswers.length; i++) {
				String token = tokenAnswers[i] != null ? tokenAnswers[i].trim() : "";
				if("".equals(token)) continue;
				itemCountMap.put(token, (itemCountMap.containsKey(token) ? itemCountMap.get(token).intValue() : 0) + answers.i("cnt"));
			}
		}

		for(int i = 1; i <= questions.i("item_cnt"); i++) {
			int selectedCount = itemCountMap.containsKey(i + "") ? itemCountMap.get(i + "").intValue() : 0;
			questions.put("item" + i + "_cnt", selectedCount);
		}

		questions.put("response_cnt", surveyResult.getOneInt(
			" SELECT COUNT(*) "
			+ " FROM " + surveyResult.table + " r "
			+ " INNER JOIN " + surveyUser.table + " su ON su.site_id = " + siteId + " AND su.survey_id = r.survey_id AND su.course_user_id = r.course_user_id AND su.status = 1 "
			+ " INNER JOIN " + courseUser.table + " cu ON cu.id = r.course_user_id AND cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status IN (1,3) "
			+ " WHERE r.survey_id = " + surveyId + " AND r.survey_question_id = " + questions.i("question_id") + " AND r.status = 1 "
			+ " AND r.answer IS NOT NULL AND r.answer <> '' "
		));
	} else {
		questions.put("response_cnt", surveyResult.getOneInt(
			" SELECT COUNT(*) "
			+ " FROM " + surveyResult.table + " r "
			+ " INNER JOIN " + surveyUser.table + " su ON su.site_id = " + siteId + " AND su.survey_id = r.survey_id AND su.course_user_id = r.course_user_id AND su.status = 1 "
			+ " INNER JOIN " + courseUser.table + " cu ON cu.id = r.course_user_id AND cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status IN (1,3) "
			+ " WHERE r.survey_id = " + surveyId + " AND r.survey_question_id = " + questions.i("question_id") + " AND r.status = 1 "
			+ " AND r.answer_text IS NOT NULL AND r.answer_text <> '' "
		));
	}
}

DataSet responses = new DataSet();
if(questionId > 0) {
	DataSet qinfo = surveyItem.query(
		" SELECT a.question_id, a.sort "
		+ " , q.question_type, q.question, q.item_cnt "
		+ " , q.item1, q.item2, q.item3, q.item4, q.item5, q.item6, q.item7, q.item8, q.item9, q.item10 "
		+ " FROM " + surveyItem.table + " a "
		+ " INNER JOIN " + surveyQuestion.table + " q ON q.id = a.question_id AND q.site_id = " + siteId + " AND q.status = 1 "
		+ " WHERE a.survey_id = " + surveyId + " AND a.question_id = " + questionId + " AND a.status = 1 "
	);
	if(!qinfo.next()) {
		result.put("rst_code", "4043");
		result.put("rst_message", "해당 문항이 없습니다.");
		result.print();
		return;
	}

	boolean choiceBlock = "1".equals(qinfo.s("question_type")) || "M".equals(qinfo.s("question_type"));

	responses = surveyResult.query(
		" SELECT r.course_user_id, r.answer, r.answer_text, r.reg_date "
		+ " , u.user_nm, u.login_id "
		+ " FROM " + surveyResult.table + " r "
		+ " INNER JOIN " + surveyUser.table + " su ON su.site_id = " + siteId + " AND su.survey_id = r.survey_id AND su.course_user_id = r.course_user_id AND su.status = 1 "
		+ " INNER JOIN " + courseUser.table + " cu ON cu.id = r.course_user_id AND cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status IN (1,3) "
		+ " INNER JOIN " + user.table + " u ON u.id = cu.user_id AND u.status != -1 "
		+ " WHERE r.survey_id = " + surveyId + " AND r.survey_question_id = " + questionId + " AND r.status = 1 "
		+ " ORDER BY r.reg_date DESC, r.course_user_id ASC "
	);

	while(responses.next()) {
		if(anonymousBlock) {
			responses.put("user_nm", "익명");
			responses.put("login_id", "");
			//왜 필요한가:
			//- 익명 설문에서 내부 식별키(course_user_id)까지 노출되면 실명 추적 단서가 될 수 있어 함께 마스킹합니다.
			responses.put("course_user_id", 0);
		}

		if(choiceBlock) {
			String answerText = responses.s("answer");
			if(!"".equals(answerText)) {
				String[] tokenAnswers = answerText.split("\\|\\|");
				java.util.Vector<String> converted = new java.util.Vector<String>();
				for(int i = 0; i < tokenAnswers.length; i++) {
					String token = tokenAnswers[i] != null ? tokenAnswers[i].trim() : "";
					if("".equals(token)) continue;
					String itemValue = qinfo.s("item" + token);
					converted.add(!"".equals(itemValue) ? itemValue : token);
				}
				responses.put("answer_conv", m.join(", ", converted.toArray(new String[0])));
			} else {
				responses.put("answer_conv", "");
			}
		} else {
			responses.put("answer_conv", responses.s("answer_text"));
		}

		responses.put("reg_date_conv", "".equals(responses.s("reg_date")) ? "" : m.time("yyyy.MM.dd HH:mm", responses.s("reg_date")));
	}

	result.put("rst_question", qinfo);
	result.put("rst_responses", responses);
	result.put("rst_response_count", responses.size());
}

m.log(
	"survey_result",
	"result_done manager_id=" + userId
	+ ", site_id=" + siteId
	+ ", course_id=" + courseId
	+ ", survey_id=" + surveyId
	+ ", question_id=" + questionId
	+ ", question_cnt=" + questions.size()
	+ ", response_cnt=" + responses.size()
	+ ", anonymous_yn=" + anonymousYn
);

result.put("rst_code", "0000");
result.put("rst_message", "성공");
result.put("rst_data", questions);
result.put("rst_survey", info);
result.put("rst_stat", stat);
result.put("rst_anonymous_yn", anonymousYn);
result.print();

%>
