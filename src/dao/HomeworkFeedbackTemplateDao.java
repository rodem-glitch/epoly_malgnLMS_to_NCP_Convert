package dao;

import malgnsoft.db.*;

public class HomeworkFeedbackTemplateDao extends DataObject {

	public String[] statusList = { "1=>사용", "0=>중지" };
	public String[] statusListMsg = { "1=>list.homework_feedback_template.status_list.1", "0=>list.homework_feedback_template.status_list.0" };

	public HomeworkFeedbackTemplateDao() {
		// 왜: 교수자별/과목별 피드백 템플릿을 독립적으로 보관하기 위한 테이블입니다.
		this.table = "LM_HOMEWORK_FEEDBACK_TEMPLATE";
		this.PK = "id";
	}

	public boolean isValidSort(int sort) {
		return 0 <= sort && sort <= 9999;
	}
}
