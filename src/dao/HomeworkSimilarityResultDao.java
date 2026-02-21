package dao;

import malgnsoft.db.*;
import malgnsoft.util.*;
import java.util.*;

public class HomeworkSimilarityResultDao extends DataObject {

	public String[] statusList = { "1=>사용", "0=>중지", "-1=>삭제" };

	public HomeworkSimilarityResultDao() {
		this.table = "LM_HOMEWORK_SIMILARITY_RESULT";
		this.PK = "id";
	}

	private static class SubmissionData {
		public int courseUserId = 0;
		public String subject = "";
		public String content = "";
		public HashSet<String> fileTokens = new HashSet<String>();
	}

	private double normalizeThreshold(double thresholdScore) {
		// 왜: 임계치는 0~100 범위에서만 의미가 있으므로, 저장 기준 오입력을 서버에서 정규화합니다.
		if(thresholdScore < 0) return 0.0;
		if(100.0 < thresholdScore) return 100.0;
		return thresholdScore;
	}

	private String normalizeText(String text) {
		if(null == text) return "";
		String v = text;
		v = v.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
		v = v.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
		v = v.replaceAll("(?is)<[^>]+>", " ");
		v = v.replaceAll("&nbsp;", " ");
		v = v.toLowerCase();
		v = v.replaceAll("[^0-9a-zA-Z가-힣]+", " ");
		v = v.replaceAll("\\s+", " ").trim();
		return v;
	}

	private HashSet<String> toTokenSet(String text) {
		HashSet<String> set = new HashSet<String>();
		String normalized = normalizeText(text);
		if("".equals(normalized)) return set;

		String[] tokens = normalized.split(" ");
		for(int i = 0; i < tokens.length; i++) {
			String token = tokens[i].trim();
			if("".equals(token)) continue;
			if(token.length() < 2) continue;
			set.add(token);
		}
		return set;
	}

	private double round2(double v) {
		return Math.round(v * 100.0) / 100.0;
	}

	private double jaccard(HashSet<String> left, HashSet<String> right) {
		if(left.isEmpty() || right.isEmpty()) return 0.0;
		HashSet<String> union = new HashSet<String>(left);
		union.addAll(right);
		if(union.isEmpty()) return 0.0;

		HashSet<String> inter = new HashSet<String>(left);
		inter.retainAll(right);
		return round2((100.0 * inter.size()) / union.size());
	}

	private HashMap<Integer, HashSet<String>> getHomeworkFileTokenMap(int siteId, int homeworkId) {
		HashMap<Integer, HashSet<String>> map = new HashMap<Integer, HashSet<String>>();
		ClFileDao file = new ClFileDao();
		DataSet list = file.find(
			"site_id = " + siteId + " AND module = 'homework_" + homeworkId + "' AND status = 1",
			"module_id, filename",
			"id ASC"
		);
		while(list.next()) {
			int courseUserId = list.i("module_id");
			if(!map.containsKey(courseUserId)) map.put(courseUserId, new HashSet<String>());

			HashSet<String> tokens = map.get(courseUserId);
			tokens.addAll(toTokenSet(list.s("filename")));
		}
		return map;
	}

	private ArrayList<SubmissionData> getSubmittedList(int siteId, int courseId, int homeworkId) {
		ArrayList<SubmissionData> rows = new ArrayList<SubmissionData>();
		HashMap<Integer, HashSet<String>> fileTokenMap = getHomeworkFileTokenMap(siteId, homeworkId);

		HomeworkUserDao homeworkUser = new HomeworkUserDao();
		CourseUserDao courseUser = new CourseUserDao();

		DataSet list = homeworkUser.query(
			" SELECT hu.course_user_id, hu.subject, hu.content "
			+ " FROM " + homeworkUser.table + " hu "
			+ " INNER JOIN " + courseUser.table + " cu ON cu.id = hu.course_user_id "
				+ " AND cu.site_id = " + siteId + " AND cu.course_id = " + courseId + " AND cu.status IN (1,3) "
			+ " WHERE hu.site_id = " + siteId + " AND hu.course_id = " + courseId + " AND hu.homework_id = " + homeworkId + " "
				+ " AND hu.status = 1 AND hu.submit_yn = 'Y' "
			+ " ORDER BY hu.course_user_id ASC "
		);
		while(list.next()) {
			SubmissionData row = new SubmissionData();
			row.courseUserId = list.i("course_user_id");
			row.subject = normalizeText(list.s("subject"));
			row.content = normalizeText(list.s("content"));
			if(fileTokenMap.containsKey(row.courseUserId)) row.fileTokens = fileTokenMap.get(row.courseUserId);
			rows.add(row);
		}
		return rows;
	}

