package kr.go.growailms.statistics.kosis.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KosisPopulationRow {
    // 왜: KOSIS 응답 JSON 필드명(adm_cd/adm_nm)을 자바 필드로 매핑합니다.

    @JsonProperty("adm_cd")
    private String admCd;

    @JsonProperty("adm_nm")
    private String admNm;

    @JsonProperty("population")
    private long population;

    public String getAdmCd() {
        return admCd;
    }

    public void setAdmCd(String admCd) {
        this.admCd = admCd;
    }

    public String getAdmNm() {
        return admNm;
    }

    public void setAdmNm(String admNm) {
        this.admNm = admNm;
    }

    public long getPopulation() {
        return population;
    }

    public void setPopulation(long population) {
        this.population = population;
    }
}
