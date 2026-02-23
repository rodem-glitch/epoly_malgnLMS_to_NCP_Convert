package kr.polytech.lms.haksa.controller;

import kr.polytech.lms.haksa.repository.LmsCourseViewRepository;
import kr.polytech.lms.haksa.repository.LmsMemberViewRepository;
import kr.polytech.lms.haksa.repository.LmsProfessorViewRepository;
import kr.polytech.lms.haksa.repository.LmsStudentViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 왜: 학사 Oracle DB 연동 상태를 실시간으로 확인하는 헬스체크 엔드포인트입니다.
 * - GET /haksa/health → 4개 핵심 뷰테이블의 행 수를 조회하여 연결 상태를 반환합니다.
 * - haksa.oracle.url이 설정되지 않으면 이 컨트롤러는 비활성화됩니다.
 */
@Slf4j
@RestController
@RequestMapping("/haksa")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "haksa.oracle.url", matchIfMissing = false)
public class HaksaHealthController {

    private final LmsMemberViewRepository memberRepo;
    private final LmsCourseViewRepository courseRepo;
    private final LmsStudentViewRepository studentRepo;
    private final LmsProfessorViewRepository professorRepo;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean allOk = true;

        // 왜: 4개 핵심 뷰를 각각 COUNT 조회하여 Oracle 연결 + 데이터 존재를 동시에 확인합니다.
        result.put("LMS_MEMBER_VIEW", safeCount("MEMBER", () -> memberRepo.countAll()));
        result.put("LMS_COURSE_VIEW", safeCount("COURSE", () -> courseRepo.countAll()));
        result.put("LMS_STUDENT_VIEW", safeCount("STUDENT", () -> studentRepo.countAll()));
        result.put("LMS_PROFESSOR_VIEW", safeCount("PROFESSOR", () -> professorRepo.countAll()));

        for (Object v : result.values()) {
            if (v instanceof String s && s.startsWith("ERROR")) {
                allOk = false;
                break;
            }
        }

        result.put("status", allOk ? "OK" : "PARTIAL_FAIL");
        return allOk ? ResponseEntity.ok(result) : ResponseEntity.status(503).body(result);
    }

    private Object safeCount(String label, CountSupplier supplier) {
        try {
            long count = supplier.get();
            log.debug("학사 뷰 {} 조회 성공: {}건", label, count);
            return count;
        } catch (Exception e) {
            log.error("학사 뷰 {} 조회 실패: {}", label, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    @FunctionalInterface
    private interface CountSupplier {
        long get();
    }
}
