param(
    [switch]$Stage
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 왜: “커밋 여부”와 무관하게, 작업을 끝내기 직전에 RPG 문서/인덱스를 항상 최신으로 맞추기 위한 마무리 스크립트입니다.
#     (에이전트가 작업 종료 직전에 강제로 실행하는 용도 + 사람이 수동으로 실행하는 용도)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path

Write-Host "[RPG] finish: 생성 스크립트 실행"
powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'tools\\rpg\\generate.ps1')

if ($Stage) {
    try {
        Write-Host "[RPG] finish: docs/rpg 자동 스테이징"
        git add (Join-Path $repoRoot 'docs\\rpg') (Join-Path $repoRoot 'docs\\rpg\\generated') | Out-Null
    } catch {
        Write-Host "[RPG] finish: git add 실패(무시). git 환경이 아니거나 경로 문제일 수 있습니다."
    }
}

Write-Host "[RPG] finish: 완료"


