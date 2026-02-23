package kr.polytech.lms.haksa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * 왜: COM.LMS_PROFESSOR_VIEW (11컬럼)를 읽기 전용 JPA 엔티티로 매핑합니다.
 */
@Entity
@Immutable
@Getter
@Table(name = "LMS_PROFESSOR_VIEW", schema = "COM")
public class LmsProfessorView {

    @Id
    @Column(name = "MEMBER_KEY")
    private String memberKey;

    @Column(name = "PROF_NAME")
    private String profName;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "MOBILE")
    private String mobile;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "DEPT_CODE")
    private String deptCode;

    @Column(name = "DEPT_NAME")
    private String deptName;

    @Column(name = "CAMPUS_CODE")
    private String campusCode;

    @Column(name = "CAMPUS_NAME")
    private String campusName;

    @Column(name = "INSTITUTION_CODE")
    private String institutionCode;

    @Column(name = "INSTITUTION_NAME")
    private String institutionName;
}
