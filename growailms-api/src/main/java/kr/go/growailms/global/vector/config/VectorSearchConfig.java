package kr.go.growailms.global.vector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vector.search")
public class VectorSearchConfig {
    // 왜: 벡터 검색은 "얼마나 많이(topK) 가져오느냐"에 따라 결과 품질/비용이 크게 달라서,
    //     운영 환경에서 쉽게 튜닝할 수 있게 설정으로 분리합니다.

    /**
     * VectorStore에서 가져올 최대 후보 개수 상한입니다.
     * - 왜: 잘못된 요청(예: topK=10000)로 인해 Qdrant/임베딩 비용이 폭증하는 것을 막습니다.
     */
    private int maxTopK = 300;

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }

    public int maxTopKOrDefault() {
        // 왜: 설정이 0/음수로 들어오면 clamp가 깨질 수 있으니, 안전한 기본값으로 보정합니다.
        int raw = maxTopK;
        if (raw <= 0) return 300;
        return Math.min(raw, 1000);
    }
}

