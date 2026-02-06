package kr.go.growailms.statistics.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatisticsDashboardPageController {
    // 왜: 별도 템플릿 엔진 없이도 통계 화면을 URL로 바로 접근할 수 있게 forward 합니다.

    @GetMapping({"/statistics", "/statistics/dashboard"})
    public String dashboard() {
        return "forward:/statistics/dashboard.html";
    }
}

