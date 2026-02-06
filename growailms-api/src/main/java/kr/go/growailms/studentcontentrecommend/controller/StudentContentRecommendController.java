package kr.go.growailms.studentcontentrecommend.controller;

import java.util.List;
import kr.go.growailms.studentcontentrecommend.service.StudentContentRecommendService;
import kr.go.growailms.studentcontentrecommend.service.dto.StudentHomeRecommendRequest;
import kr.go.growailms.studentcontentrecommend.service.dto.StudentSearchRecommendRequest;
import kr.go.growailms.studentcontentrecommend.service.dto.StudentVideoRecommendResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student/content-recommend")
public class StudentContentRecommendController {

    private final StudentContentRecommendService studentContentRecommendService;

    public StudentContentRecommendController(StudentContentRecommendService studentContentRecommendService) {
        // 왜: 학생 추천은 "홈 추천(프로필/수강과목 기반)"과 "검색(자연어)" 두 흐름이어서 전용 컨트롤러로 분리합니다.
        this.studentContentRecommendService = studentContentRecommendService;
    }

    @PostMapping("/home")
    public List<StudentVideoRecommendResponse> recommendHome(@RequestBody(required = false) StudentHomeRecommendRequest request) {
        return studentContentRecommendService.recommendHome(request);
    }

    @PostMapping("/home/more")
    public List<StudentVideoRecommendResponse> recommendHomeMore(@RequestBody(required = false) StudentHomeRecommendRequest request) {
        // 왜: "더보기"는 많이 보여주되, 이미 수강/시청/완료한 콘텐츠는 제외하지 않고 표시만 해주려는 요구가 있어 엔드포인트를 분리합니다.
        StudentHomeRecommendRequest safe = request == null ? StudentHomeRecommendRequest.empty() : request;
        StudentHomeRecommendRequest tuned = new StudentHomeRecommendRequest(
            safe.userId(),
            safe.siteId(),
            safe.deptName(),
            safe.majorName(),
            safe.courseNames(),
            safe.extraQuery(),
            false,
            false,
            false,
            safe.topK(),
            safe.similarityThreshold()
        );
        return studentContentRecommendService.recommendHome(tuned);
    }

    @PostMapping("/search")
    public List<StudentVideoRecommendResponse> recommendSearch(@RequestBody(required = false) StudentSearchRecommendRequest request) {
        return studentContentRecommendService.recommendSearch(request);
    }
}
