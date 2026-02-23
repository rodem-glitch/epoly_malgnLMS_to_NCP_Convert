package kr.polytech.lms.haksa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;

/**
 * 왜: COM.LMS_COURSE_VIEW (28컬럼)를 읽기 전용 JPA 엔티티로 매핑합니다.
 */
@Entity
@Immutable
@Getter
@Table(name = "LMS_COURSE_VIEW", schema = "COM")
@IdClass(LmsCourseView.CourseViewId.class)
public class LmsCourseView {

    @Id
    @Column(name = "COURSE_CODE")
    private String courseCode;

    @Id
    @Column(name = "OPEN_YEAR")
    private String openYear;

    @Id
    @Column(name = "OPEN_TERM")
    private String openTerm;

    @Id
    @Column(name = "BUNBAN_CODE")
    private String bunbanCode;

    @Id
    @Column(name = "GROUP_CODE")
    private String groupCode;

    @Column(name = "COURSE_NAME")
    private String courseName;

    @Column(name = "COURSE_ENAME")
    private String courseEname;

    @Column(name = "DEPT_CODE")
    private String deptCode;

    @Column(name = "DEPT_NAME")
    private String deptName;

    @Column(name = "GRAD_CODE")
    private String gradCode;

    @Column(name = "GRAD_NAME")
    private String gradName;

    @Column(name = "WEEK")
    private String week;

    @Column(name = "GRADE")
    private String grade;

    @Column(name = "DAY_CD")
    private String dayCd;

    @Column(name = "CLASSROOM")
    private String classroom;

    @Column(name = "CURRICULUM_CODE")
    private String curriculumCode;

    @Column(name = "CURRICULUM_NAME")
    private String curriculumName;

    @Column(name = "VISIBLE")
    private String visible;

    @Column(name = "STARTDATE")
    private String startdate;

    @Column(name = "ENDDATE")
    private String enddate;

    @Getter
    public static class CourseViewId implements Serializable {
        private String courseCode;
        private String openYear;
        private String openTerm;
        private String bunbanCode;
        private String groupCode;
    }
}
