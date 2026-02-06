package kr.go.growailms.statistics.ai.v2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class StatisticsAiV2ExecutorConfig {
    @Bean(destroyMethod = "shutdown")
    public ExecutorService statisticsAiV2Executor() {
        // 왜: v2는 "병렬 조회"가 핵심이어서, 공용 ForkJoinPool에 기대지 않고 별도 풀로 격리합니다.
        //     (외부 API 지연이 전체 서버를 잡아먹는 상황을 줄입니다)
        return Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
    }
}

