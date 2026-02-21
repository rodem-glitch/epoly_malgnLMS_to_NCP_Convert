package dao;

import malgnsoft.db.*;

public class HomeworkTemplateDao extends DataObject {

	public String[] statusList = { "1=>사용", "0=>중지" };
	public String[] statusListMsg = { "1=>list.homework_template.status_list.1", "0=>list.homework_template.status_list.0" };
	public String[] submissionTypeList = { "file=>파일", "text=>텍스트", "both=>파일+텍스트" };

	public HomeworkTemplateDao() {
		// 왜: 교수자가 자주 쓰는 과제 설정을 템플릿으로 재사용하기 위한 테이블입니다.
		this.table = "LM_HOMEWORK_TEMPLATE";
		this.PK = "id";
	}

	public boolean isValidSubmissionType(String submissionType) {
		return "file".equals(submissionType) || "text".equals(submissionType) || "both".equals(submissionType);
	}

	public boolean isValidTotalScore(int totalScore) {
		return 0 <= totalScore && totalScore <= 1000;
	}

	public boolean isValidMaxFileSize(int maxFileSize) {
		return 0 <= maxFileSize && maxFileSize <= 2048;
	}

	public boolean isValidLatePenalty(int latePenalty) {
		return 0 <= latePenalty && latePenalty <= 100;
	}
}

