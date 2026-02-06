package kr.go.growailms.statistics.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "statistics.ai")
public class StatisticsAiProperties {
    // 왜: AI 통계는 외부 LLM 호출이 포함되어 비용/보안/품질 영향이 커서,
    //     운영 환경에서 기능 on/off, 모델, 디버그 출력 등을 설정으로 제어할 수 있어야 합니다.

    private boolean enabled = true;
    private String apiKey;
    private String model = "gemini-1.5-flash";
    private boolean debug = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }
}

