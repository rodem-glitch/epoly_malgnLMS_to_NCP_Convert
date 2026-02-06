package kr.go.growailms.global.controller;

import kr.go.growailms.global.vector.service.VectorIndexService;
import kr.go.growailms.global.vector.service.dto.IndexLessonsRequest;
import kr.go.growailms.global.vector.service.dto.IndexLessonsResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/global/vector")
public class VectorIndexController {

    private final VectorIndexService vectorIndexService;

    public VectorIndexController(VectorIndexService vectorIndexService) {
        // 왜: 운영/개발 환경에서 "한 번 눌러서" 벡터 적재를 할 수 있는 최소 도구가 필요합니다.
        this.vectorIndexService = vectorIndexService;
    }

    @PostMapping("/index/lessons")
    public IndexLessonsResponse indexLessons(@RequestBody(required = false) IndexLessonsRequest request) {
        IndexLessonsRequest safeRequest = request == null ? new IndexLessonsRequest(null, null, null, null) : request;
        return vectorIndexService.indexLessonsFromLegacy(safeRequest);
    }
}

