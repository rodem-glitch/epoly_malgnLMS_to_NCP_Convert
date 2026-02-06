package kr.go.growailms.job.service;

import kr.go.growailms.job.service.dto.JobRecruitItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JobRecruitInterleaveUtilTest {

    @Test
    void 통합목록이_Work24_뒤에_JobKorea가_붙어도_앞쪽에_섞이도록_교차된다() {
        // 왜: 자연어 검색은 display만큼만 잘라 노출하므로,
        //      [Work24..., JobKorea...] 구조면 초반이 Work24로만 채워져 "잡코리아가 안 나오는 것처럼" 보일 수 있습니다.
        //      이 테스트는 교차(interleave) 로직이 초반에도 JOBKOREA가 나오게 만드는지 확인합니다.

        List<JobRecruitItem> input = new ArrayList<>();
        for (int i = 0; i < 10; i++) input.add(dummy("WORK24", "W" + i));
        for (int i = 0; i < 10; i++) input.add(dummy("JOBKOREA", "J" + i));

        List<JobRecruitItem> out = JobRecruitInterleaveUtil.interleaveProvidersForAll(input);

        assertNotNull(out);
        assertEquals(input.size(), out.size());

        long jobkoreaInHead = out.stream().limit(10)
            .filter(it -> it != null && it.infoSvc() != null && "JOBKOREA".equalsIgnoreCase(it.infoSvc()))
            .count();
        assertTrue(jobkoreaInHead >= 1, "앞쪽 결과에 JOBKOREA가 섞여야 합니다.");
    }

    private JobRecruitItem dummy(String infoSvc, String id) {
        return new JobRecruitItem(
            id,          // wantedAuthNo
            "회사" + id,   // company
            "",          // busino
            "",          // indTpNm
            "제목" + id,   // title
            "",          // salTpNm
            "",          // sal
            "",          // minSal
            "",          // maxSal
            "",          // region
            "",          // holidayTpNm
            "",          // minEdubg
            "",          // career
            "",          // regDt
            "",          // closeDt
            infoSvc,     // infoSvc
            "",          // wantedInfoUrl
            "",          // wantedMobileInfoUrl
            "",          // smodifyDtm
            "",          // zipCd
            "",          // strtnmCd
            "",          // basicAddr
            "",          // detailAddr
            "",          // empTpCd
            ""           // jobsCd
        );
    }
}

