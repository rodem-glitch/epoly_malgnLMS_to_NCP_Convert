package kr.go.growailms.tutor.haksa;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: haksa_*.jsp 11개의 비즈니스 로직을 담당합니다.
 *     학사 시스템은 5-part 복합키를 사용하고, 커리큘럼/평가/시험 설정을 JSON으로 저장합니다.
 */
@Service
public class TutorHaksaService {

    private static final Logger log = LoggerFactory.getLogger(TutorHaksaService.class);

    private final TutorHaksaJdbcRepository repo;
    private final ObjectMapper objectMapper;

    public TutorHaksaService(TutorHaksaJdbcRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    // ========== haksa_resolve.jsp ==========

    /**
     * 왜: 학사 과목을 LMS 과목으로 매핑합니다.
     *     기존 매핑이 없으면 LM_COURSE를 자동 생성하고, 교수자와 학생도 등록합니다.
     */
    public Map<String, Object> resolve(TutorAuthContext.AuthInfo auth,
                                        String courseCode, String openYear, String openTerm,
                                        String bunbanCode, String groupCode) {
        // 1) 학사 과목 미러 테이블에서 과목명 조회
        Optional<Map<String, Object>> polyOpt = repo.findPolyCourse(
                courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId());

        String courseName = polyOpt.map(p -> (String) p.getOrDefault("course_name", courseCode))
                .orElse(courseCode);

        // 2) 기존 LMS 매핑 과목 찾기
        Optional<Map<String, Object>> lmsCourseOpt = repo.findMappedLmsCourse(
                courseCode, openYear, openTerm, auth.siteId());

        long lmsCourseId;
        boolean created = false;

        if (lmsCourseOpt.isPresent()) {
            lmsCourseId = ((Number) lmsCourseOpt.get().get("id")).longValue();
        } else {
            // 3) 없으면 LMS 과목 자동 생성
            lmsCourseId = repo.createLmsCourseFromHaksa(courseCode, courseName, openYear, auth.siteId());
            created = true;
            log.info("학사→LMS 과목 자동 생성: courseCode={}, openYear={}, newLmsCourseId={}",
                    courseCode, openYear, lmsCourseId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_id", lmsCourseId);
        result.put("course_code", courseCode);
        result.put("course_name", courseName);
        result.put("open_year", openYear);
        result.put("open_term", openTerm);
        result.put("created", created);
        return result;
    }

    // ========== haksa_students_list.jsp ==========

    public List<Map<String, Object>> listStudents(TutorAuthContext.AuthInfo auth,
                                                   String courseCode, String openYear, String openTerm,
                                                   String bunbanCode, String groupCode, String keyword) {
        List<Map<String, Object>> students = repo.listPolyStudents(
                courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId(), keyword);

        // 왜: 마지막 동기화 시간을 함께 제공하여, 데이터가 얼마나 최신인지 알 수 있게 합니다.
        Optional<Map<String, Object>> syncLog = repo.getLastSyncLog("LM_POLY_STUDENT", auth.siteId());
        String lastSync = syncLog.map(s -> String.valueOf(s.get("sync_date"))).orElse("");

        for (Map<String, Object> s : students) {
            s.put("last_sync", lastSync);
        }
        return students;
    }

    // ========== haksa_attendance.jsp ==========

    public Map<String, Object> getAttendance(TutorAuthContext.AuthInfo auth,
                                              String courseCode, String openYear, String openTerm,
                                              String bunbanCode, String groupCode,
                                              int sessionId, int courseId) {
        // 왜: 출석은 동영상 시청 완료, 시험 제출, 과제 제출 여부를 합쳐서 판단합니다.
        //     현재는 구조만 반환하고, 상세 출석 로직은 추후 구현합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("course_code", courseCode);
        result.put("session_id", sessionId);
        result.put("course_id", courseId);
        result.put("students", List.of());
        log.info("출석 조회: courseCode={}, sessionId={} (상세 구현 예정)", courseCode, sessionId);
        return result;
    }

    // ========== haksa_curriculum_get.jsp ==========

    public Map<String, Object> getCurriculum(TutorAuthContext.AuthInfo auth,
                                              String courseCode, String openYear, String openTerm,
                                              String bunbanCode, String groupCode) {
        Optional<Map<String, Object>> setting = repo.getCourseSetting(
                courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId());

        Map<String, Object> result = new LinkedHashMap<>();
        if (setting.isPresent()) {
            Object curriculumJson = setting.get().get("curriculum_json");
            result.put("curriculum_json", curriculumJson != null ? curriculumJson.toString() : "[]");
        } else {
            // 왜: 설정이 없으면 빈 커리큘럼을 반환합니다.
            //     JSP에서는 LM_COURSE_MODULE에서 자동 복구하는 로직이 있었으나, 별도 구현 필요.
            result.put("curriculum_json", "[]");
        }
        return result;
    }

    // ========== haksa_curriculum_update.jsp ==========

    public void updateCurriculum(TutorAuthContext.AuthInfo auth,
                                  String courseCode, String openYear, String openTerm,
                                  String bunbanCode, String groupCode, String curriculumJson) {
        repo.upsertCourseSetting(courseCode, openYear, openTerm, bunbanCode, groupCode,
                auth.siteId(), "curriculum_json", curriculumJson);
        log.info("학사 커리큘럼 저장: courseCode={}, openYear={}", courseCode, openYear);
    }

    // ========== haksa_course_eval_get.jsp ==========

    public Map<String, Object> getEvaluation(TutorAuthContext.AuthInfo auth,
                                              String courseCode, String openYear, String openTerm,
                                              String bunbanCode, String groupCode) {
        Optional<Map<String, Object>> setting = repo.getCourseSetting(
                courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId());

        Map<String, Object> result = new LinkedHashMap<>();
        if (setting.isPresent()) {
            Object evalJson = setting.get().get("eval_json");
            result.put("eval_json", evalJson != null ? evalJson.toString() : "{}");
        } else {
            result.put("eval_json", "{}");
        }
        return result;
    }

    // ========== haksa_course_eval_update.jsp ==========

    public void updateEvaluation(TutorAuthContext.AuthInfo auth,
                                  String courseCode, String openYear, String openTerm,
                                  String bunbanCode, String groupCode, String evalJson) {
        repo.upsertCourseSetting(courseCode, openYear, openTerm, bunbanCode, groupCode,
                auth.siteId(), "eval_json", evalJson);
        log.info("학사 평가설정 저장: courseCode={}, openYear={}", courseCode, openYear);
    }

    // ========== haksa_exam_get.jsp ==========

    public Map<String, Object> getExams(TutorAuthContext.AuthInfo auth,
                                         String courseCode, String openYear, String openTerm,
                                         String bunbanCode, String groupCode) {
        Optional<Map<String, Object>> setting = repo.getCourseSetting(
                courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId());

        Map<String, Object> result = new LinkedHashMap<>();
        if (setting.isPresent()) {
            Object examsJson = setting.get().get("exams_json");
            result.put("exams_json", examsJson != null ? examsJson.toString() : "[]");
        } else {
            result.put("exams_json", "[]");
        }
        return result;
    }

    // ========== haksa_exam_update.jsp ==========

    public void updateExams(TutorAuthContext.AuthInfo auth,
                             String courseCode, String openYear, String openTerm,
                             String bunbanCode, String groupCode, String examsJson) {
        repo.upsertCourseSetting(courseCode, openYear, openTerm, bunbanCode, groupCode,
                auth.siteId(), "exams_json", examsJson);
        log.info("학사 시험설정 저장: courseCode={}, openYear={}", courseCode, openYear);
    }

    // ========== haksa_grade_list.jsp ==========

    public List<Map<String, Object>> listGrades(TutorAuthContext.AuthInfo auth,
                                                 String courseCode, String openYear, String openTerm,
                                                 String bunbanCode, String groupCode) {
        return repo.listGrades(courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId());
    }

    // ========== haksa_grade_update.jsp ==========

    /**
     * 왜: haksa_grade_update.jsp는 기존 성적을 전부 지우고 새로 입력합니다 (replace 방식).
     *     grades_json은 [{"member_key":"...", "grade":"A"}, ...] 형식입니다.
     */
    public void updateGrades(TutorAuthContext.AuthInfo auth,
                              String courseCode, String openYear, String openTerm,
                              String bunbanCode, String groupCode, String gradesJson) {
        // 왜: 기존 성적 전부 삭제 후 재입력 (JSP 원본 로직)
        repo.deleteGrades(courseCode, openYear, openTerm, bunbanCode, groupCode, auth.siteId());

        try {
            List<Map<String, String>> grades = objectMapper.readValue(gradesJson,
                    new TypeReference<List<Map<String, String>>>() {});

            for (Map<String, String> g : grades) {
                String memberKey = g.get("member_key");
                String grade = g.get("grade");
                if (memberKey != null && grade != null) {
                    repo.insertGrade(courseCode, openYear, openTerm, bunbanCode, groupCode,
                            auth.siteId(), memberKey, grade);
                }
            }
            log.info("학사 성적 저장: courseCode={}, openYear={}, gradeCount={}", courseCode, openYear, grades.size());
        } catch (Exception e) {
            log.error("학사 성적 JSON 파싱 실패: courseCode={}, error={}", courseCode, e.getMessage());
            throw new IllegalArgumentException("성적 데이터 형식이 올바르지 않습니다.", e);
        }
    }
}
