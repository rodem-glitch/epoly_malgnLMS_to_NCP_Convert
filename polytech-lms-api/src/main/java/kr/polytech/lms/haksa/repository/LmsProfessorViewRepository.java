package kr.polytech.lms.haksa.repository;

import kr.polytech.lms.haksa.entity.LmsProfessorView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 왜: COM.LMS_PROFESSOR_VIEW (11컬럼) 읽기 전용 리포지토리입니다.
 */
public interface LmsProfessorViewRepository extends JpaRepository<LmsProfessorView, String> {

    @Query("SELECT COUNT(p) FROM LmsProfessorView p")
    long countAll();
}
