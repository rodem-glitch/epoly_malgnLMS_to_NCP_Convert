package kr.go.growailms.statistics.util;

import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class StatisticsFileLocator {
    // 왜: 로컬 실행(IDE/Gradle/배치) 방식에 따라 작업 폴더(user.dir)가 달라지면,
    //     설정에 상대경로(예: ../통계/...)가 들어있는 경우 파일을 못 찾는 문제가 자주 발생합니다.
    //     통계 기능은 엑셀 파일 의존도가 높아서, 이 문제를 자동 보정해 개발/운영 안정성을 올립니다.

    private StatisticsFileLocator() {
    }

    public static Optional<Path> findStatisticsDirectory() {
        // 왜: 저장소 루트에 `통계/` 폴더가 있고, 실행 위치가 달라져도 상위 폴더에서 찾을 수 있게 합니다.
        Path base = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && base != null; i++) {
            Path candidate = base.resolve("통계");
            if (Files.exists(candidate) && Files.isDirectory(candidate)) {
                return Optional.of(candidate);
            }
            base = base.getParent();
        }
        return Optional.empty();
    }

    public static Optional<Path> tryResolve(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return Optional.empty();
        }

        Path direct = Path.of(filePath);
        if (Files.exists(direct)) {
            return Optional.of(direct);
        }

        // 왜: 설정값이 상대경로일 때, 작업 폴더가 달라져도 "통계 폴더에 같은 파일명"이 있으면 자동으로 찾습니다.
        Path fileName = direct.getFileName();
        if (fileName == null) {
            return Optional.empty();
        }

        return findStatisticsDirectory()
                .map(dir -> dir.resolve(fileName.toString()))
                .filter(Files::exists);
    }
}

