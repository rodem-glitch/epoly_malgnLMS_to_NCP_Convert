package kr.go.growailms.global.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/global")
public class GlobalHealthController {
    // 왜: 공통 상태 확인 엔드포인트를 이 패키지에서 시작해 다른 기능과 충돌을 막습니다.
}
