package kr.go.growailms.job.client;

import kr.go.growailms.job.config.Work24Properties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class Work24CodeClient {
    // 왜: Work24 공통코드(지역/직종)를 받아 DB에 적재할 수 있게 분리합니다.

    private static final String[] REGION_CODE_TAGS = {"regionCd", "regionCode", "code"};
    private static final String[] REGION_NAME_TAGS = {"regionNm", "regionName", "name"};
    private static final String[] JOB_CODE_TAGS = {"jobsCd", "jobCd", "occupationCd", "occuCd", "ocptCd", "code"};
    private static final String[] JOB_NAME_TAGS = {"jobsNm", "jobNm", "occupationNm", "occuNm", "name"};

    private final Work24Properties properties;

    public Work24CodeClient(Work24Properties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public List<RegionCodeItem> fetchRegionCodes() {
        Document document = requestCommonCodes("1");
        ensureNoError(document);
        return parseRegionCodes(document);
    }

    public List<OccupationCodeItem> fetchOccupationCodes() {
        Document document = requestCommonCodes("2");
        ensureNoError(document);
        return parseOccupationCodes(document);
    }

    private Document requestCommonCodes(String dtlGb) {
        String apiUrl = properties.getCodeApiUrl();
        String authKey = properties.getAuthKey();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("Work24 코드 API URL이 비어 있습니다.");
        }
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("Work24 인증키가 비어 있습니다.");
        }

        String requestUrl = UriComponentsBuilder.fromHttpUrl(apiUrl)
            .queryParam("returnType", "XML")
            .queryParam("target", "CMCD")
            .queryParam("authKey", authKey)
            .queryParam("dtlGb", dtlGb)
            .build(true)
            .toUriString();

        return requestXml(requestUrl);
    }

    private Document requestXml(String requestUrl) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(requestUrl);
            document.getDocumentElement().normalize();
            return document;
        } catch (Exception e) {
            throw new IllegalStateException("Work24 코드 API 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private void ensureNoError(Document document) {
        if (document == null || document.getDocumentElement() == null) return;
        NodeList errors = document.getElementsByTagName("error");
        if (errors == null || errors.getLength() == 0) return;
        String message = errors.item(0).getTextContent();
        if (message != null && !message.isBlank()) {
            throw new IllegalStateException("Work24 코드 API 오류: " + message.trim());
        }
    }

    private List<RegionCodeItem> parseRegionCodes(Document document) {
        List<RegionCodeItem> items = new ArrayList<>();
        if (document == null) return items;

        NodeList rootList = getRootNodes(document, "cmcdRegion", "cmcdRegionCode");
        if (rootList == null || rootList.getLength() == 0) return items;

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rootList.getLength(); i++) {
            Node root = rootList.item(i);
            NodeList oneDepthList = getChildNodes(root, "oneDepth");
            for (int j = 0; j < oneDepthList.getLength(); j++) {
                Node oneDepth = oneDepthList.item(j);
                String depth1Name = getFirstChildText(oneDepth, REGION_NAME_TAGS);
                String depth1Code = getFirstChildText(oneDepth, REGION_CODE_TAGS);
                addRegionItem(items, seen, depth1Code, depth1Name, null, null);

                NodeList twoDepthList = getChildNodes(oneDepth, "twoDepth");
                for (int k = 0; k < twoDepthList.getLength(); k++) {
                    Node twoDepth = twoDepthList.item(k);
                    String depth2Name = getFirstChildText(twoDepth, REGION_NAME_TAGS);
                    String depth2Code = getFirstChildText(twoDepth, REGION_CODE_TAGS);
                    addRegionItem(items, seen, depth2Code, depth1Name, depth2Name, null);

                    NodeList threeDepthList = getChildNodes(twoDepth, "threeDepth");
                    for (int m = 0; m < threeDepthList.getLength(); m++) {
                        Node threeDepth = threeDepthList.item(m);
                        String depth3Name = getFirstChildText(threeDepth, REGION_NAME_TAGS);
                        String depth3Code = getFirstChildText(threeDepth, REGION_CODE_TAGS);
                        addRegionItem(items, seen, depth3Code, depth1Name, depth2Name, depth3Name);
                    }
                }
            }
        }
        return items;
    }

    private List<OccupationCodeItem> parseOccupationCodes(Document document) {
        List<OccupationCodeItem> items = new ArrayList<>();
        if (document == null) return items;

        NodeList rootList = getRootNodes(document, "cmcdOccupation", "cmcdOccupationCode", "cmcdJob");
        if (rootList == null || rootList.getLength() == 0) return items;

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rootList.getLength(); i++) {
            Node root = rootList.item(i);
            NodeList oneDepthList = getChildNodes(root, "oneDepth");
            for (int j = 0; j < oneDepthList.getLength(); j++) {
                Node oneDepth = oneDepthList.item(j);
                String depth1Name = getFirstChildText(oneDepth, JOB_NAME_TAGS);
                String depth1Code = getFirstChildText(oneDepth, JOB_CODE_TAGS);
                addOccupationItem(items, seen, depth1Code, depth1Name, null, null);

                NodeList twoDepthList = getChildNodes(oneDepth, "twoDepth");
                for (int k = 0; k < twoDepthList.getLength(); k++) {
                    Node twoDepth = twoDepthList.item(k);
                    String depth2Name = getFirstChildText(twoDepth, JOB_NAME_TAGS);
                    String depth2Code = getFirstChildText(twoDepth, JOB_CODE_TAGS);
                    addOccupationItem(items, seen, depth2Code, depth1Name, depth2Name, null);

                    NodeList threeDepthList = getChildNodes(twoDepth, "threeDepth");
                    for (int m = 0; m < threeDepthList.getLength(); m++) {
                        Node threeDepth = threeDepthList.item(m);
                        String depth3Name = getFirstChildText(threeDepth, JOB_NAME_TAGS);
                        String depth3Code = getFirstChildText(threeDepth, JOB_CODE_TAGS);
                        addOccupationItem(items, seen, depth3Code, depth1Name, depth2Name, depth3Name);
                    }
                }
            }
        }
        return items;
    }

    private void addRegionItem(List<RegionCodeItem> items, Set<String> seen, String code, String depth1, String depth2, String depth3) {
        if (code == null || code.isBlank()) return;
        if (!seen.add(code)) return;
        items.add(new RegionCodeItem(code.trim(), safe(depth1), safe(depth2), safe(depth3)));
    }

    private void addOccupationItem(List<OccupationCodeItem> items, Set<String> seen, String code, String depth1, String depth2, String depth3) {
        if (code == null || code.isBlank()) return;
        if (!seen.add(code)) return;
        items.add(new OccupationCodeItem(code.trim(), safe(depth1), safe(depth2), safe(depth3)));
    }

    private NodeList getChildNodes(Node parent, String name) {
        List<Node> matched = new ArrayList<>();
        if (parent == null) return new NodeListAdapter(matched);
        NodeList children = parent.getChildNodes();
        if (children == null) return new NodeListAdapter(matched);
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node == null) continue;
            if (name.equals(node.getNodeName())) {
                matched.add(node);
            }
        }
        return new NodeListAdapter(matched);
    }

    private NodeList getRootNodes(Document document, String... names) {
        if (document == null || names == null) return new NodeListAdapter(List.of());
        for (String name : names) {
            NodeList list = document.getElementsByTagName(name);
            if (list != null && list.getLength() > 0) {
                return list;
            }
        }
        return new NodeListAdapter(List.of());
    }

    private String getFirstChildText(Node parent, String[] candidates) {
        if (parent == null || candidates == null) return null;
        for (String name : candidates) {
            String text = getChildText(parent, name);
            if (text != null && !text.isBlank()) return text;
        }
        return null;
    }

    private String getChildText(Node parent, String name) {
        if (parent == null || name == null) return null;
        NodeList children = parent.getChildNodes();
        if (children == null) return null;
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node == null) continue;
            if (name.equals(node.getNodeName())) {
                return node.getTextContent();
            }
        }
        return null;
    }

    private String safe(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    public record RegionCodeItem(String code, String depth1, String depth2, String depth3) {
    }

    public record OccupationCodeItem(String code, String depth1, String depth2, String depth3) {
    }

    private static class NodeListAdapter implements NodeList {
        private final List<Node> nodes;

        private NodeListAdapter(List<Node> nodes) {
            this.nodes = nodes;
        }

        @Override
        public Node item(int index) {
            if (index < 0 || index >= nodes.size()) return null;
            return nodes.get(index);
        }

        @Override
        public int getLength() {
            return nodes.size();
        }
    }
}