	private SubmissionData getSubmissionByCourseUserId(ArrayList<SubmissionData> list, int courseUserId) {
		for(int i = 0; i < list.size(); i++) {
			SubmissionData row = list.get(i);
			if(row.courseUserId == courseUserId) return row;
		}
		return null;
	}

	private void deleteHomeworkResults(int siteId, int courseId, int homeworkId) {
		this.execute(
			"DELETE FROM " + this.table
			+ " WHERE site_id = " + siteId + " AND course_id = " + courseId + " AND homework_id = " + homeworkId
		);
	}

	private void deleteCourseUserResults(int siteId, int courseId, int homeworkId, int courseUserId) {
		this.execute(
			"DELETE FROM " + this.table
			+ " WHERE site_id = " + siteId + " AND course_id = " + courseId + " AND homework_id = " + homeworkId
			+ " AND (left_course_user_id = " + courseUserId + " OR right_course_user_id = " + courseUserId + ")"
		);
	}

	private boolean insertPairResult(
		int runId,
		int siteId,
		int courseId,
		int homeworkId,
		int leftCourseUserId,
		int rightCourseUserId,
		double subjectScore,
		double contentScore,
		double fileScore,
		double totalScore,
		String reasonJson,
		String now
	) {
		int newId = this.getSequence();
		this.item("id", newId);
		this.item("run_id", runId);
		this.item("site_id", siteId);
		this.item("course_id", courseId);
		this.item("homework_id", homeworkId);
		this.item("left_course_user_id", leftCourseUserId);
		this.item("right_course_user_id", rightCourseUserId);
		this.item("subject_score", subjectScore);
		this.item("content_score", contentScore);
		this.item("file_score", fileScore);
		this.item("total_score", totalScore);
		this.item("reason_json", reasonJson);
		this.item("reg_date", now);
		this.item("mod_date", now);
		this.item("status", 1);
		return this.insert();
	}

	private String buildReasonJson(
		int subjectLeftSize,
		int subjectRightSize,
		int contentLeftSize,
		int contentRightSize,
		int fileLeftSize,
		int fileRightSize
	) {
		return "{"
			+ "\"subject_left_tokens\":" + subjectLeftSize + ","
			+ "\"subject_right_tokens\":" + subjectRightSize + ","
			+ "\"content_left_tokens\":" + contentLeftSize + ","
			+ "\"content_right_tokens\":" + contentRightSize + ","
			+ "\"file_left_tokens\":" + fileLeftSize + ","
			+ "\"file_right_tokens\":" + fileRightSize
			+ "}";
	}

