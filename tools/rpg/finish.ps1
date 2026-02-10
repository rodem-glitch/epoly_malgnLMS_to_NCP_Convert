param(
    [switch]$Stage,
    [switch]$Full
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# 왜: “커밋 여부”와 무관하게, 작업을 끝내기 직전에 RPG 문서를 최신으로 맞추기 위한 마무리 스크립트입니다.
#     기본은 빠른 모드(SummaryOnly: 문서의 @generated 블록만 갱신)로 실행해 시간/변경량을 줄이고,
#     필요할 때만 -Full로 전체 인덱스(tsv)까지 재생성합니다.

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
$genScript = (Join-Path $repoRoot 'tools\\rpg\\generate.ps1')

if ($Full) {
    Write-Host '[RPG] finish: 생성 스크립트 실행(Full)'
    powershell -NoProfile -ExecutionPolicy Bypass -File $genScript
} else {
    Write-Host '[RPG] finish: 생성 스크립트 실행(SummaryOnly)'
    powershell -NoProfile -ExecutionPolicy Bypass -File $genScript -SummaryOnly
}

if ($Stage) {
    try {
        Write-Host '[RPG] finish: docs/rpg 자동 스테이징'
        git add (Join-Path $repoRoot 'docs\\rpg') (Join-Path $repoRoot 'docs\\rpg\\generated') | Out-Null
    } catch {
        Write-Host '[RPG] finish: git add 실패(무시). git 환경이 아니거나 경로 문제일 수 있습니다.'
    }
}

Write-Host '[RPG] finish: 완료'