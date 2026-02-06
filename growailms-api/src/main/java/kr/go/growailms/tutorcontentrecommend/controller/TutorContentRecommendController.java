package kr.go.growailms.tutorcontentrecommend.controller;

import java.util.List;
import kr.go.growailms.tutorcontentrecommend.service.TutorContentRecommendService;
import kr.go.growailms.tutorcontentrecommend.service.dto.TutorContentRecommendRequest;
import kr.go.growailms.tutorcontentrecommend.service.dto.TutorContentRecommendResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tutor/content-recommend")
public class TutorContentRecommendController {

    private final TutorContentRecommendService tutorContentRecommendService;

    public TutorContentRecommendController(TutorContentRecommendService tutorContentRecommendService) {
        // 왜: 교수자 화면(과목/차시 입력값)에서 "콘텐츠 추천"을 눌렀을 때 호출되는 전용 API가 필요합니다.
        this.tutorContentRecommendService = tutorContentRecommendService;
    }

    @PostMapping("/lessons")
    public List<TutorContentRecommendResponse> recommendLessons(@RequestBody(required = false) TutorContentRecommendRequest request) {
        return tutorContentRecommendService.recommendLessons(request);
    }
}

