package kr.go.growailms.statistics.ai.v2;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics/api/ai/v2/datastore")
public class StatisticsAiV2DataStoreApiController {
    // 왜: 프론트 없이도(또는 LLM 내부에서) "어떤 데이터/연산이 가능한지"를 빠르게 조회할 수 있어야 합니다.

    private final StatisticsAiV2DataStoreService dataStoreService;
    private final StatisticsAiV2DataStoreIndexService indexService;

    public StatisticsAiV2DataStoreApiController(
            StatisticsAiV2DataStoreService dataStoreService,
            StatisticsAiV2DataStoreIndexService indexService
    ) {
        this.dataStoreService = dataStoreService;
        this.indexService = indexService;
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody StatisticsAiV2DataStoreSearchRequest request) {
        try {
            if (request == null || request.prompt() == null || request.prompt().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError("prompt는 필수입니다."));
            }
            return ResponseEntity.ok(dataStoreService.search(request.prompt(), request.context()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("데이터 스토어 검색 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> reindex() {
        try {
            int count = indexService.reindex();
            return ResponseEntity.ok(new ReindexResponse(count));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("데이터 스토어 인덱싱 중 오류가 발생했습니다."));
        }
    }

    private record ApiError(String message) {
    }

    private record ReindexResponse(int indexedDocuments) {
    }
}