	public Hashtable<String, Object> runFullAnalysis(
		int siteId,
		int courseId,
		int homeworkId,
		int requestedUserId,
		double thresholdScore,
		String runType
	) {
		Hashtable<String, Object> out = new Hashtable<String, Object>();
		out.put("success", "N");
		out.put("run_id", 0);
		out.put("pair_total", 0);
		out.put("pair_saved", 0);
		out.put("message", "유사도 분석 중 오류가 발생했습니다.");

		double safeThreshold = normalizeThreshold(thresholdScore);
		String now = Malgn.time("yyyyMMddHHmmss");

		HomeworkSimilarityRunDao runDao = new HomeworkSimilarityRunDao();
		int runId = runDao.startRun(siteId, courseId, homeworkId, runType, safeThreshold, requestedUserId, now);
		if(runId == 0) {
			out.put("message", "유사도 실행 이력을 저장하지 못했습니다.");
			return out;
		}

		int pairTotal = 0;
		int pairSaved = 0;
		try {
			ArrayList<SubmissionData> rows = getSubmittedList(siteId, courseId, homeworkId);
			deleteHomeworkResults(siteId, courseId, homeworkId);

			for(int i = 0; i < rows.size(); i++) {
				SubmissionData left = rows.get(i);
				HashSet<String> leftSubjectTokens = toTokenSet(left.subject);
				HashSet<String> leftContentTokens = toTokenSet(left.content);
				for(int j = i + 1; j < rows.size(); j++) {
					SubmissionData right = rows.get(j);
					pairTotal++;

					HashSet<String> rightSubjectTokens = toTokenSet(right.subject);
					HashSet<String> rightContentTokens = toTokenSet(right.content);

					double subjectScore = jaccard(leftSubjectTokens, rightSubjectTokens);
					double contentScore = jaccard(leftContentTokens, rightContentTokens);
					double fileScore = jaccard(left.fileTokens, right.fileTokens);
					double totalScore = round2(subjectScore * 0.2 + contentScore * 0.7 + fileScore * 0.1);

					if(totalScore < safeThreshold) continue;

					int leftId = Math.min(left.courseUserId, right.courseUserId);
					int rightId = Math.max(left.courseUserId, right.courseUserId);
					String reasonJson = buildReasonJson(
						leftSubjectTokens.size(), rightSubjectTokens.size(),
						leftContentTokens.size(), rightContentTokens.size(),
						left.fileTokens.size(), right.fileTokens.size()
					);

					if(!insertPairResult(
						runId, siteId, courseId, homeworkId,
						leftId, rightId,
						subjectScore, contentScore, fileScore, totalScore,
						reasonJson, now
					)) {
						runDao.finishFail(runId, "결과 저장 실패", Malgn.time("yyyyMMddHHmmss"));
						out.put("run_id", runId);
						out.put("pair_total", pairTotal);
						out.put("pair_saved", pairSaved);
						out.put("message", "유사도 결과를 저장하지 못했습니다.");
						return out;
					}
					pairSaved++;
				}
			}
		} catch(Exception e) {
			runDao.finishFail(runId, "분석 실행 중 예외: " + e.getMessage(), Malgn.time("yyyyMMddHHmmss"));
			out.put("run_id", runId);
			out.put("pair_total", pairTotal);
			out.put("pair_saved", pairSaved);
			out.put("message", "유사도 분석 중 예외가 발생했습니다.");
			return out;
		}

		String endNow = Malgn.time("yyyyMMddHHmmss");
		String doneMessage = "분석 완료";
		if(!runDao.finishSuccess(runId, pairTotal, pairSaved, doneMessage, endNow)) {
			out.put("run_id", runId);
			out.put("pair_total", pairTotal);
			out.put("pair_saved", pairSaved);
			out.put("message", "유사도 실행 이력 종료 저장에 실패했습니다.");
			return out;
		}

		out.put("success", "Y");
		out.put("run_id", runId);
		out.put("pair_total", pairTotal);
		out.put("pair_saved", pairSaved);
		out.put("message", doneMessage);
		return out;
	}

