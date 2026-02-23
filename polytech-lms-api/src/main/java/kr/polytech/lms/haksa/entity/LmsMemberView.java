package kr.polytech.lms.haksa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

/**
 * 왜: COM.LMS_MEMBER_VIEW (22컬럼)를 읽기 전용 JPA 엔티티로 매핑합니다.
 * - Oracle 뷰테이블이므로 @Immutable로 쓰기를 방지합니다.
 */
@Entity
@Immutable
@Getter
@Table(name = "LMS_MEMBER_VIEW", schema = "COM")
public class LmsMemberView {

    @Id
    @Column(name = "MEMBER_KEY")
    private String memberKey;

    @Column(name = "RPST_MEMBER_KEY")
    private String rpstMemberKey;

    @Column(name = "USER_TYPE")
    private String userType;

    @Column(name = "KOR_NAME")
    private String korName;

    @Column(name = "ENG_NAME")
    private String engName;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "MOBILE")
    private String mobile;

    @Column(name = "PHONE")
    private String phone;

    @Column(name = "CAMPUS_CODE")
    private String campusCode;

    @Column(name = "CAMPUS_NAME")
    private String campusName;

    @Column(name = "INSTITUTION_CODE")
    private String institutionCode;

    @Column(name = "INSTITUTION_NAME")
    private String institutionName;

    @Column(name = "DEPT_CODE")
    private String deptCode;

    @Column(name = "DEPT_NAME")
    private String deptName;

    @Column(name = "STATE")
    private String state;

    @Column(name = "USE_YN")
    private String useYn;

    @Column(name = "GENDER")
    private String gender;
}
