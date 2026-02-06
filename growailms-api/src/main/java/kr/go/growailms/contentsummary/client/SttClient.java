package kr.go.growailms.contentsummary.client;

import java.nio.file.Path;

public interface SttClient {
    /**
     * @param mediaFile 오디오/영상 파일 경로(제공자에 따라 지원 포맷이 다를 수 있습니다)
     * @param language  전사 언어 힌트(예: ko)
     * @return 전사 텍스트(빈 문자열이면 실패로 간주하는 편이 안전합니다)
     */
    String transcribe(Path mediaFile, String language);
}

