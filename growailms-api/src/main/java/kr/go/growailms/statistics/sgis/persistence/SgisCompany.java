package kr.go.growailms.statistics.sgis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "sgis_company")
public class SgisCompany {
    // 왜: 외부(SGIS) API 결과를 DB에 저장해 "매번 호출"을 피하고, 화면 로딩을 빠르게 합니다.

    @EmbeddedId
    private SgisCompanyId id;

    @Column(name = "corp_cnt")
    private Long corpCnt;

    @Column(name = "tot_worker")
    private Long totWorker;

    protected SgisCompany() {
    }

    public SgisCompany(SgisCompanyId id, Long corpCnt, Long totWorker) {
        this.id = id;
        this.corpCnt = corpCnt;
        this.totWorker = totWorker;
    }

    public SgisCompanyId getId() {
        return id;
    }

    public Long getCorpCnt() {
        return corpCnt;
    }

    public Long getTotWorker() {
        return totWorker;
    }
}
