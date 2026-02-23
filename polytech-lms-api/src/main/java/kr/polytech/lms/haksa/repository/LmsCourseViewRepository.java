package kr.polytech.lms.haksa.repository;

import kr.polytech.lms.haksa.entity.LmsCourseView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 왜: COM.LMS_COURSE_VIEW (28컬럼) 읽기 전용 리포지토리입니다.
 */
public interface LmsCourseViewRepository extends JpaRepository<LmsCourseView, LmsCourseView.CourseViewId> {

    @Query("SELECT COUNT(c) FROM LmsCourseView c")
    long countAll();

    List<LmsCourseView> findByOpenYearAndOpenTerm(String openYear, String openTerm);
}
