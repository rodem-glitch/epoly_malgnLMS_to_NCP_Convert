package kr.go.growailms.recocontent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.go.growailms.recocontent.entity.RecoContent;
import kr.go.growailms.recocontent.repository.RecoContentRepository;
import kr.go.growailms.recocontent.service.dto.ImportRecoContentsSampleRequest;
import kr.go.growailms.recocontent.service.dto.ImportRecoContentsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecoContentImportService {

    private final RecoContentRepository recoContentRepository;

    public RecoContentImportService(RecoContentRepository recoContentRepository) {
        // 왜: 임시 데이터(샘플)라도 "원본 테이블"에 먼저 넣어야, 추천/벡터 인덱싱을 재현 가능하게 운영할 수 있습니다.
        this.recoContentRepository = Objects.requireNonNull(recoContentRepository);
    }

    @Transactional
    public ImportRecoContentsResponse importFromSampleText(
        String rawText,
        boolean replace
    ) {
        String safeText = rawText == null ? "" : rawText;

        List<RecoContent> parsed = parseSampleText(safeText);

        if (replace) {
            // 왜: 테스트 데이터를 반복해서 적재할 때, 중복 누적이 가장 흔한 사고라서 "전체 교체" 옵션을 제공합니다.
            recoContentRepository.deleteAllInBatch();
        }

        List<RecoContent> saved = recoContentRepository.saveAll(parsed);
        return new ImportRecoContentsResponse(parsed.size(), saved.size());
    }

    @Transactional
    public ImportRecoContentsResponse importFromSampleRequest(ImportRecoContentsSampleRequest request) {
        ImportRecoContentsSampleRequest safe = request == null ? new ImportRecoContentsSampleRequest("", false) : request;
        return importFromSampleText(safe.sampleText(), safe.replace());
    }

    List<RecoContent> parseSampleText(String rawText) {
        // 왜: 현재는 "개발/검증"용 임시 데이터를 빠르게 넣는 게 목적이라, 파일 포맷에 강하게 의존하지 않도록 관대한 파서를 둡니다.
        String normalized = rawText.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");

        Pattern categoryPattern = Pattern.compile("^\\s*\\d+\\.\\s*(.+?)\\s*\\(\\d+건\\)\\s*$");
        Pattern titlePattern = Pattern.compile("^\\s*\\d+\\.\\s*(.+?)\\s*$");

        String currentCategory = "미분류";
        String currentTitle = null;
        String currentSummary = null;
        String currentKeywords = null;

        List<RecoContent> results = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) continue;

            Matcher categoryMatcher = categoryPattern.matcher(line);
            if (categoryMatcher.matches()) {
                currentCategory = categoryMatcher.group(1).trim();
                continue;
            }

            if (line.startsWith("* 요약:")) {
                String after = line.substring("* 요약:".length()).trim();
                currentSummary = after;
                continue;
            }

            if (line.startsWith("* 핵심 키워드:")) {
                String after = line.substring("* 핵심 키워드:".length()).trim();
                currentKeywords = after;
                // 왜: 키워드가 나왔다는 건 "한 콘텐츠 단위가 끝났다"는 신호라서 여기서 저장합니다.
                if (currentTitle != null && currentSummary != null && currentKeywords != null) {
                    results.add(new RecoContent(currentCategory, currentTitle, currentSummary, currentKeywords));
                }
                currentTitle = null;
                currentSummary = null;
                currentKeywords = null;
                continue;
            }

            Matcher titleMatcher = titlePattern.matcher(line);
            if (titleMatcher.matches() && !line.contains("건)")) {
                String maybeTitle = titleMatcher.group(1).trim();
                // 왜: 카테고리 줄도 "1. ~" 형태라서, "(10건)" 패턴은 위에서 먼저 걸러집니다.
                currentTitle = maybeTitle;
                currentSummary = null;
                currentKeywords = null;
                continue;
            }

            // 왜: 요약이 여러 줄로 들어올 수도 있어서, 요약 모드일 때는 다음 필드가 나오기 전까지 이어 붙입니다.
            if (currentTitle != null && currentSummary != null && currentKeywords == null) {
                currentSummary = currentSummary + "\n" + line;
            }
        }

        return results;
    }
}
