Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 왜: Git hook은 기본적으로 .git/hooks 아래를 보는데, 저장소에 포함되지 않아 팀 공유가 어렵습니다.
#     core.hooksPath를 .githooks로 바꾸면, 저장소에 훅을 넣어 "강제 규칙"을 공유할 수 있습니다.

try {
    git rev-parse --is-inside-work-tree | Out-Null
} catch {
    throw "현재 폴더는 git 저장소가 아닙니다. 저장소 루트에서 실행해주세요."
}

git config core.hooksPath .githooks

Write-Host "[RPG] 완료: git hooks 경로를 .githooks로 설정했습니다."
Write-Host " - 확인: git config --get core.hooksPath"
Write-Host " - 적용 훅: .githooks/pre-commit"

