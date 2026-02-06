package kr.go.growailms.tutor.course;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 왜: course_*.jsp 20개의 비즈니스 로직을 모아둔 서비스입니다.
 *     각 JSP에서 init.jsp 인증 이후 수행하던 로직(권한 체크, 데이터 가공, DB 호출)을 담당합니다.
 */
@Service
public class TutorCourseService {

    private static final Logger log = LoggerFactory.getLogger(TutorCourseService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TutorCourseJdbcRepository repo;

    public TutorCourseService(TutorCourseJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canAccessCourse(TutorAuthContext.AuthInfo auth, int courseId) {
        // 왜: 관리자는 전체 과목, 교수자는 주담당 과목만 접근 가능합니다.
        return auth.isAdmin() || repo.isMajorTutor(auth.userId(), courseId, auth.siteId());
    }

    // ========== course_list.jsp ==========

    public List<Map<String, Object>> listCourses(TutorAuthContext.AuthInfo auth,
                                                  String keyword, String year, int tutorId) {
        List<Map<String, Object>> rows = repo.listCourses(
                auth.userId(), auth.siteId(), auth.isAdmin(), keyword, year, tutorId);

        String today = LocalDate.now().format(DATE_FORMAT);

        // 왜: JSP에서 하던 화면용 가공(날짜 포맷, 상태 라벨, 코드→텍스트 변환)을 서비스에서 수행합니다.
        for (Map<String, Object> row : rows) {
            formatCourseRow(row, today);
        }
        return rows;
    }

    // ========== course_view.jsp / course_info_get.jsp ==========

    public Optional<Map<String, Object>> getCourseDetail(TutorAuthContext.AuthInfo auth, int courseId) {
        if (!canAccessCourse(auth, courseId)) {
            return Optional.empty();
        }
        return repo.getCourseDetail(courseId, auth.siteId())
                .map(row -> {
                    formatCourseRow(row, LocalDate.now().format(DATE_FORMAT));
                    return row;
                });
    }

    // ========== course_info_update.jsp ==========

    public boolean updateCourseInfo(TutorAuthContext.AuthInfo auth, int courseId,
                                    String content1, String content2) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        int updated = repo.updateCourseInfo(courseId, auth.siteId(), content1, content2);
        return updated > 0;
    }

    // ========== course_insert.jsp ==========

    public long insertCourse(TutorAuthContext.AuthInfo auth, int tutorId, String courseNm, String year,
                             String studySdate, String studyEdate, int programId, int categoryId,
                             String semester, int credit, String content1, String content2, String courseFile) {
        int step = repo.calcNextStep(programId, year, auth.siteId());

        Map<String, Object> courseData = new HashMap<>();
        courseData.put("site_id", auth.siteId());
        courseData.put("subject_id", programId);
        courseData.put("course_nm", courseNm);
        courseData.put("year", year);
        courseData.put("step", step);
        courseData.put("study_sdate", studySdate);
        courseData.put("study_edate", studyEdate);
        courseData.put("category_id", categoryId);
        courseData.put("content1", content1);
        courseData.put("content2", content2);
        courseData.put("course_file", courseFile);

        long newId = repo.insertCourse(courseData);
        repo.insertCourseTutor(newId, tutorId, auth.siteId());

        log.info("과목 생성: id={}, courseNm={}, tutorId={}", newId, courseNm, tutorId);
        return newId;
    }

    // ========== course_copy.jsp ==========

    public long copyCourse(TutorAuthContext.AuthInfo auth, int sourceCourseId, String courseNm, int tutorId) {
        return repo.copyCourse(sourceCourseId, courseNm, tutorId, auth.siteId());
    }

    // ========== course_categories.jsp ==========

    public List<Map<String, Object>> listCategories(long siteId) {
        List<Map<String, Object>> rows = repo.listCategories(siteId);
        // 왜: 트리 뷰용 label을 추가합니다.
        for (Map<String, Object> row : rows) {
            row.put("label", row.get("category_nm"));
            row.put("name_conv", row.get("category_nm"));
        }
        return rows;
    }

    // ========== course_years.jsp ==========

    public List<Map<String, Object>> listYears(TutorAuthContext.AuthInfo auth, int tutorId) {
        List<Map<String, Object>> years = repo.listYears(auth.userId(), auth.siteId(), auth.isAdmin(), tutorId);

        // 왜: DB에 연도가 없으면 현재 연도 ± 2년을 반환합니다 (JSP 폴백 로직).
        if (years.isEmpty()) {
            int currentYear = LocalDate.now().getYear();
            for (int y = currentYear + 2; y >= currentYear - 2; y--) {
                years.add(Map.of("year", String.valueOf(y)));
            }
        }
        return years;
    }

    // ========== course_certificate_update.jsp ==========

    public boolean updateCertificate(TutorAuthContext.AuthInfo auth, int courseId, Map<String, String> params) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        return repo.updateCertificate(courseId, auth.siteId(), params) > 0;
    }

