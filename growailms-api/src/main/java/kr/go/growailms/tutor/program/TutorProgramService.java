package kr.go.growailms.tutor.program;

import kr.go.growailms.tutor.common.TutorAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 왜: program_*.jsp 6개의 비즈니스 로직을 모아둔 서비스입니다.
 *     프로그램(LM_SUBJECT)은 과목(LM_COURSE)의 상위 개념으로, 여러 과목을 묶어 관리합니다.
 *     LM_SUBJECT_PLAN은 프로그램의 운영 계획을 저장합니다.
 */
@Service
public class TutorProgramService {

    private static final Logger log = LoggerFactory.getLogger(TutorProgramService.class);

    private final TutorProgramJdbcRepository repo;

    public TutorProgramService(TutorProgramJdbcRepository repo) {
        this.repo = repo;
    }

    // ========== 권한 확인 공통 메서드 ==========

    private boolean canManageProgram(TutorAuthContext.AuthInfo auth, int programId) {
        // 왜: 프로그램 관리는 관리자 또는 해당 프로그램에 속한 과목의 주담당 교수자만 가능합니다.
        return auth.isAdmin() || repo.isProgramTutor(auth.userId(), programId, auth.siteId());
    }

    // ========== program_list.jsp ==========

    public List<Map<String, Object>> listPrograms(TutorAuthContext.AuthInfo auth,
                                                   String keyword, String year) {
        List<Map<String, Object>> rows = repo.listPrograms(
                auth.userId(), auth.siteId(), auth.isAdmin(), keyword, year);

        // 왜: JSP에서 하던 화면용 가공(날짜 포맷, 과목 수 표시)을 서비스에서 수행합니다.
        for (Map<String, Object> row : rows) {
            String startDate = str(row, "start_date");
            String endDate = str(row, "end_date");
            row.put("period_conv", formatPeriod(startDate, endDate));
            row.put("course_nm_conv", cutString(str(row, "course_nm"), 100));
        }
        return rows;
    }

    // ========== program_view.jsp ==========

    public Optional<Map<String, Object>> getProgram(TutorAuthContext.AuthInfo auth, int programId) {
        if (!canManageProgram(auth, programId)) {
            return Optional.empty();
        }
        Optional<Map<String, Object>> programOpt = repo.getProgram(programId, auth.siteId());

        // 왜: 프로그램 상세에는 운영 계획(LM_SUBJECT_PLAN)도 함께 반환합니다.
        programOpt.ifPresent(program -> {
            List<Map<String, Object>> plans = repo.listSubjectPlans(programId, auth.siteId());
            program.put("plans", plans);
        });

        return programOpt;
    }

    // ========== program_insert.jsp ==========

    public long insertProgram(TutorAuthContext.AuthInfo auth, String courseNm, String year,
                              String startDate, String endDate, String description, int categoryId) {
        long newId = repo.insertProgram(auth.siteId(), courseNm, year, startDate, endDate, description, categoryId);
        log.info("프로그램 생성: id={}, courseNm={}, userId={}", newId, courseNm, auth.userId());
        return newId;
    }

    // ========== program_modify.jsp ==========

    public boolean modifyProgram(TutorAuthContext.AuthInfo auth, int programId, String courseNm,
                                 String year, String startDate, String endDate,
                                 String description, int categoryId) {
        if (!canManageProgram(auth, programId)) {
            return false;
        }
        int updated = repo.updateProgram(programId, auth.siteId(), courseNm, year,
                startDate, endDate, description, categoryId);
        if (updated > 0) {
            log.info("프로그램 수정: programId={}, courseNm={}", programId, courseNm);
        }
        return updated > 0;
    }

    // ========== program_delete.jsp ==========

    public boolean deleteProgram(TutorAuthContext.AuthInfo auth, int programId) {
        if (!canManageProgram(auth, programId)) {
            return false;
        }
        // 왜: 프로그램에 활성 과목이 있으면 삭제할 수 없습니다.
        int activeCourses = repo.countActiveCourses(programId, auth.siteId());
        if (activeCourses > 0) {
            log.warn("프로그램 삭제 실패: 활성 과목 {}건 존재 (programId={})", activeCourses, programId);
            return false;
        }

        int updated = repo.softDeleteProgram(programId, auth.siteId());
        if (updated > 0) {
            log.info("프로그램 삭제: programId={}", programId);
        }
        return updated > 0;
    }

    // ========== program_course_list.jsp ==========

    public List<Map<String, Object>> listProgramCourses(TutorAuthContext.AuthInfo auth,
                                                         int programId, String year) {
        // 왜: 프로그램에 속한 과목 목록을 조회합니다. 교수자는 자기 과목만, 관리자는 전체를 봅니다.
        return repo.listProgramCourses(programId, auth.userId(), auth.siteId(), auth.isAdmin(), year);
    }

    // ==================== 내부 유틸리티 ====================

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v != null ? v.toString().trim() : "";
    }

    private String formatPeriod(String startDate, String endDate) {
        String s = formatDate(startDate);
        String e = formatDate(endDate);
        if (!s.isEmpty() && !e.isEmpty()) return s + " - " + e;
        if (!s.isEmpty()) return s + " -";
        return "";
    }

    private String formatDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() < 8) return "";
        return yyyymmdd.substring(0, 4) + "." + yyyymmdd.substring(4, 6) + "." + yyyymmdd.substring(6, 8);
    }

    private String cutString(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
