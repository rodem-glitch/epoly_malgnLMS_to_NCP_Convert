package kr.go.growailms.job.client;

import kr.go.growailms.job.config.JobKoreaProperties;
import kr.go.growailms.job.service.dto.JobRecruitItem;
import kr.go.growailms.job.service.dto.JobRecruitListResponse;
import kr.go.growailms.job.service.dto.JobRecruitSearchCriteria;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class JobKoreaClient {
    // 왜: 잡코리아 Open API(XML)를 호출해 공통 포맷으로 변환합니다.

    private final JobKoreaProperties properties;

    public JobKoreaClient(JobKoreaProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public JobRecruitListResponse fetchRecruitList(JobRecruitSearchCriteria criteria) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("잡코리아 연동이 비활성화되어 있습니다.");
        }

        String apiUrl = properties.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("잡코리아 API URL이 비어 있습니다.");
        }

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("잡코리아 API 키(api)가 비어 있습니다.");
        }

        String requestUrl = buildRequestUrl(apiUrl, apiKey, properties.getOemCode(), criteria);
        Document response = requestXml(requestUrl);

        // 왜: TotalSumCnt는 “검색 결과 총 건수(최대 500)”이고, TotalCnt는 “현재 페이지 건수”라서 우선순위를 둡니다.
        int total = parseInt(getFirstText(response, "TotalSumCnt"), -1);
        if (total < 0) total = parseInt(getFirstText(response, "TotalCnt"), 0);
        int startPage = criteria.startPage();
        int display = criteria.display();

        List<JobRecruitItem> items = parseGiList(response);
        return new JobRecruitListResponse(total, startPage, display, items);
    }

    private String buildRequestUrl(String apiUrl, String apiKey, String oemCode, JobRecruitSearchCriteria criteria) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl);
        appendQueryParam(builder, properties.getApiKeyParam(), apiKey);
        // 왜: Oem_Code는 계약/설정에 따라 필수일 수도 있지만, 로컬 개발에서 “API 키만으로도 조회”가 되는 경우가 있어 선택값으로 둡니다.
        if (oemCode != null && !oemCode.isBlank()) {
            appendQueryParam(builder, properties.getOemCodeParam(), oemCode);
        }

        if (properties.getPageParam() != null && !properties.getPageParam().isBlank()) {
            builder.queryParam(properties.getPageParam(), criteria.startPage());
        }
        if (properties.getDisplayParam() != null && !properties.getDisplayParam().isBlank()) {
            builder.queryParam(properties.getDisplayParam(), criteria.display());
        }
        if (criteria.region() != null && properties.getRegionParam() != null && !properties.getRegionParam().isBlank()) {
            String region = criteria.region().trim();
            // 왜: 현재 화면은 Work24 지역코드(숫자)를 보내므로, 잡코리아(알파벳+숫자) 코드가 아닐 때는 필터를 적용하지 않습니다.
            if (looksLikeJobKoreaAreaCode(region)) {
                builder.queryParam(properties.getRegionParam(), region);
            }
        }
        if (criteria.occupation() != null) {
            String occupation = criteria.occupation().trim();
            // 왜: 잡코리아 업직종은 대분류(rbcd=10001 등)와 소분류(rpcd=1000001 등)로 나뉘어 파라미터가 다릅니다.
            if (looksLikeJobKoreaOccupationCode(occupation)) {
                if (occupation.length() == 5) {
                    appendQueryParam(builder, properties.getIndustryParam(), occupation);
                } else {
                    appendQueryParam(builder, properties.getOccupationParam(), occupation);
                    // 왜: 잡코리아는 rpcd만 보내면 필터가 안 먹는 케이스가 있어, 부모 rbcd를 같이 보내줍니다.
                    String parentRbcd = kr.go.growailms.job.code.JobKoreaCodeCatalog.resolveParentRbcdByRpcd(occupation);
                    appendQueryParam(builder, properties.getIndustryParam(), parentRbcd);
                }
            }
        }

        if (criteria.salTp() != null) {
            Integer payCode = mapPayCode(criteria.salTp());
            if (payCode != null) {
                appendQueryParam(builder, properties.getPayParam(), String.valueOf(payCode));
                if (criteria.minPay() != null && criteria.maxPay() != null) {
                    appendQueryParam(builder, properties.getPayTermParam(), criteria.minPay() + "," + criteria.maxPay());
                }
            }
        }

        if (criteria.education() != null) {
            Integer edu1 = mapEdu1Code(criteria.education());
            if (edu1 != null) {
                appendQueryParam(builder, properties.getEdu1Param(), String.valueOf(edu1));
                // 왜: 화면 요구사항상 “학력무관” 공고도 항상 포함돼야 하므로, 잡코리아의 edu3(학력무관 포함) 옵션을 켭니다.
                appendQueryParam(builder, properties.getEdu3Param(), "1");
            }
        }

        for (Map.Entry<String, String> entry : properties.getParams().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            builder.queryParam(entry.getKey(), value);
        }

        return builder.build(true).toUriString();
    }

    private static void appendQueryParam(UriComponentsBuilder builder, String paramName, String value) {
        if (builder == null) return;
        if (paramName == null || paramName.isBlank()) return;
        if (value == null || value.isBlank()) return;
        builder.queryParam(paramName, value);
    }

    private Document requestXml(String requestUrl) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(requestUrl);
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception e) {
            throw new IllegalStateException("잡코리아 API 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private List<JobRecruitItem> parseGiList(Document document) {
        List<JobRecruitItem> items = new ArrayList<>();
        if (document == null) return items;

        NodeList giList = document.getElementsByTagName("GI_List");
        if (giList == null || giList.getLength() == 0) {
            // 왜: 가이드/환경에 따라 공고 노드명이 `GI_List`가 아니라 `Items`로 내려오는 경우가 있습니다.
            giList = document.getElementsByTagName("Items");
        }
        if (giList == null || giList.getLength() == 0) return items;

        for (int i = 0; i < giList.getLength(); i++) {
            NodeList childNodes = giList.item(i).getChildNodes();

            String giNo = null;
            String giSubject = null;
            String company = null;
            String areaCode = null;
            String jobCode = null;
            String giCareer = null;
            String giWDate = null;
            String giEndDate = null;
            String jkUrl = null;
            String empType = null;
            String giPay = null;
            String giPayTerm = null;
            String giEduCutLine = null;
            String giJobType = null;

            for (int j = 0; j < childNodes.getLength(); j++) {
                Node node = childNodes.item(j);
                String nodeName = node.getNodeName();
                String text = node.getTextContent();

                switch (nodeName) {
                    case "GI_No" -> giNo = text;
                    case "GI_Subject" -> giSubject = text;
                    case "C_Name" -> company = text;
                    case "AreaCode" -> areaCode = text;
                    case "Job_Ctgr_Code" -> jobCode = text;
                    case "GI_Career" -> giCareer = text;
                    case "GI_W_Date" -> giWDate = text;
                    case "GI_End_Date" -> giEndDate = text;
                    case "JK_URL" -> jkUrl = text;
                    case "Emp_Type" -> empType = text;
                    case "GI_Pay" -> giPay = text;
                    case "GI_Pay_Term" -> giPayTerm = text;
                    case "GI_EDU_CutLine" -> giEduCutLine = text;
                    case "GI_Job_Type" -> giJobType = text;
                    default -> {
                        // 사용하지 않는 필드는 무시합니다.
                    }
                }
            }

            if (giNo == null || giNo.isBlank()) {
                // 왜: `Items`가 “전체를 감싸는 루트”로 올 수도 있어, 공고키가 없는 노드는 제외합니다.
                continue;
            }

            // 왜: JK_URL에 api 키가 포함되어 내려오면 브라우저로 그대로 노출될 수 있어 제거합니다.
            String safeJkUrl = stripQueryParam(jkUrl, properties.getApiKeyParam());

            SalaryInfo salaryInfo = SalaryInfo.from(giPay, giPayTerm);
            String regionLabel = kr.go.growailms.job.code.JobKoreaCodeCatalog.resolveAreaDisplayName(areaCode);
            String careerLabel = mapCareerLabel(giCareer);
            String eduLabel = mapEducationLabel(giEduCutLine);
            String jobTypeLabel = mapJobTypeLabel(giJobType, empType);
            String regDate = normalizeDate(giWDate);
            String closeDate = normalizeDate(giEndDate);

            items.add(new JobRecruitItem(
                giNo,
                company,
                null,
                null,
                giSubject,
                salaryInfo.salTpNm(),
                salaryInfo.sal(),
                salaryInfo.minSal(),
                salaryInfo.maxSal(),
                regionLabel,
                jobTypeLabel,
                eduLabel,
                careerLabel,
                regDate,
                closeDate,
                "JOBKOREA",
                safeJkUrl,
                safeJkUrl,
                null,
                null,
                null,
                null,
                null,
                giJobType,
                jobCode
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

    private static boolean looksLikeJobKoreaAreaCode(String raw) {
        if (raw == null) return false;
        String value = raw.trim().toUpperCase();
        if (value.isBlank()) return false;
        // 예: I010, B180, 0 (전체), 1000(세종)
        return value.matches("^(0|[A-Z][0-9]{3}|[0-9]{4})(,(0|[A-Z][0-9]{3}|[0-9]{4}))*$");
    }

    private static boolean looksLikeJobKoreaOccupationCode(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        if (value.isBlank()) return false;
        // 왜: 잡코리아 업직종 코드는 대체로 100xx(대분류) 또는 1000xxx(소분류) 형태입니다.
        return value.matches("^100\\d{2,4}$");
    }

    private static Integer mapPayCode(String salTp) {
        if (salTp == null || salTp.isBlank()) return null;
        String v = salTp.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "Y" -> 1; // 연봉(만원)
            case "M" -> 2; // 월급(만원)
            case "D" -> 4; // 일급(원)
            case "H" -> 5; // 시급(원)
            default -> null;
        };
    }

    private static Integer mapEdu1Code(String education) {
        if (education == null || education.isBlank()) return null;
        String v = education.trim();
        // UI 값(01~07)을 잡코리아 edu1(1~7)로 매핑합니다.
        if (!v.matches("^\\d{2}$")) return null;
        if ("00".equals(v)) return 0;
        int n;
        try {
            n = Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
        if (n < 1 || n > 7) return null;
        return n;
    }

    private static String normalizeDate(String raw) {
        if (raw == null) return "";
        String v = raw.trim();
        if (v.isBlank()) return "";
        // YYYYMMDD → YYYY-MM-DD
        if (v.matches("^\\d{8}$")) {
            return v.substring(0, 4) + "-" + v.substring(4, 6) + "-" + v.substring(6, 8);
        }
        // YYYY.MM.DD → YYYY-MM-DD
        v = v.replace('.', '-');
        return v;
    }

    private static String mapCareerLabel(String raw) {
        if (raw == null || raw.isBlank()) return "경력무관";
        String v = raw.trim();
        return switch (v) {
            case "1" -> "신입";
            case "2" -> "경력";
            case "3" -> "신입/경력";
            case "4" -> "경력무관";
            default -> "경력무관";
        };
    }

    private static String mapEducationLabel(String raw) {
        if (raw == null || raw.isBlank()) return "학력무관";
        String v = raw.trim();
        return switch (v) {
            case "0" -> "학력무관";
            case "1" -> "초졸";
            case "2" -> "중졸";
            case "3" -> "고졸";
            case "4" -> "대졸(2~3년)";
            case "5" -> "대졸(4년)";
            case "6" -> "석사";
            case "7" -> "박사";
            default -> "학력무관";
        };
    }

    private static String mapJobTypeLabel(String giJobType, String empTypeLegacy) {
        String raw = (giJobType == null || giJobType.isBlank()) ? (empTypeLegacy == null ? "" : empTypeLegacy.trim()) : giJobType.trim();
        if (raw.isBlank()) return "";
        // 왜: 복수 고용형태가 올 수 있지만, 카드 뱃지는 1개만 보여주므로 첫 번째만 표시합니다.
        String first = raw.contains(",") ? raw.substring(0, raw.indexOf(',')) : raw;
        first = first.trim();
        return switch (first) {
            case "1" -> "정규직";
            case "2" -> "계약직";
            case "3" -> "인턴";
            case "4" -> "파견직";
            case "5" -> "도급";
            case "6" -> "프리랜서";
            case "7" -> "아르바이트";
            case "8" -> "연수생/교육생";
            case "9" -> "병역특례";
            case "10" -> "위촉직/개인사업자";
            default -> "기타";
        };
    }

    private static String stripQueryParam(String url, String paramName) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isBlank()) return trimmed;
        if (paramName == null || paramName.isBlank()) return trimmed;

        try {
            URI uri = new URI(trimmed);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) return trimmed;

            List<String> kept = new ArrayList<>();
            for (String part : query.split("&")) {
                if (part == null || part.isBlank()) continue;
                int idx = part.indexOf('=');
                String name = (idx >= 0) ? part.substring(0, idx) : part;
                if (name.equalsIgnoreCase(paramName)) continue;
                kept.add(part);
            }

            String newQuery = kept.isEmpty() ? null : String.join("&", kept);
            URI rebuilt = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment());
            return rebuilt.toString();
        } catch (Exception ignored) {
            // 왜: URL 형식이 깨져 있더라도(공백/줄바꿈 등) 화면이 죽지 않게 정규식으로 한 번 더 처리합니다.
        }

        String pattern = "(?i)([?&])" + Pattern.quote(paramName) + "=[^&#]*(&)?";
        String result = trimmed.replaceAll(pattern, "$1");
        result = result.replaceAll("\\?&", "?");
        result = result.replaceAll("[?&]$", "");
        return result;
    }

    private record SalaryInfo(String salTpNm, String sal, String minSal, String maxSal) {
        static SalaryInfo from(String giPayRaw, String giPayTermRaw) {
            String giPay = giPayRaw == null ? "" : giPayRaw.trim();
            String giPayTerm = giPayTermRaw == null ? "" : giPayTermRaw.trim();

            int pay;
            try {
                pay = Integer.parseInt(giPay);
            } catch (Exception ignored) {
                pay = -1;
            }

            String[] parts = giPayTerm.split(",");
            Integer min = null;
            Integer max = null;
            if (parts.length >= 2) {
                min = parseInt(parts[0]);
                max = parseInt(parts[1]);
            }

            String salTpNm = switch (pay) {
                case 1 -> "연봉";
                case 2 -> "월급";
                case 3 -> "주급";
                case 4 -> "일급";
                case 5 -> "시급";
                case 6 -> "건별";
                default -> "";
            };

            String unit = (pay == 1 || pay == 2) ? "만원" : "원";

            String sal;
            if (pay == 0) {
                salTpNm = "내규";
                sal = "회사 내규에 따름";
            } else if (min == null || max == null || (min == 0 && max == 0)) {
                sal = "-";
            } else if (min.equals(max)) {
                sal = formatNumber(min) + unit + " ~ " + formatNumber(max) + unit;
            } else {
                sal = formatNumber(min) + unit + " ~ " + formatNumber(max) + unit;
            }

            return new SalaryInfo(salTpNm, sal, min == null ? "" : String.valueOf(min), max == null ? "" : String.valueOf(max));
        }

        private static Integer parseInt(String raw) {
            if (raw == null) return null;
            String v = raw.trim();
            if (v.isBlank()) return null;
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static String formatNumber(Integer value) {
            if (value == null) return "";
            return String.format(Locale.ROOT, "%,d", value);
        }
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
