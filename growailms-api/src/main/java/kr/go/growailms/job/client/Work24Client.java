package kr.go.growailms.job.client;

import kr.go.growailms.job.config.Work24Properties;
import kr.go.growailms.job.service.dto.JobRecruitItem;
import kr.go.growailms.job.service.dto.JobRecruitListResponse;
import kr.go.growailms.job.service.dto.JobRecruitSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class Work24Client {
    // 왜: Work24 OpenAPI(XML)를 직접 호출하고 응답을 공통 포맷으로 변환합니다.

    private final Work24Properties properties;

    public Work24Client(Work24Properties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public JobRecruitListResponse fetchRecruitList(JobRecruitSearchCriteria criteria) {
        String apiUrl = properties.getApiUrl();
        String authKey = properties.getAuthKey();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("Work24 API URL이 비어 있습니다.");
        }
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("Work24 인증키가 비어 있습니다.");
        }

        String requestUrl = buildRequestUrl(apiUrl, authKey, criteria);
        Document response = requestXml(requestUrl);

        int total = parseInt(getFirstText(response, "total"), 0);
        int startPage = parseInt(getFirstText(response, "startPage"), criteria.startPage());
        int display = parseInt(getFirstText(response, "display"), criteria.display());

        List<JobRecruitItem> items = parseWantedItems(response);
        return new JobRecruitListResponse(total, startPage, display, items);
    }

    private String buildRequestUrl(String apiUrl, String authKey, JobRecruitSearchCriteria criteria) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl)
            .queryParam("authKey", authKey)
            .queryParam("callTp", criteria.callType())
            .queryParam("returnType", "XML")
            .queryParam("startPage", criteria.startPage())
            .queryParam("display", criteria.display());

        if (criteria.region() != null && !criteria.region().isBlank()) {
            builder.queryParam("region", criteria.region().trim());
        }
        if (criteria.occupation() != null && !criteria.occupation().isBlank()) {
            builder.queryParam("occupation", criteria.occupation().trim());
        }
        if (criteria.salTp() != null && !criteria.salTp().isBlank()) {
            builder.queryParam("salTp", criteria.salTp().trim());
        }
        if (criteria.minPay() != null) {
            builder.queryParam("minPay", criteria.minPay());
        }
        if (criteria.maxPay() != null) {
            builder.queryParam("maxPay", criteria.maxPay());
        }
        if (criteria.education() != null && !criteria.education().isBlank()) {
            builder.queryParam("education", criteria.education().trim());
        }

        return builder.build(true).toUriString();
    }

    private Document requestXml(String requestUrl) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(requestUrl);
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception e) {
            throw new IllegalStateException("Work24 API 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private List<JobRecruitItem> parseWantedItems(Document document) {
        List<JobRecruitItem> items = new ArrayList<>();
        if (document == null) return items;

        NodeList wantedList = document.getElementsByTagName("wanted");
        if (wantedList == null || wantedList.getLength() == 0) return items;

        for (int i = 0; i < wantedList.getLength(); i++) {
            NodeList childNodes = wantedList.item(i).getChildNodes();

            String wantedAuthNo = null;
            String company = null;
            String busino = null;
            String indTpNm = null;
            String title = null;
            String salTpNm = null;
            String sal = null;
            String minSal = null;
            String maxSal = null;
            String region = null;
            String holidayTpNm = null;
            String minEdubg = null;
            String career = null;
            String regDt = null;
            String closeDt = null;
            String infoSvc = null;
            String wantedInfoUrl = null;
            String wantedMobileInfoUrl = null;
            String smodifyDtm = null;
            String zipCd = null;
            String strtnmCd = null;
            String basicAddr = null;
            String detailAddr = null;
            String empTpCd = null;
            String jobsCd = null;

            for (int j = 0; j < childNodes.getLength(); j++) {
                String nodeName = childNodes.item(j).getNodeName();
                String text = childNodes.item(j).getTextContent();

                switch (nodeName) {
                    case "wantedAuthNo" -> wantedAuthNo = text;
                    case "company" -> company = text;
                    case "busino" -> busino = text;
                    case "indTpNm" -> indTpNm = text;
                    case "title" -> title = text;
                    case "salTpNm" -> salTpNm = text;
                    case "sal" -> sal = text;
                    case "minSal" -> minSal = text;
                    case "maxSal" -> maxSal = text;
                    case "region" -> region = text;
                    case "holidayTpNm" -> holidayTpNm = text;
                    case "minEdubg" -> minEdubg = text;
                    case "career" -> career = text;
                    case "regDt" -> regDt = text;
                    case "closeDt" -> closeDt = text;
                    case "infoSvc" -> infoSvc = text;
                    case "wantedInfoUrl" -> wantedInfoUrl = text;
                    case "wantedMobileInfoUrl" -> wantedMobileInfoUrl = text;
                    case "smodifyDtm" -> smodifyDtm = text;
                    case "zipCd" -> zipCd = text;
                    case "strtnmCd" -> strtnmCd = text;
                    case "basicAddr" -> basicAddr = text;
                    case "detailAddr" -> detailAddr = text;
                    case "empTpCd" -> empTpCd = text;
                    case "jobsCd" -> jobsCd = text;
                    default -> {
                        // 사용하지 않는 필드는 무시합니다.
                    }
                }
            }

            // 왜: Work24 응답의 infoSvc 값이 `VALIDATION`처럼 제공처가 아닌 다른 값으로 내려오는 경우가 있어,
            // 통합(ALL) 화면에서 제공처 표시에 혼선이 생깁니다. 이 프로젝트에서는 제공처를 WORK24로 고정해 사용합니다.
            String safeInfoSvc = "WORK24";

            items.add(new JobRecruitItem(
                wantedAuthNo,
                company,
                busino,
                indTpNm,
                title,
                salTpNm,
                sal,
                minSal,
                maxSal,
                region,
                holidayTpNm,
                minEdubg,
                career,
                regDt,
                closeDt,
                safeInfoSvc,
                wantedInfoUrl,
                wantedMobileInfoUrl,
                smodifyDtm,
                zipCd,
                strtnmCd,
                basicAddr,
                detailAddr,
                empTpCd,
                jobsCd
            ));
        }

        return items;
    }

    private String getFirstText(Document document, String tagName) {
        if (document == null || tagName == null) return null;
        NodeList list = document.getElementsByTagName(tagName);
        if (list == null || list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    private int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
