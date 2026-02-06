package kr.go.growailms.contentsummary.client;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * MP4 컨테이너 안에 "video 트랙이 있는지"를 아주 가볍게 확인합니다.
 *
 * 왜 필요하나요?
 * - Kollus에서 내려받은 MP4가 "검은 화면 + 오디오"처럼 보이는데, 실제로는 video 트랙이 없는 오디오-only MP4인 경우가 있습니다.
 * - 이런 파일을 Gemini에 video/mp4로 올리면 File API 활성화(STATE=ACTIVE) 단계에서 실패/타임아웃이 날 수 있어서,
 *   audio/mp4로 분기하기 위해 트랙 종류를 확인합니다.
 *
 * 주의:
 * - 완전한 MP4 파서가 아니라, 'hdlr' 박스의 handler_type(vide/soun)만 훑는 최소 구현입니다.
 * - 파싱에 실패하면 hasVideo/hasAudio를 모두 false로 반환합니다(호출부에서 기본값 처리).
 */
final class Mp4TrackInspector {

    record TrackTypes(boolean hasVideo, boolean hasAudio) {
    }

    private Mp4TrackInspector() {
    }

    static TrackTypes inspect(Path mp4File) {
        if (mp4File == null) return new TrackTypes(false, false);

        boolean hasVideo = false;
        boolean hasAudio = false;

        try (FileChannel ch = FileChannel.open(mp4File, StandardOpenOption.READ)) {
            long fileSize = ch.size();
            Deque<Long> endStack = new ArrayDeque<>();
            endStack.push(fileSize);

            while (!endStack.isEmpty()) {
                long end = endStack.peek();
                long pos = ch.position();

                if (pos >= end) {
                    endStack.pop();
                    continue;
                }

                if (end - pos < 8) {
                    // 왜: 박스 헤더(8바이트)조차 못 읽으면 더 진행해도 의미가 없습니다.
                    break;
                }

                long boxStart = pos;
                BoxHeader header = readBoxHeader(ch, end);
                if (header == null) break;

                String type = header.type();
                long boxEnd = header.end();

                // 컨테이너 박스면 하위로 내려갑니다.
                if (isContainer(type)) {
                    endStack.push(boxEnd);
                    continue;
                }

                if ("hdlr".equals(type)) {
                    // hdlr payload: version/flags(4) + pre_defined(4) + handler_type(4) ...
                    if (boxEnd - ch.position() >= 12) {
                        ByteBuffer buf = ByteBuffer.allocate(12);
                        readFully(ch, buf);
                        buf.flip();
                        buf.getInt(); // version/flags
                        buf.getInt(); // pre_defined
                        String handler = intToFourCC(buf.getInt());

                        if ("vide".equalsIgnoreCase(handler)) hasVideo = true;
                        if ("soun".equalsIgnoreCase(handler)) hasAudio = true;

                        // 둘 다 찾았으면 조기 종료합니다.
                        if (hasVideo && hasAudio) return new TrackTypes(true, true);
                    }
                }

                // 다음 박스로 이동
                ch.position(boxEnd);

                // 왜: boxEnd가 역행하면 무한 루프 위험이 있어 탈출합니다.
                if (boxEnd <= boxStart) break;
            }
        } catch (Exception ignored) {
            return new TrackTypes(false, false);
        }

        return new TrackTypes(hasVideo, hasAudio);
    }

    private static boolean isContainer(String type) {
        // 왜: 필요한 최소 범위의 컨테이너 박스만 포함합니다.
        return "moov".equals(type)
            || "trak".equals(type)
            || "mdia".equals(type)
            || "minf".equals(type)
            || "stbl".equals(type)
            || "edts".equals(type)
            || "dinf".equals(type)
            || "udta".equals(type)
            || "meta".equals(type)
            || "moof".equals(type)
            || "traf".equals(type);
    }

    private record BoxHeader(String type, long end) {
    }

    private static BoxHeader readBoxHeader(FileChannel ch, long parentEnd) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(8);
        readFully(ch, header);
        header.flip();

        long size32 = Integer.toUnsignedLong(header.getInt());
        String type = intToFourCC(header.getInt());

        long headerSize = 8;
        long size = size32;

        if (size32 == 1) {
            // large size
            if (parentEnd - ch.position() < 8) return null;
            ByteBuffer large = ByteBuffer.allocate(8);
            readFully(ch, large);
            large.flip();
            size = large.getLong();
            headerSize = 16;
        } else if (size32 == 0) {
            // box extends to end of parent
            size = parentEnd - (ch.position() - 8);
        }

        long boxStart = ch.position() - headerSize;
        if (size < headerSize) return null;

        long boxEnd = boxStart + size;
        if (boxEnd > parentEnd) {
            // 왜: 비정상/손상 파일 방어
            boxEnd = parentEnd;
        }

        return new BoxHeader(type, boxEnd);
    }

    private static void readFully(FileChannel ch, ByteBuffer buf) throws Exception {
        while (buf.hasRemaining()) {
            int n = ch.read(buf);
            if (n < 0) throw new IllegalStateException("Unexpected EOF");
        }
    }

    private static String intToFourCC(int v) {
        byte[] b = new byte[] {
            (byte) ((v >> 24) & 0xFF),
            (byte) ((v >> 16) & 0xFF),
            (byte) ((v >> 8) & 0xFF),
            (byte) (v & 0xFF)
        };
        return new String(b, StandardCharsets.US_ASCII);
    }
}

