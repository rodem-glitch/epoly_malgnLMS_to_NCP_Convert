package kr.go.growailms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GrowAiLmsApiApplication {
    public static void main(String[] args) {
        // 왜: `@ConfigurationProperties`(예: kollus.*, KOSIS 설정 등)을 패키지 스캔으로 자동 등록하기 위해 사용합니다.
        SpringApplication.run(GrowAiLmsApiApplication.class, args);
    }
}