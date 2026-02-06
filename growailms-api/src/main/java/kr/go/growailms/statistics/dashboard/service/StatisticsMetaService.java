package kr.go.growailms.statistics.dashboard.service;

import kr.go.growailms.statistics.mapping.MajorIndustryMappingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatisticsMetaService {
    // 왜: 통계 화면의 드롭다운(캠퍼스 등)은 화면 하드코딩 대신 API로 내려주면 유지보수가 쉬워집니다.
    //     현재 통계 기준은 "엑셀/통계청 API"이므로, 내부 DB 학기(OPEN_TERM) 같은 값은 여기서 제공하지 않습니다.

    private final MajorIndustryMappingService majorIndustryMappingService;

    public StatisticsMetaService(
            MajorIndustryMappingService majorIndustryMappingService
    ) {
        this.majorIndustryMappingService = majorIndustryMappingService;
    }

    public List<MajorIndustryMappingService.CampusGroup> getCampusGroups() {
        return majorIndustryMappingService.getCampusGroups();
    }
}
