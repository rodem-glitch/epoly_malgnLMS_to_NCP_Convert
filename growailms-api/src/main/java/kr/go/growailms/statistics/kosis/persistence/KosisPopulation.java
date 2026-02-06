package kr.go.growailms.statistics.kosis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "kosis_population")
public class KosisPopulation {
    // 왜: 외부(KOSIS)에서 받은 인구 통계를 DB에 저장해 "캐시"로 재사용하기 위함입니다.

    @EmbeddedId
    private KosisPopulationId id;

    @Column(name = "adm_nm", nullable = false, length = 200)
    private String admNm;

    @Column(name = "population", nullable = false)
    private long population;

    protected KosisPopulation() {
        // 왜: JPA 프록시/리플렉션을 위한 기본 생성자가 필요합니다.
    }

    public KosisPopulation(KosisPopulationId id, String admNm, long population) {
        this.id = id;
        this.admNm = admNm;
        this.population = population;
    }

    public KosisPopulationId getId() {
        return id;
    }

    public String getAdmNm() {
        return admNm;
    }

    public long getPopulation() {
        return population;
    }
}