    // ========== course_evaluation_get.jsp ==========

    public Optional<Map<String, Object>> getEvaluation(TutorAuthContext.AuthInfo auth, int courseId) {
        if (!canAccessCourse(auth, courseId)) {
            return Optional.empty();
        }
        return repo.getEvaluation(courseId, auth.siteId());
    }

    // ========== course_evaluation_update.jsp ==========

    public boolean updateEvaluation(TutorAuthContext.AuthInfo auth, int courseId, Map<String, String> params) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        return repo.updateEvaluation(courseId, auth.siteId(), params) > 0;
    }

    // ========== course_feedback_report.jsp ==========

    public Optional<Map<String, Object>> getFeedbackReport(TutorAuthContext.AuthInfo auth,
                                                            int courseId, String startDate, String endDate) {
        if (!canAccessCourse(auth, courseId)) {
            return Optional.empty();
        }
        // 왜: 피드백 보고서는 과제 제출물 + QnA를 합쳐서 반환합니다.
        //     복잡한 다중 테이블 조인이 필요하므로, 별도 리포지토리 메서드로 분리합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("homework", List.of());  // TODO: 과제 제출물 조회
        result.put("qna", List.of());       // TODO: QnA 조회
        log.info("피드백 보고서 조회: courseId={} (상세 구현 예정)", courseId);
        return Optional.of(result);
    }

    // ========== course_image_upload.jsp ==========

    public Map<String, Object> uploadCourseImage(TutorAuthContext.AuthInfo auth, int courseId, MultipartFile file) {
        // 왜: 이미지 업로드는 파일 시스템 접근이 필요하므로, Spring의 MultipartFile을 사용합니다.
        //     저장 경로는 레거시와 동일한 public_html/data/ 하위를 사용합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("file_name", file.getOriginalFilename());
        result.put("course_id", courseId);
        log.info("과목 이미지 업로드: courseId={}, fileName={} (파일 저장 구현 예정)", courseId, file.getOriginalFilename());
        return result;
    }

    // ========== course_list_combined.jsp ==========

    public Map<String, Object> listCombined(TutorAuthContext.AuthInfo auth, String tab, String year,
                                             String keyword, String haksaCategory, String haksaGrad,
                                             String haksaCurriculum, String sortOrder, int page, int pageSize) {
        // 왜: combined 목록은 프리즘(LM_COURSE)과 학사(LM_POLY_COURSE) 두 데이터 소스를
        //     탭으로 분리하여 조회합니다. 현재는 프리즘 탭만 구현합니다.
        Map<String, Object> result = new LinkedHashMap<>();

        if ("prism".equals(tab)) {
            List<Map<String, Object>> data = repo.listCourses(
                    auth.userId(), auth.siteId(), auth.isAdmin(), keyword, year, 0);
            String today = LocalDate.now().format(DATE_FORMAT);
            for (Map<String, Object> row : data) {
                row.put("source_type", "prism");
                formatCourseRow(row, today);
            }
            // 왜: 페이지네이션 적용
            int totalCount = data.size();
            int fromIdx = Math.min((page - 1) * pageSize, totalCount);
            int toIdx = Math.min(fromIdx + pageSize, totalCount);
            List<Map<String, Object>> paged = data.subList(fromIdx, toIdx);

            result.put("data", paged);
            result.put("message", "성공 (프리즘:" + totalCount + "건)");
            result.put("total_count", totalCount);
        } else {
            // 왜: 학사 탭은 LM_POLY_COURSE 미러 테이블 조회 + 외부 API 폴백이 필요합니다.
            //     현재는 빈 목록을 반환하고, 추후 구현합니다.
            result.put("data", List.of());
            result.put("message", "성공 (학사:0건, 구현 예정)");
            result.put("total_count", 0);
        }
        return result;
    }

    // ========== course_list_poly.jsp ==========

    public List<Map<String, Object>> listPoly(TutorAuthContext.AuthInfo auth, String year, int cnt) {
        // 왜: Poly(학사) 과목은 외부 API 호출이 필요합니다.
        //     현재는 빈 목록을 반환하고, 추후 구현합니다.
        log.info("학사 과목 목록 조회 (구현 예정): year={}, cnt={}", year, cnt);
        return List.of();
    }

    // ========== course_resolve.jsp ==========

    public Optional<Map<String, Object>> resolveCourse(TutorAuthContext.AuthInfo auth,
                                                        String courseId, String sourceType) {
        // 왜: resolve는 courseId가 숫자(프리즘)인지 복합키(학사)인지에 따라 분기합니다.
        try {
            int numId = Integer.parseInt(courseId);
            if (!canAccessCourse(auth, numId)) {
                return Optional.empty();
            }
            return repo.getCourseDetail(numId, auth.siteId())
                    .map(row -> {
                        row.put("source_type", "prism");
                        formatCourseRow(row, LocalDate.now().format(DATE_FORMAT));
                        return row;
                    });
        } catch (NumberFormatException e) {
            // 왜: 숫자가 아닌 복합키는 학사 과목입니다 (추후 구현).
            log.info("학사 과목 resolve 요청 (구현 예정): courseId={}", courseId);
            return Optional.empty();
        }
    }

    // ========== course_set_program.jsp ==========

    public boolean setProgram(TutorAuthContext.AuthInfo auth, int courseId, int programId) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        if (programId > 0 && !repo.programExists(programId, auth.siteId())) {
            return false;
        }
        return repo.updateCourseProgram(courseId, programId, auth.siteId()) > 0;
    }

    // ========== course_students_list.jsp ==========

    public List<Map<String, Object>> listStudents(TutorAuthContext.AuthInfo auth, int courseId, String keyword) {
        if (!canAccessCourse(auth, courseId)) {
            return List.of();
        }
        List<Map<String, Object>> students = repo.listStudents(courseId, auth.siteId(), keyword);
        // 왜: JSP에서 편의 필드(student_id, name, progress)를 추가하던 것을 재현합니다.
        for (Map<String, Object> s : students) {
            s.put("student_id", s.get("user_id"));
            s.put("name", s.get("user_nm"));
            s.put("progress", s.get("progress_ratio"));
        }
        return students;
    }

    // ========== course_students_add.jsp ==========

    public Map<String, Object> addStudents(TutorAuthContext.AuthInfo auth, int courseId, String userIds) {
        if (!canAccessCourse(auth, courseId)) {
            return Map.of("rst_code", TutorApiResponse.CODE_NO_EDIT_PERMISSION);
        }

        String[] tokens = userIds.split(",");
        int inserted = 0;
        int skipped = 0;
        int notFound = 0;

        for (String token : tokens) {
            if (token.trim().isEmpty()) continue;
            Optional<Map<String, Object>> userOpt = repo.findUserByIdOrLoginId(token, auth.siteId());
            if (userOpt.isEmpty()) {
                notFound++;
                continue;
            }
            long userId = ((Number) userOpt.get().get("id")).longValue();
            if (repo.isAlreadyEnrolled(courseId, userId, auth.siteId())) {
                skipped++;
                continue;
            }
            try {
                repo.insertCourseUser(courseId, userId, auth.siteId());
                inserted++;
            } catch (Exception e) {
                log.warn("수강생 추가 실패: courseId={}, userId={}, error={}", courseId, userId, e.getMessage());
                skipped++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inserted", inserted);
        result.put("skipped", skipped);
        result.put("not_found", notFound);
        return result;
    }

    // ========== course_students_remove.jsp ==========

    public boolean removeStudent(TutorAuthContext.AuthInfo auth, int courseId, int userId) {
        if (!canAccessCourse(auth, courseId)) {
            return false;
        }
        return repo.softRemoveStudent(courseId, userId, auth.siteId()) > 0;
    }

    // ==================== 내부 유틸리티 ====================

    /**
     * 왜: JSP에서 while(list.next()) 루프 안에서 하던 화면용 가공을 재현합니다.
     *     course_type → "정규/상시", onoff_type → "온라인/집합/혼합", 날짜 포맷, 상태 라벨 등.
     */
    private void formatCourseRow(Map<String, Object> row, String today) {
        // course_id_conv: course_cd가 있으면 사용, 없으면 id
        String courseCd = str(row, "course_cd");
        row.put("course_id_conv", !courseCd.isEmpty() ? courseCd : String.valueOf(row.get("id")));

        // course_type 변환
        String courseType = str(row, "course_type");
        row.put("course_type_conv", switch (courseType) {
            case "R" -> "정규";
            case "A" -> "상시";
            default -> courseType;
        });

        // onoff_type 변환
        String onoffType = str(row, "onoff_type");
        row.put("onoff_type_conv", switch (onoffType) {
            case "O" -> "온라인";
            case "F" -> "집합(오프라인)";
            case "B" -> "혼합(블렌디드)";
            default -> onoffType;
        });

        // 과목명/프로그램명 자르기
        row.put("subject_nm_conv", cutString(str(row, "course_nm"), 100));
        String programNm = str(row, "program_nm");
        row.put("program_nm_conv", !programNm.isEmpty() ? cutString(programNm, 100) : "-");

        // 학습기간 포맷
        String ss = str(row, "study_sdate");
        String se = str(row, "study_edate");
        String ssConv = formatDate(ss);
        String seConv = formatDate(se);
        row.put("period_conv", (!ssConv.isEmpty() && !seConv.isEmpty()) ? (ssConv + " - " + seConv) : "");

        // 상태 라벨 계산
        String rs = str(row, "request_sdate");
        String re = str(row, "request_edate");
        String statusLabel = "대기";
        if (!rs.isEmpty() && !re.isEmpty() && rs.compareTo(today) <= 0 && today.compareTo(re) <= 0) {
            statusLabel = "신청기간";
        } else if (!ss.isEmpty() && !se.isEmpty() && ss.compareTo(today) <= 0 && today.compareTo(se) <= 0) {
            statusLabel = "학습기간";
        } else if (!se.isEmpty() && se.compareTo(today) < 0) {
            statusLabel = "종료";
        }
        row.put("status_label", statusLabel);
    }

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private String formatDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() < 8) return "";
        return yyyymmdd.substring(0, 4) + "." + yyyymmdd.substring(4, 6) + "." + yyyymmdd.substring(6, 8);
    }

    private String cutString(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // 왜: addStudents에서 사용하는 응답 코드 상수 참조
    private static class TutorApiResponse {
        static final String CODE_NO_EDIT_PERMISSION = "4031";
    }
}
