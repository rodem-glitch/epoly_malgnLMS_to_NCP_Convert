package kr.polytech.lms.statistics.sgis.service;

import kr.polytech.lms.statistics.sgis.client.SgisClient;
import kr.polytech.lms.statistics.sgis.persistence.SgisCompany;
import kr.polytech.lms.statistics.sgis.persistence.SgisCompanyId;
import kr.polytech.lms.statistics.sgis.persistence.SgisCompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Optional;

@Service
public class SgisCompanyCacheService {
    // 왜: SGIS API는 네트워크/토큰/제공연도 이슈가 있으므로,
    //     "DB 캐시 우선 → 없으면 호출 → 저장" 패턴으로 안정성을 확보합니다.

    private final SgisClient sgisClient;
    private final SgisCompanyRepository sgisCompanyRepository;

    public SgisCompanyCacheService(
            SgisClient sgisClient,
            SgisCompanyRepository sgisCompanyRepository
    ) {
        this.sgisClient = sgisClient;
        this.sgisCompanyRepository = sgisCompanyRepository;
    }

    public SgisClient.CompanyStats getCompanyStats(String year, String admCd, String classCode) throws IOException {
        String resolvedYear = normalize(year);
        String resolvedAdmCd = normalize(admCd);
        String resolvedClassCode = normalize(classCode);

        if (!StringUtils.hasText(resolvedYear) || !StringUtils.hasText(resolvedAdmCd) || !StringUtils.hasText(resolvedClassCode)) {
            throw new IllegalArgumentException("year/admCd/classCode는 필수입니다.");
        }

        SgisCompanyId id = new SgisCompanyId(resolvedYear, resolvedAdmCd, resolvedClassCode);
        Optional<SgisCompany> cached = sgisCompanyRepository.findById(id);
        if (cached.isPresent()) {
            SgisCompany entity = cached.get();

            boolean nationwide = "00".equals(resolvedAdmCd);
            boolean sido = resolvedAdmCd.matches("\\d{2}");
            if (entity.getTotWorker() != null || entity.getCorpCnt() != null) {
                // 왜: 과거에는 corp_cnt만 저장했던 이력(마이그레이션)이 있을 수 있어,
                //     tot_worker가 비어있으면 한 번 더 호출해서 채웁니다.
                boolean suspiciousZeroForWideArea =
                        (nationwide || sido)
                                && entity.getTotWorker() != null
                                && entity.getTotWorker() == 0L
                                && (entity.getCorpCnt() == null || entity.getCorpCnt() == 0L);
                if (entity.getTotWorker() == null || suspiciousZeroForWideArea) {
                    // 왜: 시도/전국 코드에서 0,0 캐시가 남아 있으면 실제 값이 열려도 계속 0으로 고착될 수 있어,
                    //     1회 재조회로 캐시를 갱신합니다.
                    SgisClient.CompanyStats refreshed = sgisClient.fetchCompanyStats(resolvedYear, resolvedAdmCd, resolvedClassCode);
                    sgisCompanyRepository.save(new SgisCompany(id, refreshed.corpCnt(), refreshed.totWorker()));
                    return refreshed;
                }
                return new SgisClient.CompanyStats(entity.getCorpCnt(), entity.getTotWorker());
            }

            // 왜: 둘 다 NULL이면(=N/A 등) 다시 호출해도 동일할 가능성이 높아서 negative cache로 취급합니다.
            //     단, 전국(adm_cd=00)은 조회 방식(low_search)이 바뀌면 값이 생길 수 있어 1회 재조회합니다.
            if (nationwide || sido) {
                SgisClient.CompanyStats refreshed = sgisClient.fetchCompanyStats(resolvedYear, resolvedAdmCd, resolvedClassCode);
                sgisCompanyRepository.save(new SgisCompany(id, refreshed.corpCnt(), refreshed.totWorker()));
                return refreshed;
            }

            return new SgisClient.CompanyStats(null, null);
        }

        SgisClient.CompanyStats fetched = sgisClient.fetchCompanyStats(resolvedYear, resolvedAdmCd, resolvedClassCode);
        sgisCompanyRepository.save(new SgisCompany(id, fetched.corpCnt(), fetched.totWorker()));
        return fetched;
    }

    public SgisClient.CompanyStats getCompanyStatsNationwideList(String year, String classCode) throws IOException {
        String resolvedYear = normalize(year);
        String resolvedClassCode = normalize(classCode);
        if (!StringUtils.hasText(resolvedYear) || !StringUtils.hasText(resolvedClassCode)) {
            throw new IllegalArgumentException("year/classCode는 필수입니다.");
        }

        // 왜: 사업체통계 API 문서 기준으로 adm_cd 미전달(non) 시 "전국 시도 리스트"가 내려오므로,
        //     전국(00) 집계가 비는 경우에는 이 방식으로 전체 합계를 직접 구합니다.
        return sgisClient.fetchCompanyStats(resolvedYear, null, resolvedClassCode);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
