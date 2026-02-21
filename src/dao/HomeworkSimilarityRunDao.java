package dao;

import malgnsoft.db.*;

public class HomeworkSimilarityRunDao extends DataObject {

	public HomeworkSimilarityRunDao() {
		this.table = "LM_HOMEWORK_SIMILARITY_RUN";
		this.PK = "id";
	}

	public int startRun(
		int siteId,
		int courseId,
		int homeworkId,
		String runType,
		double thresholdScore,
		int requestedUserId,
		String now
	) {
		int runId = this.getSequence();
		this.item("id", runId);
		this.item("site_id", siteId);
		this.item("course_id", courseId);
		this.item("homework_id", homeworkId);
		this.item("run_type", runType);
		this.item("threshold_score", thresholdScore);
		this.item("pair_total", 0);
		this.item("pair_saved", 0);
		this.item("requested_user_id", requestedUserId);
		this.item("started_at", now);
		this.item("ended_at", "");
		this.item("message", "");
		this.item("status", 0);
		this.item("reg_date", now);
		this.item("mod_date", now);
		if(!this.insert()) return 0;
		return runId;
	}

	public boolean finishSuccess(int runId, int pairTotal, int pairSaved, String message, String now) {
		this.item("pair_total", pairTotal);
		this.item("pair_saved", pairSaved);
		this.item("ended_at", now);
		this.item("message", message);
		this.item("status", 1);
		this.item("mod_date", now);
		return this.update("id = " + runId);
	}

	public boolean finishFail(int runId, String message, String now) {
		this.item("ended_at", now);
		this.item("message", message);
		this.item("status", -1);
		this.item("mod_date", now);
		return this.update("id = " + runId);
	}
}

