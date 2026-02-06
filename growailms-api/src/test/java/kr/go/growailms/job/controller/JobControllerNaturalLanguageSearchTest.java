package kr.go.growailms.job.controller;

import kr.go.growailms.job.service.JobService;
import kr.go.growailms.job.service.dto.JobRecruitListResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
public class JobControllerNaturalLanguageSearchTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    JobService jobService;

    @Test
    void 자연어검색은_통합에서만_허용되고_필터는_무시된다() throws Exception {
        JobRecruitListResponse stub = new JobRecruitListResponse(0, 1, 10, List.of());
        when(jobService.getRecruitmentsByNaturalLanguageForAll(
            eq("개발자"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(1),
            eq(10),
            any(JobService.CachePolicy.class)
        )).thenReturn(stub);

        mockMvc.perform(get("/job/recruits/nl")
                .param("provider", "ALL")
                .param("q", "개발자")
                // 아래 필터들은 "무시"되어야 합니다.
                .param("region", "11000")
                .param("occupation", "133100")
                .param("salTp", "Y")
                .param("minPay", "3000")
                .param("maxPay", "5000")
                .param("education", "05")
                .param("startPage", "1")
                .param("display", "10")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.wanted").isArray());

        verify(jobService).getRecruitmentsByNaturalLanguageForAll(
            eq("개발자"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(1),
            eq(10),
            any(JobService.CachePolicy.class)
        );
    }

    @Test
    void 자연어검색은_통합이_아니면_거부된다() throws Exception {
        mockMvc.perform(get("/job/recruits/nl")
                .param("provider", "WORK24")
                .param("q", "개발자")
                .param("startPage", "1")
                .param("display", "10")
            )
            .andExpect(status().isBadRequest());
    }
}

