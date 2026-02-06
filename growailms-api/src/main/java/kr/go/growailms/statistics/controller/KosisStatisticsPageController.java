package kr.go.growailms.statistics.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class KosisStatisticsPageController {
    // 왜: 별도 템플릿 엔진 없이도, static HTML을 보기 쉬운 URL로 연결합니다.

    @GetMapping("/statistics/kosis")
    public String redirectToKosisPage() {
        return "forward:/statistics/kosis.html";
    }
}
