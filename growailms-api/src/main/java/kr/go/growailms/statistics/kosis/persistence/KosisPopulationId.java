package kr.go.growailms.statistics.kosis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class KosisPopulationId implements Serializable {
    // 왜: KOSIS 인구 통계는 (year, age_type, gender, adm_cd) 조합으로 한 행이 유일해질 수 있어 복합키로 둡니다.

    @Column(name = "\"year\"", nullable = false, length = 4)
    private String year;

    @Column(name = "age_type", nullable = false, length = 8)
    private String ageType;

    @Column(name = "gender", nullable = false, length = 2)
    private String gender;

    @Column(name = "adm_cd", nullable = false, length = 20)
    private String admCd;

    protected KosisPopulationId() {
        // 왜: JPA 프록시/리플렉션을 위한 기본 생성자가 필요합니다.
    }

    public KosisPopulationId(String year, String ageType, String gender, String admCd) {
        this.year = year;
        this.ageType = ageType;
        this.gender = gender;
        this.admCd = admCd;
    }

    public String getYear() {
        return year;
    }

    public String getAgeType() {
        return ageType;
    }

    public String getGender() {
        return gender;
    }

    public String getAdmCd() {
        return admCd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KosisPopulationId that)) return false;
        return Objects.equals(year, that.year)
                && Objects.equals(ageType, that.ageType)
                && Objects.equals(gender, that.gender)
                && Objects.equals(admCd, that.admCd);
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, ageType, gender, admCd);
    }
}

