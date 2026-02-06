package kr.go.growailms.statistics.controller;

import kr.go.growailms.statistics.kosis.client.dto.KosisPopulationRow;
import kr.go.growailms.statistics.kosis.service.KosisStatisticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/statistics/kosis")
public class KosisStatisticsController {
    // 왜: KOSIS 통계 API를 `/statistics/kosis/*` 하위로 묶어 다른 통계들과 충돌을 방지합니다.

    private final KosisStatisticsService kosisStatisticsService;

    public KosisStatisticsController(KosisStatisticsService kosisStatisticsService) {
        this.kosisStatisticsService = kosisStatisticsService;
    }

    @GetMapping("/population")
    public ResponseEntity<?> getPopulation(
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "ageType", required = false) String ageType,
            @RequestParam(value = "age_type", required = false) String ageTypeLegacy,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "admCd", required = false) String admCd,
            @RequestParam(value = "adm_cd", required = false) String admCdLegacy
    ) throws IOException {
        // 왜: 레거시 화면(TestPage.jsp)은 `age_type`를 사용하므로 호환 파라미터를 함께 받습니다.
        String resolvedAgeType = (ageType != null) ? ageType : ageTypeLegacy;
        String resolvedAdmCd = (admCd != null) ? admCd : admCdLegacy;

        try {
            List<KosisPopulationRow> rows = kosisStatisticsService.getPopulation(year, resolvedAgeType, gender, resolvedAdmCd);
            return ResponseEntity.ok(rows);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    private record ApiError(String message) {
    }
}
