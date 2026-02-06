package kr.go.growailms.statistics.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    // 왜: 통계 기능을 별도 모듈로 유지해 변경 영향 범위를 줄입니다.
}
