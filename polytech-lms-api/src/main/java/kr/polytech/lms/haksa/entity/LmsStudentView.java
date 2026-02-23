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
 * 왜: COM.LMS_STUDENT_VIEW (10컬럼)를 읽기 전용 JPA 엔티티로 매핑합니다.
 */
@Entity
@Immutable
@Getter
@Table(name = "LMS_STUDENT_VIEW", schema = "COM")
@IdClass(LmsStudentView.StudentViewId.class)
public class LmsStudentView {

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

    @Id
    @Column(name = "MEMBER_KEY")
    private String memberKey;

    @Column(name = "VISIBLE")
    private String visible;

    @Getter
    public static class StudentViewId implements Serializable {
        private String courseCode;
        private String openYear;
        private String openTerm;
        private String bunbanCode;
        private String groupCode;
        private String memberKey;
    }
}
