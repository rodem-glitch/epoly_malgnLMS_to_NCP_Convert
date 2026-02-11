package kr.polytech.lms.tutorcontentrecommend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import kr.polytech.lms.global.vector.service.VectorQueryService;
import kr.polytech.lms.global.vector.service.dto.VectorSearchResult;
import kr.polytech.lms.recocontent.entity.RecoContent;
import kr.polytech.lms.recocontent.repository.RecoContentRepository;
import kr.polytech.lms.tutorcontentrecommend.service.dto.TutorContentRecommendRequest;
import kr.polytech.lms.tutorcontentrecommend.service.dto.TutorContentRecommendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TutorContentRecommendService {

    private static final Logger log = LoggerFactory.getLogger(TutorContentRecommendService.class);
    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[\\s\\p{Punct}]+");
    // 왜: 학생 검색과 동일하게 비교하려면 중복 제거 옵션도 같은 상태(OFF)로 맞춰야 결과 체감이 일치합니다.
    private static final boolean ENABLE_RECO_DEDUPE = false;

    private final VectorQueryService vectorQueryService;
    private final RecoContentRepository recoContentRepository;

    public TutorContentRecommendService(
        VectorQueryService vectorQueryService,
        RecoContentRepository recoContentRepository
    ) {
        // 왜: 교수자 추천도 학생 검색과 동일하게 RETRIEVAL_QUERY 검색을 써야 "자연어 검색" 체감이 맞습니다.
        this.vectorQueryService = Objects.requireNonNull(vectorQueryService);
        this.recoContentRepository = Objects.requireNonNull(recoContentRepository);
    }

    public List<TutorContentRecommendResponse> recommendLessons(TutorContentRecommendRequest request) {
        TutorContentRecommendRequest safe = request == null ? TutorContentRecommendRequest.empty() : request;

        String query = buildSearchQueryText(safe);
        String filterExpression = "source == 'tb_reco_content'";
        int desiredTopK = safe.topKOrDefault();

        List<VectorSearchResult> keywordResults = keywordSearchFromDatabase(query, desiredTopK);
        int fetchTopK = computeSearchFetchTopK(desiredTopK);
        List<VectorSearchResult> vectorResults = vectorQueryService.similaritySearchWithQueryTaskType(
            query,
            fetchTopK,
            safe.similarityThresholdOrDefault(),
            filterExpression
        );

        // 왜: 학생 검색처럼 "제목 일치 우선 + 벡터 의미 유사도"를 결합하면 강의/차시명 기반 검색 체감이 좋아집니다.
        List<VectorSearchResult> results = mergeAndDedupeResults(
            keywordResults,
            rerankByTitleMatch(query, vectorResults)
        );

        Map<Long, RecoContent> contentById = fetchRecoContentsById(results);
        List<TutorContentRecommendResponse> out = results.stream()
            .limit(desiredTopK)
            .map(r -> toResponse(r, contentById))
            .toList();

        // 왜: 운영에서 "왜 결과가 이 수치로 나왔는지"를 빠르게 추적하려고 입력/후보/최종 건수를 요약 로그로 남깁니다.
        log.info(
            "[TutorContentRecommend] query_len={}, topK={}, threshold={}, keyword_candidates={}, vector_candidates={}, final_count={}",
            query == null ? 0 : query.length(),
            desiredTopK,
            safe.similarityThresholdOrDefault(),
            keywordResults.size(),
            vectorResults.size(),
            out.size()
        );
        return out;
    }

    private String buildSearchQueryText(TutorContentRecommendRequest request) {
        // 왜: 교수자 추천은 차시명이 "1차시/2차시"처럼 일반적인 값인 경우가 많아
        //     오히려 추천 품질을 떨어뜨릴 수 있으므로, 강의명(courseName)만 기준으로 검색합니다.
        StringBuilder sb = new StringBuilder();
        appendQueryPart(sb, request.courseName());

        if (sb.length() == 0) {
            // 왜: 강의명이 비어 있으면 검색 의도가 성립하지 않으므로 최소 문장으로만 검색을 유지합니다.
            return "과목 개설에 적합한 교육용 영상을 추천해 주세요.";
        }
        return sb.toString().trim();
    }

    private void appendQueryPart(StringBuilder sb, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(trimmed);
    }

    private int computeSearchFetchTopK(int desiredTopK) {
        // 왜: 제목 정확매칭 후보가 벡터 순위에서 밀릴 수 있어, 학생 검색처럼 후보를 넉넉히 받습니다.
        int buffered = desiredTopK * 4;
        return Math.max(desiredTopK, Math.min(buffered, 300));
    }

    private List<VectorSearchResult> keywordSearchFromDatabase(String rawQuery, int desiredTopK) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isBlank()) return List.of();

        int candidateLimit = Math.max(desiredTopK * 3, 50);
        candidateLimit = Math.min(candidateLimit, 200);

        List<RecoContent> candidates = recoContentRepository.searchByKeyword(q, PageRequest.of(0, candidateLimit));
        if (candidates == null || candidates.isEmpty()) {
            // 왜: 콜론/특수문자 포함 검색어는 LIKE가 약해질 수 있어 토큰 단위로 한 번 더 보강합니다.
            List<String> tokens = extractTokens(q);
            if (!tokens.isEmpty()) {
                Map<Long, RecoContent> merged = new LinkedHashMap<>();
                for (String token : tokens) {
                    List<RecoContent> partial = recoContentRepository.searchByKeyword(token, PageRequest.of(0, candidateLimit));
                    if (partial == null || partial.isEmpty()) continue;
                    for (RecoContent c : partial) {
                        if (c == null || c.getId() == null) continue;
                        merged.putIfAbsent(c.getId(), c);
                    }
                    if (merged.size() >= candidateLimit) break;
                }
                candidates = merged.values().stream().toList();
            }
        }
        if (candidates == null || candidates.isEmpty()) return List.of();

        Comparator<RecoContent> comparator = buildKeywordRankComparator(q);
        return candidates.stream()
            .sorted(comparator)
            .limit(desiredTopK)
            .map(content -> new VectorSearchResult(
                content.getTitle(),
                buildRecoContentMetadata(content),
                computeSyntheticKeywordScore(q, content)
            ))
            .toList();
    }

    private Comparator<RecoContent> buildKeywordRankComparator(String query) {
        String normalizedQuery = normalizeForMatch(query);
        List<String> tokens = extractTokens(query);

        return (a, b) -> {
            TitleMatchScore sa = computeContentMatchScore(normalizedQuery, tokens, a);
            TitleMatchScore sb = computeContentMatchScore(normalizedQuery, tokens, b);

            int cmp = Integer.compare(sb.tier(), sa.tier());
            if (cmp != 0) return cmp;

            cmp = Integer.compare(sb.tokenMatches(), sa.tokenMatches());
            if (cmp != 0) return cmp;

            String at = a.getTitle() == null ? "" : a.getTitle();
            String bt = b.getTitle() == null ? "" : b.getTitle();
            cmp = Integer.compare(at.length(), bt.length());
            if (cmp != 0) return cmp;

            Long aid = a.getId() == null ? Long.MAX_VALUE : a.getId();
            Long bid = b.getId() == null ? Long.MAX_VALUE : b.getId();
            return Long.compare(aid, bid);
        };
    }

    private TitleMatchScore computeContentMatchScore(String normalizedQuery, List<String> tokens, RecoContent content) {
        String title = content == null ? null : content.getTitle();
        String keywords = content == null ? null : content.getKeywords();
        String summary = content == null ? null : content.getSummary();

        TitleMatchScore titleScore = computeTitleMatchScore(normalizedQuery, tokens, title);
        if (titleScore.tier() >= 2) return titleScore;

        boolean keywordContains = containsNormalized(keywords, normalizedQuery);
        boolean summaryContains = containsNormalized(summary, normalizedQuery);
        int tier = (keywordContains || summaryContains) ? 1 : titleScore.tier();
        int tokenMatches = Math.max(titleScore.tokenMatches(), countTokenMatches(normalizeForMatch(keywords), tokens));
        return new TitleMatchScore(tier, tokenMatches);
    }

    private double computeSyntheticKeywordScore(String rawQuery, RecoContent content) {
        String normalizedQuery = normalizeForMatch(rawQuery);
        TitleMatchScore score = computeContentMatchScore(normalizedQuery, extractTokens(rawQuery), content);
        return switch (score.tier()) {
            case 3 -> 1.0;
            case 2 -> 0.95;
            case 1 -> 0.9;
            default -> 0.85;
        };
    }

    private List<VectorSearchResult> rerankByTitleMatch(String rawQuery, List<VectorSearchResult> results) {
        if (results == null || results.size() <= 1) return results == null ? List.of() : results;

        String normalizedQuery = normalizeForMatch(rawQuery);
        List<String> tokens = extractTokens(rawQuery);

        return results.stream()
            .sorted((a, b) -> {
                TitleMatchScore sa = computeTitleMatchScore(normalizedQuery, tokens, extractTitle(a));
                TitleMatchScore sb = computeTitleMatchScore(normalizedQuery, tokens, extractTitle(b));

                int cmp = Integer.compare(sb.tier(), sa.tier());
                if (cmp != 0) return cmp;

                cmp = Integer.compare(sb.tokenMatches(), sa.tokenMatches());
                if (cmp != 0) return cmp;

                return Double.compare(b.score(), a.score());
            })
            .toList();
    }

    private String extractTitle(VectorSearchResult result) {
        if (result == null || result.metadata() == null) return null;
        Object v = result.metadata().get("title");
        if (v != null) return String.valueOf(v);
        return null;
    }

    private List<VectorSearchResult> mergeAndDedupeResults(List<VectorSearchResult> first, List<VectorSearchResult> second) {
        if (!ENABLE_RECO_DEDUPE) {
            // 왜: 학생 검색과 동일 비교를 위해 현재는 중복 제거를 하지 않고 그대로 합칩니다.
            return concat(first, second);
        }

        List<VectorSearchResult> out = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        for (VectorSearchResult r : concat(first, second)) {
            if (r == null) continue;
            String key = buildDedupeKey(r);
            if (key != null) {
                if (!seenKeys.add(key)) continue;
            }
            out.add(r);
        }
        return out;
    }

    private List<VectorSearchResult> concat(List<VectorSearchResult> a, List<VectorSearchResult> b) {
        if (a == null || a.isEmpty()) return b == null ? List.of() : b;
        if (b == null || b.isEmpty()) return a;
        List<VectorSearchResult> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private String buildDedupeKey(VectorSearchResult result) {
        Map<String, Object> meta = result.metadata();
        if (meta == null) return null;

        Long contentId = toLong(meta.get("content_id"));
        if (contentId != null) return "content:" + contentId;

        String lessonId = toStringValue(meta.get("lesson_id"));
        if (lessonId != null) return "lesson:" + lessonId;

        String title = extractTitle(result);
        return title == null || title.isBlank() ? null : "title:" + title.trim();
    }

    private Map<String, Object> buildRecoContentMetadata(RecoContent content) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("source", "tb_reco_content");
        meta.put("content_id", content.getId());
        if (content.getLessonId() != null && !content.getLessonId().isBlank()) {
            meta.put("lesson_id", content.getLessonId());
        }
        meta.put("category_nm", content.getCategoryNm());
        meta.put("title", content.getTitle());
        meta.put("keywords", content.getKeywords());
        return meta;
    }

    private record TitleMatchScore(int tier, int tokenMatches) {}

    private TitleMatchScore computeTitleMatchScore(String normalizedQuery, List<String> tokens, String title) {
        if (normalizedQuery == null || normalizedQuery.isBlank() || title == null || title.isBlank()) {
            return new TitleMatchScore(0, 0);
        }

        String normalizedTitle = normalizeForMatch(title);
        if (normalizedTitle.isBlank()) return new TitleMatchScore(0, 0);

        boolean exact = normalizedTitle.equals(normalizedQuery);
        boolean contains = !exact && normalizedTitle.contains(normalizedQuery);

        int tokenMatches = countTokenMatches(normalizedTitle, tokens);
        int tier = exact ? 3 : (contains ? 2 : (tokenMatches > 0 ? 1 : 0));
        return new TitleMatchScore(tier, tokenMatches);
    }

    private int countTokenMatches(String normalizedTarget, List<String> tokens) {
        if (normalizedTarget == null || normalizedTarget.isBlank() || tokens == null || tokens.isEmpty()) return 0;

        int matches = 0;
        for (String token : tokens) {
            String nt = normalizeForMatch(token);
            if (nt.isBlank()) continue;
            if (normalizedTarget.contains(nt)) matches++;
        }
        return matches;
    }

    private boolean containsNormalized(String rawText, String normalizedQuery) {
        if (rawText == null || rawText.isBlank()) return false;
        if (normalizedQuery == null || normalizedQuery.isBlank()) return false;
        return normalizeForMatch(rawText).contains(normalizedQuery);
    }

    private static String normalizeForMatch(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.isBlank()) return "";

        StringBuilder sb = new StringBuilder(trimmed.length());
        trimmed.codePoints().forEach(cp -> {
            if (Character.isLetterOrDigit(cp)) {
                sb.appendCodePoint(Character.toLowerCase(cp));
            }
        });
        return sb.toString();
    }

    private static List<String> extractTokens(String input) {
        if (input == null) return List.of();
        String trimmed = input.trim();
        if (trimmed.isBlank()) return List.of();

        return Arrays.stream(TOKEN_SPLIT_PATTERN.split(trimmed))
            .map(String::trim)
            .filter(t -> !t.isBlank())
            // 왜: 한 글자 토큰은 노이즈가 커서 정확매칭 보정 점수를 왜곡할 수 있어 제외합니다.
            .filter(t -> t.length() >= 2)
            .distinct()
            .limit(12)
            .toList();
    }

    private TutorContentRecommendResponse toResponse(VectorSearchResult result, Map<Long, RecoContent> contentById) {
        Map<String, Object> meta = result.metadata() == null ? new HashMap<>() : new HashMap<>(result.metadata());

        String lessonId = toStringValue(meta.get("lesson_id"));
        Long recoContentId = toLong(meta.get("content_id"));

        // 왜: 벡터 메타데이터에는 summary가 없을 수 있으므로, DB에서 직접 조회합니다.
        String title = null;
        String category = null;
        String summary = null;
        String keywords = null;

        if (recoContentId != null) {
            // 왜: topK가 커져도 DB 조회가 N번 반복되지 않도록, 미리 한 번에 조회한 Map을 사용합니다.
            RecoContent content = contentById == null ? null : contentById.get(recoContentId);
            if (content != null) {
                title = content.getTitle();
                category = content.getCategoryNm();
                summary = content.getSummary();
                keywords = content.getKeywords();
                if (lessonId == null) {
                    lessonId = content.getLessonId();
                }
            }
        }

        // 왜: DB 조회 실패 시 메타데이터에서 fallback
        if (title == null) {
            title = meta.get("title") != null ? String.valueOf(meta.get("title")) : null;
        }
        if (category == null) {
            category = meta.get("category_nm") != null ? String.valueOf(meta.get("category_nm")) : null;
        }

        return new TutorContentRecommendResponse(
            lessonId,
            recoContentId,
            title,
            category,
            summary,
            keywords,
            result.score(),
            meta
        );
    }

    private Map<Long, RecoContent> fetchRecoContentsById(List<VectorSearchResult> results) {
        if (results == null || results.isEmpty()) return Map.of();

        Set<Long> ids = new HashSet<>();
        for (VectorSearchResult result : results) {
            if (result == null || result.metadata() == null) continue;
            Long id = toLong(result.metadata().get("content_id"));
            if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) return Map.of();

        Map<Long, RecoContent> map = new HashMap<>();
        for (RecoContent content : recoContentRepository.findAllById(ids)) {
            if (content == null || content.getId() == null) continue;
            map.put(content.getId(), content);
        }
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(value).trim();
            if (s.isBlank()) return null;
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toStringValue(Object value) {
        // 왜: 콜러스 영상 키값은 '5vcd73vW' 같은 문자열이므로, 그대로 String으로 변환합니다.
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isBlank() ? null : s;
    }
}
