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
            if (entity.getTotWorker() != null || entity.getCorpCnt() != null) {
                // 왜: 과거에는 corp_cnt만 저장했던 이력(마이그레이션)이 있을 수 있어,
                //     tot_worker가 비어있으면 한 번 더 호출해서 채웁니다.
                if (entity.getTotWorker() == null) {
                    SgisClient.CompanyStats refreshed = sgisClient.fetchCompanyStats(resolvedYear, resolvedAdmCd, resolvedClassCode);
                    sgisCompanyRepository.save(new SgisCompany(id, refreshed.corpCnt(), refreshed.totWorker()));
                    return refreshed;
                }
                return new SgisClient.CompanyStats(entity.getCorpCnt(), entity.getTotWorker());
            }

            // 왜: 둘 다 NULL이면(=N/A 등) 다시 호출해도 동일할 가능성이 높아서 negative cache로 취급합니다.
            //     단, 전국(adm_cd=00)은 조회 방식(low_search)이 바뀌면 값이 생길 수 있어 1회 재조회합니다.
            if (nationwide) {
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

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
