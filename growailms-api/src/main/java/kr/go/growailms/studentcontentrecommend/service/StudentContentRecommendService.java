package kr.go.growailms.studentcontentrecommend.service;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import kr.go.growailms.global.vector.service.VectorStoreService;
import kr.go.growailms.global.vector.service.VectorQueryService;
import kr.go.growailms.global.vector.service.dto.VectorSearchResult;
import kr.go.growailms.recocontent.entity.RecoContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import kr.go.growailms.studentcontentrecommend.service.dto.StudentHomeRecommendRequest;
import kr.go.growailms.studentcontentrecommend.service.dto.StudentSearchRecommendRequest;
import kr.go.growailms.studentcontentrecommend.service.dto.StudentVideoRecommendResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StudentContentRecommendService {

    private static final Pattern TOKEN_SPLIT_PATTERN = Pattern.compile("[\\s\\p{Punct}]+");
    // 왜: 임시 테스트에서는 lessonId(영상키) 중복이 많아, 중복 제거를 끄고 결과를 더 넓게 보이게 합니다.
    // - 원복할 때는 true로만 바꾸면 됩니다.
    private static final boolean ENABLE_RECO_DEDUPE = false;

    private final VectorStoreService vectorStoreService;
    private final VectorQueryService vectorQueryService;
    private final JdbcTemplate jdbcTemplate;
    private final RecoContentRepository recoContentRepository;

    public StudentContentRecommendService(
        VectorStoreService vectorStoreService,
        VectorQueryService vectorQueryService,
        JdbcTemplate jdbcTemplate,
        RecoContentRepository recoContentRepository
    ) {
        // 왜: 홈 추천은 기존 VectorStoreService를, 검색은 RETRIEVAL_QUERY를 쓰는 VectorQueryService를 사용합니다.
        this.vectorStoreService = Objects.requireNonNull(vectorStoreService);
        this.vectorQueryService = Objects.requireNonNull(vectorQueryService);
        // 왜: 레거시 LMS DB에서 "수강/시청/완료" 상태를 빠르게 확인해야 해서 JdbcTemplate을 사용합니다.
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.recoContentRepository = Objects.requireNonNull(recoContentRepository);
    }

    public List<StudentVideoRecommendResponse> recommendHome(StudentHomeRecommendRequest request) {
        StudentHomeRecommendRequest safe = request == null ? StudentHomeRecommendRequest.empty() : request;

        String query = buildHomeQueryText(safe);
        String filterExpression = "source == 'tb_reco_content'";

        int desiredTopK = safe.topKOrDefault();
        // 왜: 홈 추천에서 "이미 수강/시청/완료 제외"를 하면 후보가 많이 빠질 수 있으니, Qdrant는 넉넉히 가져옵니다.
        int fetchTopK = computeFetchTopK(desiredTopK, safe.excludeEnrolledOrDefault() || safe.excludeWatchedOrDefault() || safe.excludeCompletedOrDefault());

        List<VectorSearchResult> results = vectorStoreService.similaritySearch(
            query,
            fetchTopK,
            safe.similarityThresholdOrDefault(),
            filterExpression
        );

        return postProcessResults(
            results,
            safe.userId(),
            safe.siteId(),
            desiredTopK,
            safe.excludeEnrolledOrDefault(),
            safe.excludeWatchedOrDefault(),
            safe.excludeCompletedOrDefault()
        );
    }

    public List<StudentVideoRecommendResponse> recommendSearch(StudentSearchRecommendRequest request) {
        StudentSearchRecommendRequest safe = request == null ? StudentSearchRecommendRequest.empty() : request;

        String query = buildSearchQueryText(safe);
        String filterExpression = "source == 'tb_reco_content'";

        int desiredTopK = safe.topKOrDefault();

        List<VectorSearchResult> keywordResults = keywordSearchFromDatabase(safe.query(), desiredTopK);

        int fetchTopK = computeSearchFetchTopK(desiredTopK);
        // 왜: RETRIEVAL_QUERY task-type으로 검색해야 벡터 공간에서 쿼리-문서 매칭이 최적화됩니다.
        List<VectorSearchResult> vectorResults = vectorQueryService.similaritySearchWithQueryTaskType(
            query,
            fetchTopK,
            safe.similarityThresholdOrDefault(),
            filterExpression
        );

        // 왜: 벡터 검색은 의미(semantic)에 강하지만, "정확한 제목" 검색에서는 순위가 뒤로 밀리기 쉽습니다.
        //     그래서 DB 키워드 매칭 결과를 먼저 보여주고, 나머지는 벡터 결과를 제목 매칭으로 재정렬해 보완합니다.
        List<VectorSearchResult> results = mergeAndDedupeResults(
            keywordResults,
            rerankByTitleMatch(query, vectorResults)
        );

        // 왜: 검색은 기본적으로 전체에서 찾되, 가능하면 "내가 본/수강한/완료한" 표시를 같이 내려줍니다.
        return postProcessResults(results, safe.userId(), safe.siteId(), desiredTopK, false, false, false);
    }

    private int computeSearchFetchTopK(int desiredTopK) {
        // 왜: 검색은 "정확한 제목"이 topK 밖으로 밀릴 수 있어, 후보를 조금 더 넉넉히 받아온 뒤 재정렬합니다.
        // - VectorStoreService에서 최종 상한을 한 번 더 clamp 하므로, 여기서는 과도한 값만 피하는 수준으로 둡니다.
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
            // 왜: 사용자가 ":" 같은 특수문자를 섞어 입력하면 LIKE 매칭이 실패할 수 있어,
            //     토큰(단어) 단위로 한 번 더 찾아서 후보를 모읍니다.
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

        // 왜: 제목에 없더라도 요약/키워드에 쿼리가 있으면 검색 의도에 더 가깝다고 판단합니다.
        boolean keywordContains = containsNormalized(keywords, normalizedQuery);
        boolean summaryContains = containsNormalized(summary, normalizedQuery);
        int tier = (keywordContains || summaryContains) ? 1 : titleScore.tier();
        int tokenMatches = Math.max(titleScore.tokenMatches(), countTokenMatches(normalizeForMatch(keywords), tokens));

        return new TitleMatchScore(tier, tokenMatches);
    }

    private double computeSyntheticKeywordScore(String rawQuery, RecoContent content) {
        // 왜: 벡터 점수와 스케일이 다르지만, 화면에서 "대략의 정렬 힌트"로만 쓰기 위해 단순한 가중치를 둡니다.
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
        // 왜: 레거시 인덱싱(lesson) 문서는 title 대신 lesson_nm을 쓸 수 있어 fallback을 둡니다.
        Object lessonName = result.metadata().get("lesson_nm");
        return lessonName == null ? null : String.valueOf(lessonName);
    }

    private List<VectorSearchResult> mergeAndDedupeResults(List<VectorSearchResult> first, List<VectorSearchResult> second) {
        if (!ENABLE_RECO_DEDUPE) {
            // 왜: 임시 테스트에서는 중복 제거를 하지 않고 그대로 합쳐 보여줍니다.
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
            // 왜: 1글자 토큰은 노이즈가 커서(예: "과", "의") 검색 품질을 떨어뜨릴 수 있어 제외합니다.
            .filter(t -> t.length() >= 2)
            .distinct()
            .limit(12)
            .toList();
    }

    private String buildHomeQueryText(StudentHomeRecommendRequest request) {
        // 왜: 홈 추천은 "학생이 저장한 프롬프트"만 기준으로 추천합니다.
        String extra = request.extraQuery() == null ? "" : request.extraQuery().trim();
        if (extra.isBlank()) {
            return "학생에게 도움이 되는 교육용 영상을 추천해 주세요.";
        }
        return extra;
    }

    private String buildSearchQueryText(StudentSearchRecommendRequest request) {
        // 왜: 검색 추천은 사용자가 친 자연어가 곧 "의도"라서, 불필요한 포맷팅 없이 그대로 쓰는 게 좋습니다.
        String q = request.query() == null ? "" : request.query().trim();
        if (q.isBlank()) return "학생에게 도움이 되는 교육용 영상을 추천해 주세요.";
        return q;
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value == null) return;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return;
        sb.append(label).append(": ").append(trimmed).append("\n");
    }

    private List<StudentVideoRecommendResponse> postProcessResults(
        List<VectorSearchResult> results,
        Long userId,
        Integer siteId,
        int desiredTopK,
        boolean excludeEnrolled,
        boolean excludeWatched,
        boolean excludeCompleted
    ) {
        Map<String, LessonStudyStatus> statusByLessonId = fetchLessonStudyStatus(userId, siteId);
        Map<Long, RecoContent> contentById = fetchRecoContentsById(results);
        Set<String> seenLessonIds = new HashSet<>();
        List<StudentVideoRecommendResponse> out = new ArrayList<>();

        for (VectorSearchResult result : results) {
            StudentVideoRecommendResponse response = toResponse(result, statusByLessonId, contentById);
            String lessonId = response.lessonId();

            // 왜: 임시 데이터(lesson_id 없는 문서)도 존재할 수 있으니, 그 경우는 그냥 통과시킵니다.
            if (lessonId != null) {
                if (ENABLE_RECO_DEDUPE) {
                    // 왜: 같은 레슨이 중복으로 내려오면 UX가 나빠서, 첫 번째만 남깁니다.
                    if (!seenLessonIds.add(lessonId)) continue;
                }

                LessonStudyStatus status = statusByLessonId.get(lessonId);
                if (excludeEnrolled && status != null && Boolean.TRUE.equals(status.enrolled())) continue;
                if (excludeWatched && status != null && Boolean.TRUE.equals(status.watched())) continue;
                if (excludeCompleted && status != null && Boolean.TRUE.equals(status.completed())) continue;
            }

            out.add(response);
            if (out.size() >= desiredTopK) break;
        }

        return out;
    }

    private StudentVideoRecommendResponse toResponse(
        VectorSearchResult result,
        Map<String, LessonStudyStatus> statusByLessonId,
        Map<Long, RecoContent> contentById
    ) {
        Map<String, Object> meta = result.metadata() == null ? new HashMap<>() : new HashMap<>(result.metadata());

        String lessonId = toStringValue(meta.get("lesson_id"));
        Long recoContentId = toLong(meta.get("content_id"));

        // 왜: 벡터 메타데이터에는 summary가 없을 수 있으므로, DB에서 직접 조회합니다.
        String title = null;
        String categoryNm = null;
        String summary = null;
        String keywords = null;

        if (recoContentId != null) {
            // 왜: topK가 커지면 findById를 N번 호출하는 방식은 DB에 부담이 커서, 미리 한 번에 조회한 Map을 씁니다.
            RecoContent content = contentById == null ? null : contentById.get(recoContentId);
            if (content != null) {
                title = content.getTitle();
                categoryNm = content.getCategoryNm();
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
        if (categoryNm == null) {
            categoryNm = meta.get("category_nm") != null ? String.valueOf(meta.get("category_nm")) : null;
        }

        LessonStudyStatus status = (lessonId == null || lessonId.isBlank()) ? null : statusByLessonId.get(lessonId);
        return new StudentVideoRecommendResponse(
            lessonId,
            recoContentId,
            title,
            categoryNm,
            summary,
            keywords,
            result.score(),
            status == null ? null : status.enrolled(),
            status == null ? null : status.watched(),
            status == null ? null : status.completed(),
            status == null ? null : status.lastDate(),
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

    private int computeFetchTopK(int desiredTopK, boolean needBuffer) {
        if (!needBuffer) return desiredTopK;
        // 왜: 제외 조건이 켜지면 결과가 부족할 수 있으니, Qdrant에서 더 많이 가져옵니다(네트워크/CPU 균형을 위해 상한 둠).
        int buffered = desiredTopK * 10 + 50;
        return Math.max(desiredTopK, Math.min(buffered, 300));
    }

    private Map<String, LessonStudyStatus> fetchLessonStudyStatus(Long userId, Integer siteId) {
        // 왜: 콜러스 영상은 외부 콘텐츠라서 기존 LMS DB(LM_COURSE_PROGRESS)에 학습 기록이 없습니다.
        // 추후 콜러스 시청 기록을 별도 테이블로 관리하게 되면, 여기서 조회하면 됩니다.
        // 현재는 빈 Map을 반환하여 수강/시청/완료 상태 표시 없이 추천만 합니다.
        return Map.of();
    }

    private record LessonStudyStatus(
        Boolean enrolled,
        Boolean watched,
        Boolean completed,
        String lastDate
    ) {}
}
