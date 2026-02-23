package kr.polytech.lms.haksa.repository;

import kr.polytech.lms.haksa.entity.LmsStudentView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 왜: COM.LMS_STUDENT_VIEW (10컬럼) 읽기 전용 리포지토리입니다.
 */
public interface LmsStudentViewRepository extends JpaRepository<LmsStudentView, LmsStudentView.StudentViewId> {

    @Query("SELECT COUNT(s) FROM LmsStudentView s")
    long countAll();
}
