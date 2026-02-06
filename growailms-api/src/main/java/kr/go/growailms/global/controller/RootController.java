package kr.go.growailms.global.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 루트 경로 컨트롤러
 * Cloud Run 배포 시 루트 접근 시 서비스 정보 제공
 */
@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> root() {
        return ResponseEntity.ok(Map.of(
                "service", "MalgnLMS API",
                "version", "1.0.0",
                "status", "running",
                "timestamp", LocalDateTime.now().toString(),
                "endpoints", Map.of(
                        "health", "/actuator/health",
                        "info", "/actuator/info"
                )
        ));
    }
}
