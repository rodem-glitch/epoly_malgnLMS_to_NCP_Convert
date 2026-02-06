package kr.go.growailms.contentsummary.client;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Mp4TrackInspectorTest {

    @TempDir
    Path tempDir;

    @Test
    void audio_only_mp4는_audio만_true로_판단한다() throws Exception {
        // 왜: 운영 데이터에 "mp4 확장자지만 video 트랙이 없는 오디오-only" 케이스가 있어
        //     이를 audio/mp4로 업로드할 수 있어야 합니다.
        Path mp4 = tempDir.resolve("audio_only.mp4");
        Files.write(mp4, buildMp4WithTracks(false, true));

        Mp4TrackInspector.TrackTypes tracks = Mp4TrackInspector.inspect(mp4);
        Assertions.assertFalse(tracks.hasVideo());
        Assertions.assertTrue(tracks.hasAudio());
    }

    @Test
    void video_audio_mp4는_video_audio_모두_true로_판단한다() throws Exception {
        Path mp4 = tempDir.resolve("video_audio.mp4");
        Files.write(mp4, buildMp4WithTracks(true, true));

        Mp4TrackInspector.TrackTypes tracks = Mp4TrackInspector.inspect(mp4);
        Assertions.assertTrue(tracks.hasVideo());
        Assertions.assertTrue(tracks.hasAudio());
    }

    private static byte[] buildMp4WithTracks(boolean includeVideo, boolean includeAudio) {
        byte[] payload = new byte[0];
        if (includeVideo) {
            payload = concat(payload, trakWithHandler("vide"));
        }
        if (includeAudio) {
            payload = concat(payload, trakWithHandler("soun"));
        }
        return box("moov", payload);
    }

    private static byte[] trakWithHandler(String handler) {
        return box("trak", box("mdia", hdlr(handler)));
    }

    private static byte[] hdlr(String handlerType) {
        ByteBuffer payload = ByteBuffer.allocate(12);
        payload.putInt(0); // version/flags
        payload.putInt(0); // pre_defined
        payload.put(handlerType.getBytes(StandardCharsets.US_ASCII)); // handler_type (4 bytes)
        return box("hdlr", payload.array());
    }

    private static byte[] box(String type, byte[] payload) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        if (typeBytes.length != 4) throw new IllegalArgumentException("type은 4글자여야 합니다: " + type);
        int size = 8 + (payload == null ? 0 : payload.length);

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(size);
        buf.put(typeBytes);
        if (payload != null) buf.put(payload);
        return buf.array();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        int aLen = a == null ? 0 : a.length;
        int bLen = b == null ? 0 : b.length;
        byte[] out = new byte[aLen + bLen];
        if (aLen > 0) System.arraycopy(a, 0, out, 0, aLen);
        if (bLen > 0) System.arraycopy(b, 0, out, aLen, bLen);
        return out;
    }
}

