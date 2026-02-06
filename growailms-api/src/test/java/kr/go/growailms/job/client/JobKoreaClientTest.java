package kr.go.growailms.job.client;

import kr.go.growailms.job.config.JobKoreaProperties;
import kr.go.growailms.job.service.dto.JobRecruitItem;
import kr.go.growailms.job.service.dto.JobRecruitSearchCriteria;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobKoreaClientTest {

    @Test
    void items노드_파싱하고_JK_URL에서_api파라미터를_제거합니다() throws Exception {
        // 왜: 실제 응답에서 공고 노드가 `GI_List`가 아니라 `Items`로 내려오는 케이스가 있어도 파싱되어야 합니다.
        JobKoreaProperties props = new JobKoreaProperties();
        props.setApiKeyParam("api");
        JobKoreaClient client = new JobKoreaClient(props);

        String xml = """
            <?xml version="1.0" encoding="euc-kr"?>
            <DataList>
              <TotalSumCnt>500</TotalSumCnt>
              <TotalCnt>1</TotalCnt>
              <Items>
                <GI_No>23592012</GI_No>
                <C_Name>테스트회사</C_Name>
                <GI_Subject>테스트 공고</GI_Subject>
                <AreaCode>I010</AreaCode>
                <GI_Career>1</GI_Career>
                <GI_W_Date>20260101</GI_W_Date>
                <GI_End_Date>20260131</GI_End_Date>
                <JK_URL>http://www.jobkorea.co.kr/Recruit/GI_Read/23592012?Oem_Code=C900&amp;api=TESTKEY</JK_URL>
              </Items>
            </DataList>
            """;

        Document doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        Method method = JobKoreaClient.class.getDeclaredMethod("parseGiList", Document.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<JobRecruitItem> items = (List<JobRecruitItem>) method.invoke(client, doc);

        assertThat(items).hasSize(1);
        JobRecruitItem item = items.get(0);
        assertThat(item.wantedAuthNo()).isEqualTo("23592012");
        assertThat(item.infoSvc()).isEqualTo("JOBKOREA");
        assertThat(item.wantedInfoUrl()).doesNotContain("api=");
        assertThat(item.wantedInfoUrl()).contains("Oem_Code=C900");
    }

    @Test
    void 중분류_rpcd를_검색할때는_부모_rbcd도_함께_붙입니다() throws Exception {
        // 왜: 잡코리아는 rpcd만 보내면 필터가 안 먹는 케이스가 있어, 부모 rbcd를 같이 보내야 합니다.
        JobKoreaProperties props = new JobKoreaProperties();
        JobKoreaClient client = new JobKoreaClient(props);

        JobRecruitSearchCriteria criteria = new JobRecruitSearchCriteria(
            null,
            "1000038",
            null,
            null,
            null,
            null,
            1,
            10,
            "L"
        );

        Method method = JobKoreaClient.class.getDeclaredMethod("buildRequestUrl", String.class, String.class, String.class, JobRecruitSearchCriteria.class);
        method.setAccessible(true);

        String url = (String) method.invoke(client, "http://example.com", "1354", "C900", criteria);

        assertThat(url).contains("rpcd=1000038");
        assertThat(url).contains("rbcd=10007");
    }
}
