param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\\..')).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Path([string]$Path, [string]$Message) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw $Message
    }
}

function Write-Utf8NoBom([string]$Path, [string[]]$Lines) {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8NoBom)
}

function Replace-GeneratedBlock([string]$Path, [string[]]$NewLines) {
    # 왜: Windows PowerShell 5.x는 UTF-8(BOM 없음) 파일을 기본 인코딩(cp949 등)으로 읽어서 한글이 깨질 수 있습니다.
    #     문서 파일은 UTF-8로 강제해서 읽고/쓰기 합니다.
    $utf8Bom = New-Object System.Text.UTF8Encoding($true)
    $content = [System.IO.File]::ReadAllText($Path, $utf8Bom)
    $start = '<!-- @generated:start -->'
    $end = '<!-- @generated:end -->'

    $startIdx = $content.IndexOf($start)
    $endIdx = $content.IndexOf($end)
    if ($startIdx -lt 0 -or $endIdx -lt 0 -or $endIdx -le $startIdx) {
        throw "생성 블록 마커를 찾지 못했습니다: $Path"
    }

    $before = $content.Substring(0, $startIdx + $start.Length)
    $after = $content.Substring($endIdx)
    $middle = "`r`n" + ($NewLines -join "`r`n") + "`r`n"
    $updated = $before + $middle + $after

    [System.IO.File]::WriteAllText($Path, $updated, $utf8Bom)
}

Assert-Path $RepoRoot "저장소 루트 경로를 찾지 못했습니다: $RepoRoot"

$docsRpg = Join-Path $RepoRoot 'docs\\rpg'
$genDir = Join-Path $docsRpg 'generated'
New-Item -ItemType Directory -Force -Path $genDir | Out-Null

Assert-Path (Join-Path $RepoRoot 'public_html') 'public_html 폴더가 없습니다. (Resin root-directory 기준 폴더가 필요합니다.)'
Assert-Path (Join-Path $RepoRoot 'src\\dao') 'src/dao 폴더가 없습니다. (DAO 테이블 매핑 생성에 필요합니다.)'

Write-Host "[RPG] 스캔 시작: $RepoRoot"

Push-Location $RepoRoot
try {

# ----------------------------
# 1) 간단 규모/구성 요약(전체 범위)
# ----------------------------
$allJspCount = (& rg --files --glob '*.jsp' --glob '!**/node_modules/**' --glob '!**/out/**' | Measure-Object).Count
$publicJspCount = (& rg --files --glob 'public_html/**/*.jsp' | Measure-Object).Count
$sysopJspCount = (& rg --files --glob 'public_html/sysop/**/*.jsp' | Measure-Object).Count
$apiJspCount = (& rg --files --glob 'public_html/api/**/*.jsp' | Measure-Object).Count
$templateHtmlCount = (& rg --files --glob 'public_html/**/html/**/*.html' | Measure-Object).Count
$daoCount = (& rg --files --glob 'src/dao/*.java' | Measure-Object).Count
$polytechJavaCount = (& rg --files --glob 'polytech-lms-api/src/**/*.java' | Measure-Object).Count
$projectFileCount = (& rg --files --glob 'project/**/*' --glob '!project/node_modules/**' | Measure-Object).Count

# ----------------------------
# 2) JSP -> setBody 인덱스(실제 코드 기반)
# ----------------------------
$jspBodyLines = & rg -n --no-heading --glob '*.jsp' --glob '!**/node_modules/**' --glob '!**/out/**' 'p\.setBody\(' 2>$null
$jspBodyRows = New-Object System.Collections.Generic.List[string]
$jspBodyRows.Add("jsp`tline`tbody`tcandidate_sysop_html`tcandidate_front_html`texists_sysop`texists_front")

foreach ($raw in $jspBodyLines) {
    if ($raw -match '^(?<path>[^:]+):(?<line>\d+):.*p\.setBody\("(?<body>[^"]+)"\)') {
        $jspPath = $Matches['path']
        $lineNo = $Matches['line']
        $body = $Matches['body']
        $keyPath = ($body -replace '\.', '/')

        $candSysop = ("public_html/sysop/html/{0}.html" -f $keyPath) -replace '/', '\\'
        $candFront = ("public_html/html/{0}.html" -f $keyPath) -replace '/', '\\'

        $existsSysop = Test-Path -LiteralPath (Join-Path $RepoRoot $candSysop)
        $existsFront = Test-Path -LiteralPath (Join-Path $RepoRoot $candFront)

        $jspBodyRows.Add(("{0}`t{1}`t{2}`t{3}`t{4}`t{5}`t{6}" -f $jspPath, $lineNo, $body, $candSysop, $candFront, $existsSysop, $existsFront))
    }
}

$jspBodyIndexPath = Join-Path $genDir 'jsp_setBody_index.tsv'
Write-Utf8NoBom $jspBodyIndexPath $jspBodyRows

# ----------------------------
# 3) JSP -> new XxxDao 인덱스(실제 코드 기반)
# ----------------------------
$jspDaoLines = & rg -n --no-heading --pcre2 --glob '*.jsp' --glob '!**/node_modules/**' --glob '!**/out/**' '\bnew\s+(?<dao>[A-Z][A-Za-z0-9_]*Dao)\b' 2>$null
$jspDaoRows = New-Object System.Collections.Generic.List[string]
$jspDaoRows.Add("jsp`tline`tdao")
foreach ($raw in $jspDaoLines) {
    if ($raw -match '^(?<path>[^:]+):(?<line>\d+):.*\bnew\s+(?<dao>[A-Z][A-Za-z0-9_]*Dao)\b') {
        $jspDaoRows.Add(("{0}`t{1}`t{2}" -f $Matches['path'], $Matches['line'], $Matches['dao']))
    }
}
$jspDaoIndexPath = Join-Path $genDir 'jsp_newDao_index.tsv'
Write-Utf8NoBom $jspDaoIndexPath $jspDaoRows

# ----------------------------
# 4) DAO -> table/PK 인덱스(실제 코드 기반)
# ----------------------------
$daoFiles = Get-ChildItem -LiteralPath (Join-Path $RepoRoot 'src\\dao') -Filter '*.java' -File
$daoRows = New-Object System.Collections.Generic.List[string]
$daoRows.Add("dao_file`tdao_class`ttable`tpk`tnote")

foreach ($file in $daoFiles) {
    $text = Get-Content -LiteralPath $file.FullName -Raw
    $daoClass = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)

    $table = $null
    $pk = $null
    if ($text -match '(?m)^\s*(?:this\.)?table\s*=\s*"(?<t>[^"]+)"') { $table = $Matches['t'] }
    if ($text -match '(?m)^\s*(?:this\.)?PK\s*=\s*"(?<p>[^"]+)"') { $pk = $Matches['p'] }

    $note = @()
    if (-not $table) { $note += 'table 미발견' }
    if (-not $pk) { $note += 'PK 미발견(또는 DataObject 기본값 사용)' }
    $noteText = ($note -join ', ')

    $rel = $file.FullName.Substring($RepoRoot.Length).TrimStart('\\')
    $tableText = $(if ($null -ne $table) { $table } else { '' })
    $pkText = $(if ($null -ne $pk) { $pk } else { '' })
    $daoRows.Add(("{0}`t{1}`t{2}`t{3}`t{4}" -f $rel, $daoClass, $tableText, $pkText, $noteText))
}