	public Hashtable<String, Object> runIncrementalAnalysis(
		int siteId,
		int courseId,
		int homeworkId,
		int targetCourseUserId,
		int requestedUserId,
		double thresholdScore,
		String runType
	) {
		Hashtable<String, Object> out = new Hashtable<String, Object>();
		out.put("success", "N");
		out.put("run_id", 0);
		out.put("pair_total", 0);
		out.put("pair_saved", 0);
		out.put("message", "유사도 자동 분석 중 오류가 발생했습니다.");

		double safeThreshold = normalizeThreshold(thresholdScore);
		String now = Malgn.time("yyyyMMddHHmmss");

		HomeworkSimilarityRunDao runDao = new HomeworkSimilarityRunDao();
		int runId = runDao.startRun(siteId, courseId, homeworkId, runType, safeThreshold, requestedUserId, now);
		if(runId == 0) {
			out.put("message", "유사도 실행 이력을 저장하지 못했습니다.");
			return out;
		}

		int pairTotal = 0;
		int pairSaved = 0;
		try {
			ArrayList<SubmissionData> rows = getSubmittedList(siteId, courseId, homeworkId);
			deleteCourseUserResults(siteId, courseId, homeworkId, targetCourseUserId);

			SubmissionData target = getSubmissionByCourseUserId(rows, targetCourseUserId);
			if(null == target) {
				String endNow = Malgn.time("yyyyMMddHHmmss");
				runDao.finishSuccess(runId, 0, 0, "대상 제출 없음(쌍 삭제만 수행)", endNow);
				out.put("success", "Y");
				out.put("run_id", runId);
				out.put("pair_total", 0);
				out.put("pair_saved", 0);
				out.put("message", "대상 제출 없음(쌍 삭제만 수행)");
				return out;
			}

			HashSet<String> targetSubjectTokens = toTokenSet(target.subject);
			HashSet<String> targetContentTokens = toTokenSet(target.content);
			for(int i = 0; i < rows.size(); i++) {
				SubmissionData other = rows.get(i);
				if(other.courseUserId == targetCourseUserId) continue;
				pairTotal++;

				HashSet<String> otherSubjectTokens = toTokenSet(other.subject);
				HashSet<String> otherContentTokens = toTokenSet(other.content);

				double subjectScore = jaccard(targetSubjectTokens, otherSubjectTokens);
				double contentScore = jaccard(targetContentTokens, otherContentTokens);
				double fileScore = jaccard(target.fileTokens, other.fileTokens);
				double totalScore = round2(subjectScore * 0.2 + contentScore * 0.7 + fileScore * 0.1);

				if(totalScore < safeThreshold) continue;

				int leftId = Math.min(targetCourseUserId, other.courseUserId);
				int rightId = Math.max(targetCourseUserId, other.courseUserId);
				String reasonJson = buildReasonJson(
					targetSubjectTokens.size(), otherSubjectTokens.size(),
					targetContentTokens.size(), otherContentTokens.size(),
					target.fileTokens.size(), other.fileTokens.size()
				);

				if(!insertPairResult(
					runId, siteId, courseId, homeworkId,
					leftId, rightId,
					subjectScore, contentScore, fileScore, totalScore,
					reasonJson, now
				)) {
					runDao.finishFail(runId, "결과 저장 실패", Malgn.time("yyyyMMddHHmmss"));
					out.put("run_id", runId);
					out.put("pair_total", pairTotal);
					out.put("pair_saved", pairSaved);
					out.put("message", "유사도 결과를 저장하지 못했습니다.");
					return out;
				}
				pairSaved++;
			}
		} catch(Exception e) {
			runDao.finishFail(runId, "증분 분석 실행 중 예외: " + e.getMessage(), Malgn.time("yyyyMMddHHmmss"));
			out.put("run_id", runId);
			out.put("pair_total", pairTotal);
			out.put("pair_saved", pairSaved);
			out.put("message", "유사도 자동 분석 중 예외가 발생했습니다.");
			return out;
		}

		String endNow = Malgn.time("yyyyMMddHHmmss");
		String doneMessage = "자동 분석 완료";
		if(!runDao.finishSuccess(runId, pairTotal, pairSaved, doneMessage, endNow)) {
			out.put("run_id", runId);
			out.put("pair_total", pairTotal);
			out.put("pair_saved", pairSaved);
			out.put("message", "유사도 실행 이력 종료 저장에 실패했습니다.");
			return out;
		}

		out.put("success", "Y");
		out.put("run_id", runId);
		out.put("pair_total", pairTotal);
		out.put("pair_saved", pairSaved);
		out.put("message", doneMessage);
		return out;
	}
}

