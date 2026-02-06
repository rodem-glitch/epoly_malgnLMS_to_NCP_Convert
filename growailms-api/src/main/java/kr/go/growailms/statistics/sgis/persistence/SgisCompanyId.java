package kr.go.growailms.statistics.sgis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class SgisCompanyId implements Serializable {
    // 왜: (연도, 행정구역코드, 산업코드) 조합이 동일하면 동일한 통계(사업체 수)로 취급합니다.

    @Column(name = "\"year\"", length = 4, nullable = false)
    private String year;

    @Column(name = "adm_cd", length = 20, nullable = false)
    private String admCd;

    @Column(name = "class_code", length = 20, nullable = false)
    private String classCode;

    protected SgisCompanyId() {
    }

    public SgisCompanyId(String year, String admCd, String classCode) {
        this.year = year;
        this.admCd = admCd;
        this.classCode = classCode;
    }

    public String getYear() {
        return year;
    }

    public String getAdmCd() {
        return admCd;
    }

    public String getClassCode() {
        return classCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SgisCompanyId that)) return false;
        return Objects.equals(year, that.year)
                && Objects.equals(admCd, that.admCd)
                && Objects.equals(classCode, that.classCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, admCd, classCode);
    }
}