$daoIndexPath = Join-Path $genDir 'dao_table_index.tsv'
Write-Utf8NoBom $daoIndexPath $daoRows

# ----------------------------
# 5) polytech-lms-api (Spring Boot) 엔드포인트 후보 인덱스
# ----------------------------
$polytechCtrlLines = & rg -n --no-heading --pcre2 --glob 'polytech-lms-api/src/main/java/**/*.java' '@(Get|Post|Put|Delete|Patch)Mapping\(|@RequestMapping\(' 2>$null
$polytechRows = New-Object System.Collections.Generic.List[string]
$polytechRows.Add("file`tline`tannotation_line")
foreach ($raw in $polytechCtrlLines) {
    if ($raw -match '^(?<path>[^:]+):(?<line>\d+):(?<rest>.*)$') {
        $polytechRows.Add(("{0}`t{1}`t{2}" -f $Matches['path'], $Matches['line'], $Matches['rest'].Trim()))
    }
}
$polytechIndexPath = Join-Path $genDir 'polytech_controller_mapping_candidates.tsv'
Write-Utf8NoBom $polytechIndexPath $polytechRows

# ----------------------------
# 6) docs/rpg 요약 블록 갱신
# ----------------------------
$today = Get-Date -Format 'yyyy-MM-dd HH:mm'
$summary = @(
    "",
    "최근 자동 갱신: $today",
    "",
    "- JSP 총합(전체): $allJspCount",
    "- JSP(public_html): $publicJspCount (sysop: $sysopJspCount, api: $apiJspCount)",
    "- 템플릿 HTML(public_html/**/html): $templateHtmlCount",
    "- DAO(src/dao): $daoCount",
    "- React(Vite) 프로젝트 파일 수(project, node_modules 제외): $projectFileCount",
    "- polytech-lms-api(Java/Spring Boot) Java 파일 수: $polytechJavaCount",
    "",
    "생성된 인덱스:",
    "- docs/rpg/generated/jsp_setBody_index.tsv",
    "- docs/rpg/generated/jsp_newDao_index.tsv",
    "- docs/rpg/generated/dao_table_index.tsv",
    "- docs/rpg/generated/polytech_controller_mapping_candidates.tsv"
)

Replace-GeneratedBlock (Join-Path $docsRpg 'map.md') $summary
Replace-GeneratedBlock (Join-Path $docsRpg 'flows.md') @(
    "",
    "최근 자동 갱신: $today",
    "",
    "- Resin root-directory: resin/resin.xml → public_html",
    "- React 빌드 산출물: project/vite.config.ts → public_html/tutor_lms/app",
    "- Spring Boot API: polytech-lms-api/build.gradle (Boot 3.2.5, Java 17)"
)
Replace-GeneratedBlock (Join-Path $docsRpg 'hotspots.md') @(
    "",
    "최근 자동 갱신: $today",
    "",
    "- Resin 설정: resin/resin.xml (root-directory=public_html)",
    "- React 배포: public_html/tutor_lms/app (project 빌드 산출물)",
    "- Spring Boot 설정: polytech-lms-api/src/main/resources/application.yml, application-local.yml",
    "- Spring Boot DB/외부연동: polytech-lms-api/build.gradle 의존성(JPA/MySQL/Google/Spring AI 등)"
)

Write-Host "[RPG] 완료"
Write-Host " - $jspBodyIndexPath"
Write-Host " - $jspDaoIndexPath"
Write-Host " - $daoIndexPath"
Write-Host " - $polytechIndexPath"

}
finally {
    Pop-Location
}





