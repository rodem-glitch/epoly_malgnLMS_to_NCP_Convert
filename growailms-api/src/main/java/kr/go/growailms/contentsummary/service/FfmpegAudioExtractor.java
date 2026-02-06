package kr.go.growailms.contentsummary.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 왜: STT는 보통 오디오만 넣는 게 더 빠르고/정확합니다.
 * 다만 ffmpeg가 서버에 없을 수도 있으니, "있으면 사용"하고 "없으면 영상 파일 그대로" 다음 단계로 넘기도록 설계합니다.
 */
public class FfmpegAudioExtractor {

    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder("ffmpeg", "-version")
                .redirectErrorStream(true)
                .start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public Path extractWav(Path videoFile, Path outWavFile) {
        if (videoFile == null) throw new IllegalArgumentException("videoFile이 null입니다.");
        if (outWavFile == null) throw new IllegalArgumentException("outWavFile이 null입니다.");

        try {
            Files.createDirectories(outWavFile.getParent());
            // 왜: wav(16kHz/mono)는 STT에서 흔히 쓰는 표준 포맷이라 변환 문제를 줄여줍니다.
            Process p = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", videoFile.toAbsolutePath().toString(),
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                outWavFile.toAbsolutePath().toString()
            ).redirectErrorStream(true).start();

            String lastLines = tailProcessOutput(p, 50);
            int code = p.waitFor();
            if (code != 0) {
                throw new IllegalStateException("ffmpeg 변환 실패 (exit=" + code + ")\n" + lastLines);
            }
            return outWavFile;
        } catch (Exception e) {
            throw new IllegalStateException("오디오 추출(ffmpeg) 중 오류가 발생했습니다.", e);
        }
    }

    private static String tailProcessOutput(Process p, int maxLines) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String[] ring = new String[Math.max(1, maxLines)];
            int i = 0;
            String line;
            while ((line = br.readLine()) != null) {
                ring[i % ring.length] = line;
                i++;
            }
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, i - ring.length);
            for (int k = start; k < i; k++) {
                String v = ring[k % ring.length];
                if (v != null) sb.append(v).append('\n');
            }
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}

