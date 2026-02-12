[CmdletBinding()]
param(
    [string]$ProjectId = "",
    [string]$BillingAccountId = "",
    [string]$Region = "asia-northeast3",
    [string]$Zone = "asia-northeast3-a",
    [string]$VmName = "polytech-lms-vm",
    [string]$VmSshUser = "",
    [string]$MachineType = "e2-standard-4",
    [string]$ApiDomain = "api.example.com",
    [string]$WwwDomain = "www.example.com",
    [string]$FirebaseSite = "",
    [string]$DbName = "lms",
    [string]$DbUser = "lms",
    [string]$DbPassword = "",
    [string]$DbRootPassword = "",
    [string]$QdrantApiKey = "",
    [string]$QdrantCollection = "video_summary_vectors_gemini",
    [string]$GoogleApiKey = "",
    [string]$GeminiApiKey = "",
    [string]$KosisConsumerKey = "",
    [string]$KosisConsumerSecret = "",
    [string]$Work24AuthKey = "",
    [string]$JobkoreaApiKey = "",
    [string]$JobkoreaOemCode = "",
    [string]$StatisticsAiApiKey = "",
    [string]$KollusAccessToken = "",
    [string]$KollusSecurityKey = "",
    [string]$KollusChannelKey = "",
    [string]$KollusClientUserId = "contentsummary",
    [string]$LetsEncryptEmail = "",
    [switch]$EnableDbMigration,
    [string]$SourceDbDumpPath = "",
    [string]$SourceDbHost = "",
    [int]$SourceDbPort = 3306,
    [string]$SourceDbName = "",
    [string]$SourceDbUser = "",
    [string]$SourceDbPassword = "",
    [switch]$SkipProjectBootstrap,
    [switch]$SkipVmProvision,
    [switch]$SkipFirebaseDeploy,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:DryRun = [bool]$DryRun
$script:ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$script:RepoRoot = Resolve-Path (Join-Path $script:ScriptDir "..\..")
$script:TemplateDir = Join-Path $script:ScriptDir "templates"
$script:GeneratedDir = Join-Path $script:ScriptDir "generated"
$script:ProjectId = $ProjectId
$script:FirebaseSite = $FirebaseSite
$script:DbPassword = $DbPassword
$script:DbRootPassword = $DbRootPassword
$script:QdrantApiKey = $QdrantApiKey
$script:GoogleApiKey = $GoogleApiKey
$script:GeminiApiKey = $GeminiApiKey
$script:KosisConsumerKey = $KosisConsumerKey
$script:KosisConsumerSecret = $KosisConsumerSecret
$script:Work24AuthKey = $Work24AuthKey
$script:JobkoreaApiKey = $JobkoreaApiKey
$script:JobkoreaOemCode = $JobkoreaOemCode
$script:StatisticsAiApiKey = $StatisticsAiApiKey
$script:KollusAccessToken = $KollusAccessToken
$script:KollusSecurityKey = $KollusSecurityKey
$script:KollusChannelKey = $KollusChannelKey
$script:KollusClientUserId = $KollusClientUserId
$script:LetsEncryptEmail = $LetsEncryptEmail
$script:EnableDbMigration = [bool]$EnableDbMigration
$script:SourceDbDumpPath = $SourceDbDumpPath
$script:SourceDbHost = $SourceDbHost
$script:SourceDbPort = $SourceDbPort
$script:SourceDbName = $SourceDbName
$script:SourceDbUser = $SourceDbUser
$script:SourceDbPassword = $SourceDbPassword
$script:DbMigrationStatus = "미실행"
$script:DbMigrationDumpPath = ""

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "[STEP] $Message" -ForegroundColor Cyan
}

function Write-Info {
    param([string]$Message)
    Write-Host "  - $Message"
}

function Test-IsWindowsPlatform {
    try {
        return [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)
    } catch {
        return ($env:OS -eq "Windows_NT")
    }
}

function Resolve-CliCommand {
    param([string]$CommandName)
    switch ($CommandName.ToLowerInvariant()) {
        "gcloud" {
            if (Get-Command "gcloud.cmd" -ErrorAction SilentlyContinue) { return "gcloud.cmd" }
            return "gcloud"
        }
        "firebase" {
            if (Get-Command "firebase.cmd" -ErrorAction SilentlyContinue) { return "firebase.cmd" }
            return "firebase"
        }
        default { return $CommandName }
    }
}

function Get-FirebaseAuthArgs {
    $token = $env:FIREBASE_TOKEN
    if ([string]::IsNullOrWhiteSpace($token)) {
        return @()
    }
    # 왜: GitHub Actions/CI에서는 브라우저 로그인 대신 토큰 인증이 필요합니다.
    return @("--token", $token)
}

function Require-Tool {
    param([string]$CommandName)
    $resolved = Resolve-CliCommand -CommandName $CommandName
    if ($script:DryRun) {
        Write-Info "DryRun: 도구 확인 생략 ($resolved)"
        return
    }
    if (-not (Get-Command $resolved -ErrorAction SilentlyContinue)) {
        throw "필수 도구 '$resolved' 을(를) 찾지 못했습니다. 먼저 설치해 주세요."
    }
}

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @(),
        [switch]$Interactive,
        [switch]$Sensitive
    )

    $resolvedCommand = Resolve-CliCommand -CommandName $Command
    $display = if ($Sensitive) { "<민감정보 포함: 출력 생략>" } else { ($Arguments -join " ") }
    Write-Info "실행: $resolvedCommand $display"

    if ($script:DryRun) {
        return ""
    }

    if ($Interactive) {
        $previousErrorActionPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = "Continue"
            & $resolvedCommand @Arguments
            if ($LASTEXITCODE -ne 0) {
                throw "명령 실행 실패: $resolvedCommand $($Arguments -join ' ')"
            }
            return ""
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
        }
    }

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $resolvedCommand @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        $message = ($output | Out-String).Trim()
        throw "명령 실행 실패: $resolvedCommand $($Arguments -join ' ')`n$message"
    }
    return ($output -join "`n")
}

function Read-RequiredValue {
    param([string]$Prompt)
    while ($true) {
        $value = Read-Host $Prompt
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
        Write-Host "값을 입력해 주세요." -ForegroundColor Yellow
    }
}

function New-RandomSecret {
    param([int]$Length = 32)
    $chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%_-"
    $bytes = New-Object byte[] ($Length)
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $buffer = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) {
        [void]$buffer.Append($chars[$b % $chars.Length])
    }
    return $buffer.ToString()
}

function Get-FirstIpv4 {
    param([string]$Text)

    $raw = ($Text | Out-String)
    $match = [regex]::Match($raw, "(?<!\d)(?:\d{1,3}\.){3}\d{1,3}(?!\d)")
    if (-not $match.Success) {
        throw "IPv4 값을 추출하지 못했습니다. 원문: $raw"
    }
    return $match.Value
}

function Ensure-Project {
    param(
        [string]$TargetProjectId,
        [string]$TargetBillingAccountId
    )

    Write-Step "GCP 프로젝트 확인"

    $exists = $true
    try {
        [void](Invoke-Checked -Command "gcloud" -Arguments @("projects", "describe", $TargetProjectId, "--format=value(projectId)"))
    } catch {
        $exists = $false
    }

    if (-not $exists) {
        Write-Info "프로젝트가 없어 자동 생성을 시도합니다."
        Invoke-Checked -Command "gcloud" -Arguments @("projects", "create", $TargetProjectId, "--name", $TargetProjectId)
    }

    $billingEnabled = "false"
    try {
        $billingEnabled = Invoke-Checked -Command "gcloud" -Arguments @("beta", "billing", "projects", "describe", $TargetProjectId, "--format=value(billingEnabled)")
    } catch {
        $billingEnabled = "false"
    }
    $billingEnabledText = ($billingEnabled | Out-String).Trim()
    $isBillingEnabled = $billingEnabledText -match "(?i)\btrue\b"

    if (-not $isBillingEnabled) {
        Write-Info "프로젝트 결제 연결을 확인/적용합니다."
        $billingId = $TargetBillingAccountId
        if ([string]::IsNullOrWhiteSpace($billingId)) {
            $accountsRaw = Invoke-Checked -Command "gcloud" -Arguments @("billing", "accounts", "list", "--filter=open=true", "--format=value(name)")
            $accounts = @(
                $accountsRaw -split "`r?`n" |
                ForEach-Object { $_.Trim() } |
                Where-Object { $_ -match "^billingAccounts/" }
            )
            if ($accounts.Count -eq 1) {
                $billingId = $accounts[0]
                Write-Info "열린 결제계정 1개를 자동 선택했습니다: $billingId"
            } elseif ($accounts.Count -gt 1) {
                throw "열린 결제계정이 여러 개입니다. -BillingAccountId 파라미터로 지정해 주세요."
            } else {
                throw "열린 결제계정이 없습니다. 결제계정을 먼저 생성/연결해 주세요."
            }
        }

        Invoke-Checked -Command "gcloud" -Arguments @("beta", "billing", "projects", "link", $TargetProjectId, "--billing-account", $billingId)
    } else {
        Write-Info "프로젝트 결제 연결이 이미 활성화되어 있습니다."
    }

    Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "project", $TargetProjectId)
    Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "compute/region", $Region)
    Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "compute/zone", $Zone)
}

function Ensure-EnabledApis {
    Write-Step "필수 API 활성화"
    $apis = @(
        "compute.googleapis.com",
        "cloudresourcemanager.googleapis.com",
        "iam.googleapis.com",
        "firebase.googleapis.com",
        "firebasehosting.googleapis.com",
        "dns.googleapis.com"
    )
    Invoke-Checked -Command "gcloud" -Arguments (@("services", "enable") + $apis)
}

function Ensure-FirewallRules {
    Write-Step "방화벽 규칙 확인"
    $ruleName = "polytech-lms-allow-web"
    $exists = $true
    try {
        [void](Invoke-Checked -Command "gcloud" -Arguments @("compute", "firewall-rules", "describe", $ruleName, "--format=value(name)"))
    } catch {
        $exists = $false
    }

    if (-not $exists) {
        Invoke-Checked -Command "gcloud" -Arguments @(
            "compute", "firewall-rules", "create", $ruleName,
            "--allow", "tcp:80,tcp:443",
            "--target-tags", "polytech-lms-web",
            "--description", "polytech lms web access"
        )
    } else {
        Write-Info "기존 규칙을 그대로 사용합니다: $ruleName"
    }
}

function Ensure-StaticIp {
    param([string]$AddressName)

    Write-Step "고정 IP 확인"
    if ($script:DryRun) {
        Write-Info "DryRun: 고정 IP 확인/생성 생략"
        return "203.0.113.10"
    }

    $address = ""
    $exists = $true
    try {
        $address = Invoke-Checked -Command "gcloud" -Arguments @("compute", "addresses", "describe", $AddressName, "--region", $Region, "--format=value(address)")
    } catch {
        $exists = $false
    }

    if (-not $exists) {
        Invoke-Checked -Command "gcloud" -Arguments @("compute", "addresses", "create", $AddressName, "--region", $Region)
        $address = Invoke-Checked -Command "gcloud" -Arguments @("compute", "addresses", "describe", $AddressName, "--region", $Region, "--format=value(address)")
    }

    return Get-FirstIpv4 -Text $address
}

function Ensure-Vm {
    param(
        [string]$VmInstanceName,
        [string]$VmAddress
    )

    Write-Step "Linux VM 확인"
    if ($script:DryRun) {
        Write-Info "DryRun: VM 확인/생성 생략"
        return $VmAddress
    }

    $exists = $true
    try {
        [void](Invoke-Checked -Command "gcloud" -Arguments @("compute", "instances", "describe", $VmInstanceName, "--zone", $Zone, "--format=value(name)"))
    } catch {
        $exists = $false
    }

    if (-not $exists) {
        Invoke-Checked -Command "gcloud" -Arguments @(
            "compute", "instances", "create", $VmInstanceName,
            "--zone", $Zone,
            "--machine-type", $MachineType,
            "--image-family", "ubuntu-2204-lts",
            "--image-project", "ubuntu-os-cloud",
            "--boot-disk-size", "80GB",
            "--tags", "polytech-lms-web",
            "--address", $VmAddress
        )
    } else {
        Write-Info "기존 VM을 그대로 사용합니다: $VmInstanceName"
    }

    $natIp = Invoke-Checked -Command "gcloud" -Arguments @(
        "compute", "instances", "describe", $VmInstanceName,
        "--zone", $Zone,
        "--format=value(networkInterfaces[0].accessConfigs[0].natIP)"
    )
    return Get-FirstIpv4 -Text $natIp
}

function Build-ApiJar {
    Write-Step "Spring API JAR 빌드"
    if ($script:DryRun) {
        $dummyJar = Join-Path $script:ScriptDir "dryrun-polytech-lms-api.jar"
        Set-Content -Path $dummyJar -Value "dryrun" -Encoding ASCII
        Write-Info "DryRun: 더미 JAR 생성 ($dummyJar)"
        return $dummyJar
    }

    $apiDir = Join-Path $script:RepoRoot "polytech-lms-api"
    Push-Location $apiDir
    try {
        $isWindowsPlatform = $false
        try {
            $isWindowsPlatform = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)
        } catch {
            $isWindowsPlatform = ($env:OS -eq "Windows_NT")
        }
        if ($isWindowsPlatform -and (Test-Path ".\gradlew.bat")) {
            $null = Invoke-Checked -Command ".\gradlew.bat" -Arguments @("bootJar", "-x", "test")
        } else {
            # 왜: GitHub Actions(ubuntu)에서 gradlew 실행권한 비트가 없어도
            # bash로 실행하면 권한 오류 없이 빌드가 가능합니다.
            $null = Invoke-Checked -Command "bash" -Arguments @("./gradlew", "bootJar", "-x", "test")
        }
    } finally {
        Pop-Location
    }

    # 왜: GitHub Actions(ubuntu)에서도 동일하게 동작하도록, OS 경로 구분자에 의존하지 않는 경로를 사용합니다.
    $libsDir = Join-Path $apiDir "build/libs"
    $jar = Get-ChildItem -Path $libsDir -Filter "*.jar" |
        Where-Object { $_.Name -notmatch "plain" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jar) {
        throw "빌드 결과 JAR 파일을 찾지 못했습니다."
    }

    return $jar.FullName
}

function Resolve-DbMigrationDumpPath {
    if (-not $script:EnableDbMigration) {
        return ""
    }

    Write-Step "DB 이관 덤프 준비"

    if (-not [string]::IsNullOrWhiteSpace($script:SourceDbDumpPath)) {
        $resolved = Resolve-Path -Path $script:SourceDbDumpPath -ErrorAction Stop
        $script:DbMigrationStatus = "기존 덤프 사용"
        $script:DbMigrationDumpPath = $resolved.Path
        Write-Info "기존 dump 파일을 사용합니다: $($resolved.Path)"
        return $resolved.Path
    }

    if ($script:DryRun) {
        if (-not (Test-Path $script:GeneratedDir)) {
            New-Item -Path $script:GeneratedDir -ItemType Directory -Force | Out-Null
        }
        $dummyDump = Join-Path $script:GeneratedDir "dryrun-source-db.sql"
        $dummySql = @(
            "-- dryrun only"
            "CREATE TABLE IF NOT EXISTS dryrun_table (id INT PRIMARY KEY);"
        ) -join "`n"
        Set-Content -Path $dummyDump -Value $dummySql -Encoding ASCII
        $script:DbMigrationStatus = "DryRun(더미 dump 생성)"
        $script:DbMigrationDumpPath = $dummyDump
        Write-Info "DryRun: 더미 DB dump 생성 ($dummyDump)"
        return $dummyDump
    }

    Require-Tool "mysqldump"

    if ([string]::IsNullOrWhiteSpace($script:SourceDbHost)) { $script:SourceDbHost = Read-RequiredValue "소스 DB Host를 입력해 주세요" }
    if ($script:SourceDbPort -le 0) { $script:SourceDbPort = 3306 }
    if ([string]::IsNullOrWhiteSpace($script:SourceDbName)) { $script:SourceDbName = Read-RequiredValue "소스 DB 이름을 입력해 주세요" }
    if ([string]::IsNullOrWhiteSpace($script:SourceDbUser)) { $script:SourceDbUser = Read-RequiredValue "소스 DB 사용자명을 입력해 주세요" }
    if ([string]::IsNullOrWhiteSpace($script:SourceDbPassword)) { $script:SourceDbPassword = Read-RequiredValue "소스 DB 비밀번호를 입력해 주세요" }

    if (-not (Test-Path $script:GeneratedDir)) {
        New-Item -Path $script:GeneratedDir -ItemType Directory -Force | Out-Null
    }

    $dumpPath = Join-Path $script:GeneratedDir ("source-db-{0}.sql" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    $errPath = "$dumpPath.err"

    $oldMysqlPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $script:SourceDbPassword
    try {
        $args = @(
            "--single-transaction",
            "--quick",
            "--routines",
            "--triggers",
            "--events",
            # 왜: 운영 DB 계정에 PROCESS 권한이 없으면 tablespace 메타 덤프 단계에서 실패하므로 제외합니다.
            "--no-tablespaces",
            "--default-character-set=utf8mb4",
            "-h", $script:SourceDbHost,
            "-P", $script:SourceDbPort.ToString(),
            "-u", $script:SourceDbUser,
            $script:SourceDbName
        )
        Write-Info "실행: mysqldump <민감정보 포함: 출력 생략>"
        $proc = Start-Process -FilePath "mysqldump" -ArgumentList $args -NoNewWindow -PassThru -Wait -RedirectStandardOutput $dumpPath -RedirectStandardError $errPath
        if ($proc.ExitCode -ne 0) {
            $err = if (Test-Path $errPath) { Get-Content -Path $errPath -Raw } else { "mysqldump 실패(에러 로그 없음)" }
            throw "mysqldump 실패: $err"
        }
    } finally {
        if ($null -eq $oldMysqlPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
        else { $env:MYSQL_PWD = $oldMysqlPwd }
        if (Test-Path $errPath) { Remove-Item -Path $errPath -Force -ErrorAction SilentlyContinue }
    }

    if (-not (Test-Path $dumpPath)) {
        throw "DB dump 파일 생성에 실패했습니다: $dumpPath"
    }

    $dumpFile = Get-Item -Path $dumpPath
    if ($dumpFile.Length -le 0) {
        throw "DB dump 파일 크기가 0입니다: $dumpPath"
    }

    $script:DbMigrationStatus = "소스 DB dump 생성 완료"
    $script:DbMigrationDumpPath = $dumpPath
    Write-Info "DB dump 생성 완료: $dumpPath"
    return $dumpPath
}

function Split-SqlValueList {
    param([string]$RowText)

    $values = New-Object System.Collections.Generic.List[string]
    $buffer = New-Object System.Text.StringBuilder
    $inQuote = $false

    for ($i = 0; $i -lt $RowText.Length; $i++) {
        $ch = $RowText[$i]
        if ($ch -eq "'") {
            if ($inQuote -and $i + 1 -lt $RowText.Length -and $RowText[$i + 1] -eq "'") {
                # 왜: SQL 문자열 이스케이프('')는 실제 값 하나이므로 토큰 분리 기준으로 취급하지 않습니다.
                [void]$buffer.Append("''")
                $i++
                continue
            }
            $inQuote = -not $inQuote
            [void]$buffer.Append($ch)
            continue
        }

        if ((-not $inQuote) -and $ch -eq ",") {
            $values.Add($buffer.ToString().Trim())
            [void]$buffer.Clear()
            continue
        }

        [void]$buffer.Append($ch)
    }

    $values.Add($buffer.ToString().Trim())
    return ,$values.ToArray()
}

function Find-SqlStatementTerminator {
    param(
        [string]$Text,
        [int]$StartIndex
    )

    $inQuote = $false
    for ($i = $StartIndex; $i -lt $Text.Length; $i++) {
        $ch = $Text[$i]
        if ($ch -eq "'") {
            if ($inQuote -and $i + 1 -lt $Text.Length -and $Text[$i + 1] -eq "'") {
                $i++
                continue
            }
            $inQuote = -not $inQuote
            continue
        }

        if ((-not $inQuote) -and $ch -eq ";") {
            return $i
        }
    }

    throw "SQL 구문 종료 문자(;)를 찾지 못했습니다. StartIndex=$StartIndex"
}

function Split-SqlInsertRows {
    param([string]$ValuesBlock)

    $rows = New-Object System.Collections.Generic.List[string]
    $inQuote = $false
    $depth = 0
    $rowStart = -1

    for ($i = 0; $i -lt $ValuesBlock.Length; $i++) {
        $ch = $ValuesBlock[$i]

        if ($ch -eq "'") {
            if ($inQuote -and $i + 1 -lt $ValuesBlock.Length -and $ValuesBlock[$i + 1] -eq "'") {
                $i++
                continue
            }
            $inQuote = -not $inQuote
            continue
        }

        if ($inQuote) { continue }

        if ($ch -eq "(") {
            if ($depth -eq 0) {
                $rowStart = $i + 1
            }
            $depth++
            continue
        }

        if ($ch -eq ")") {
            if ($depth -le 0) {
                throw "INSERT VALUES 파싱 중 괄호 균형이 맞지 않습니다."
            }
            $depth--
            if ($depth -eq 0) {
                if ($rowStart -lt 0) {
                    throw "INSERT VALUES 파싱 중 row 시작점을 찾지 못했습니다."
                }
                $rows.Add($ValuesBlock.Substring($rowStart, $i - $rowStart))
                $rowStart = -1
            }
        }
    }

    if ($depth -ne 0) {
        throw "INSERT VALUES 파싱 중 괄호가 닫히지 않았습니다."
    }

    return ,$rows.ToArray()
}

function Normalize-LmCourseInsertStatement {
    param(
        [string]$StatementText,
        [int]$ExpectedColumnCount
    )

    $headerMatch = [regex]::Match($StatementText, '^\s*INSERT\s+INTO\s+`LM_COURSE`\s+VALUES\s*', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $headerMatch.Success) {
        throw "LM_COURSE INSERT 구문 헤더를 해석하지 못했습니다."
    }

    $header = $headerMatch.Value
    $valuesBlock = $StatementText.Substring($header.Length).Trim()
    if ($valuesBlock.EndsWith(";")) {
        $valuesBlock = $valuesBlock.Substring(0, $valuesBlock.Length - 1)
    }

    $rows = Split-SqlInsertRows -ValuesBlock $valuesBlock
    if ($rows.Count -eq 0) {
        throw "LM_COURSE INSERT에서 데이터 row를 찾지 못했습니다."
    }

    $normalizedRows = New-Object System.Collections.Generic.List[string]
    $changedCount = 0

    foreach ($row in $rows) {
        $values = Split-SqlValueList -RowText $row
        if ($values.Count -eq $ExpectedColumnCount) {
            $normalizedRows.Add("($row)")
            continue
        }

        $legacyColumnCount = $ExpectedColumnCount - 2
        if ($values.Count -ne $legacyColumnCount) {
            throw "LM_COURSE row 컬럼 수가 예상과 다릅니다. expected=$ExpectedColumnCount or legacy=$legacyColumnCount, actual=$($values.Count)"
        }

        if ($values.Count -lt 46) {
            throw "LM_COURSE row 값 수가 너무 작아 자동 보정할 수 없습니다. actual=$($values.Count)"
        }

        # 왜: 레거시 덤프(99컬럼)는 COMPLETE_LIMIT_* 2개가 빠져 있어
        # 기존 LIMIT_PROGRESS/LIMIT_TOTAL_SCORE 값을 복사해 동일 의미로 복원합니다.
        $completeLimitProgress = $values[40]
        $completeLimitTotalScore = $values[45]
        $prefix = $values[0..45]
        $suffix = @()
        if ($values.Count -gt 46) {
            $suffix = $values[46..($values.Count - 1)]
        }
        $merged = @($prefix + @($completeLimitProgress, $completeLimitTotalScore) + $suffix)
        $normalizedRows.Add("(" + ($merged -join ",") + ")")
        $changedCount++
    }

    $normalizedStatement = $header + ($normalizedRows -join ",") + ";"
    return @{
        Statement = $normalizedStatement
        ChangedRows = $changedCount
        TotalRows = $rows.Count
    }
}

function Normalize-LmCourseUserInsertStatement {
    param(
        [string]$StatementText,
        [int]$ExpectedColumnCount
    )

    $headerMatch = [regex]::Match($StatementText, '^\s*INSERT\s+INTO\s+`LM_COURSE_USER`\s+VALUES\s*', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $headerMatch.Success) {
        throw "LM_COURSE_USER INSERT 구문 헤더를 해석하지 못했습니다."
    }

    $header = $headerMatch.Value
    $valuesBlock = $StatementText.Substring($header.Length).Trim()
    if ($valuesBlock.EndsWith(";")) {
        $valuesBlock = $valuesBlock.Substring(0, $valuesBlock.Length - 1)
    }

    $rows = Split-SqlInsertRows -ValuesBlock $valuesBlock
    if ($rows.Count -eq 0) {
        throw "LM_COURSE_USER INSERT에서 데이터 row를 찾지 못했습니다."
    }

    $normalizedRows = New-Object System.Collections.Generic.List[string]
    $changedCount = 0

    foreach ($row in $rows) {
        $values = Split-SqlValueList -RowText $row
        if ($values.Count -eq $ExpectedColumnCount) {
            $normalizedRows.Add("($row)")
            continue
        }

        $legacyColumnCount = $ExpectedColumnCount - 1
        if ($values.Count -ne $legacyColumnCount) {
            throw "LM_COURSE_USER row 컬럼 수가 예상과 다릅니다. expected=$ExpectedColumnCount or legacy=$legacyColumnCount, actual=$($values.Count)"
        }

        if ($values.Count -lt 26) {
            throw "LM_COURSE_USER row 값 수가 너무 작아 자동 보정할 수 없습니다. actual=$($values.Count)"
        }

        # 왜: 레거시 덤프(36컬럼)는 COMPLETE_STATUS 1개가 빠져 있어
        # 현재 스키마(37컬럼)와 맞추기 위해 기본값('')을 삽입합니다.
        $prefix = $values[0..25]
        $suffix = @()
        if ($values.Count -gt 26) {
            $suffix = $values[26..($values.Count - 1)]
        }
        $merged = @($prefix + @("''") + $suffix)
        $normalizedRows.Add("(" + ($merged -join ",") + ")")
        $changedCount++
    }

    $normalizedStatement = $header + ($normalizedRows -join ",") + ";"
    return @{
        Statement = $normalizedStatement
        ChangedRows = $changedCount
        TotalRows = $rows.Count
    }
}

function Get-TableColumnCountFromSql {
    param(
        [string]$SqlText,
        [string]$TableName
    )

    $pattern = ('CREATE\s+TABLE\s+`{0}`\s*\((?<body>.*?)\)\s*ENGINE=' -f [regex]::Escape($TableName))
    $createMatch = [regex]::Match(
        $SqlText,
        $pattern,
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
    )
    if (-not $createMatch.Success) {
        throw "$TableName 테이블 정의를 찾지 못했습니다."
    }

    $tableBody = $createMatch.Groups["body"].Value
    $columnCount = [regex]::Matches($tableBody, '^\s*`[^`]+`\s', [System.Text.RegularExpressions.RegexOptions]::Multiline).Count
    if ($columnCount -le 0) {
        throw "$TableName 컬럼 수를 계산하지 못했습니다."
    }
    return $columnCount
}

function Normalize-TableInsertInSql {
    param(
        [string]$SqlText,
        [string]$TableName,
        [int]$ExpectedColumnCount
    )

    $marker = "INSERT INTO ``$TableName`` VALUES"
    $statementStart = $SqlText.IndexOf($marker, [System.StringComparison]::OrdinalIgnoreCase)
    if ($statementStart -lt 0) {
        return @{
            Sql = $SqlText
            ChangedRows = 0
            TotalRows = 0
            Found = $false
        }
    }

    $statementEnd = Find-SqlStatementTerminator -Text $SqlText -StartIndex $statementStart
    $statementText = $SqlText.Substring($statementStart, $statementEnd - $statementStart + 1)

    $normalized = switch ($TableName) {
        "LM_COURSE" { Normalize-LmCourseInsertStatement -StatementText $statementText -ExpectedColumnCount $ExpectedColumnCount; break }
        "LM_COURSE_USER" { Normalize-LmCourseUserInsertStatement -StatementText $statementText -ExpectedColumnCount $ExpectedColumnCount; break }
        default { throw "지원하지 않는 보정 대상 테이블입니다: $TableName" }
    }

    $patched = $SqlText.Substring(0, $statementStart) + $normalized.Statement + $SqlText.Substring($statementEnd + 1)
    return @{
        Sql = $patched
        ChangedRows = $normalized.ChangedRows
        TotalRows = $normalized.TotalRows
        Found = $true
    }
}

function Normalize-DbDumpIfNeeded {
    param([string]$DumpPath)

    if ([string]::IsNullOrWhiteSpace($DumpPath)) {
        return $DumpPath
    }
    if ($script:DryRun) {
        Write-Info "DryRun: DB dump 보정 로직을 건너뜁니다."
        return $DumpPath
    }
    if (-not (Test-Path $DumpPath)) {
        throw "DB dump 보정 대상 파일이 없습니다: $DumpPath"
    }

    $raw = [System.IO.File]::ReadAllText($DumpPath, [System.Text.Encoding]::UTF8)
    $targets = @("LM_COURSE", "LM_COURSE_USER")
    $patchedSql = $raw
    $totalChangedRows = 0

    foreach ($tableName in $targets) {
        $marker = "INSERT INTO ``$tableName`` VALUES"
        if ($patchedSql.IndexOf($marker, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
            Write-Info "$tableName INSERT 구문이 없어 보정을 건너뜁니다."
            continue
        }

        $columnCount = Get-TableColumnCountFromSql -SqlText $patchedSql -TableName $tableName
        $result = Normalize-TableInsertInSql -SqlText $patchedSql -TableName $tableName -ExpectedColumnCount $columnCount
        $patchedSql = $result.Sql

        if ($result.ChangedRows -gt 0) {
            Write-Info "$tableName dump 보정 완료: $($result.ChangedRows)/$($result.TotalRows) rows"
            $totalChangedRows += $result.ChangedRows
        } else {
            Write-Info "$tableName dump 보정 필요 없음(컬럼 수 일치)."
        }
    }

    $definerPattern = 'DEFINER=`[^`]+`@`[^`]+`\s+'
    $definerMatches = [regex]::Matches($patchedSql, $definerPattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($definerMatches.Count -gt 0) {
        # 왜: 로컬/사설IP 기반 DEFINER가 남아 있으면 Cloud VM MySQL에서 권한 오류(1227)로 import가 중단됩니다.
        $patchedSql = [regex]::Replace($patchedSql, $definerPattern, '', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        Write-Info "DB dump DEFINER 제거 완료: $($definerMatches.Count)건"
    }

    if ($totalChangedRows -le 0) {
        if ($definerMatches.Count -le 0) {
            Write-Info "DB dump 보정 필요 없음(대상 테이블 컬럼 수 일치)."
            return $DumpPath
        }
    }

    if ($totalChangedRows -le 0 -and $definerMatches.Count -gt 0) {
        Write-Info "테이블 컬럼 보정은 없고 DEFINER만 제거했습니다."
    }

    if ($totalChangedRows -le 0 -and $definerMatches.Count -le 0) {
        return $DumpPath
    }

    if (-not (Test-Path $script:GeneratedDir)) {
        New-Item -Path $script:GeneratedDir -ItemType Directory -Force | Out-Null
    }
    $originName = [System.IO.Path]::GetFileNameWithoutExtension($DumpPath)
    $normalizedPath = Join-Path $script:GeneratedDir ("{0}-normalized.sql" -f $originName)

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($normalizedPath, $patchedSql, $utf8NoBom)
    Write-Info "DB dump 보정 파일 생성 완료: $normalizedPath (총 보정 row=$totalChangedRows)"

    if ($script:DbMigrationStatus -eq "기존 덤프 사용") {
        $script:DbMigrationStatus = "기존 덤프 사용(보정 완료)"
    } elseif ($script:DbMigrationStatus -eq "소스 DB dump 생성 완료") {
        $script:DbMigrationStatus = "소스 DB dump 생성 완료(보정 완료)"
    }
    $script:DbMigrationDumpPath = $normalizedPath
    return $normalizedPath
}

function Render-Template {
    param(
        [string]$TemplatePath,
        [string]$OutputPath,
        [hashtable]$Values
    )

    $content = [System.IO.File]::ReadAllText($TemplatePath, [System.Text.Encoding]::UTF8)
    foreach ($key in $Values.Keys) {
        $token = "__${key}__"
        $content = $content.Replace($token, [string]$Values[$key])
    }
    $normalized = $content -replace "`r`n", "`n"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($OutputPath, $normalized, $utf8NoBom)
}

function Prepare-StackBundle {
    param(
        [string]$ApiJarPath,
        [string]$DbDumpPath = ""
    )

    Write-Step "VM 배포 번들 생성"

    if (-not (Test-Path $script:GeneratedDir)) {
        New-Item -Path $script:GeneratedDir -ItemType Directory -Force | Out-Null
    }

    # 왜: 동일 경로 재사용 시 이전 배포 프로세스가 파일 핸들을 잡고 있으면 삭제 실패가 발생할 수 있어
    # 매 실행마다 고유 폴더를 만들어 잠금 충돌 없이 진행합니다.
    $stackDir = Join-Path $script:GeneratedDir ("stack-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    $appDir = Join-Path $stackDir "app"
    $nginxDir = Join-Path $stackDir "nginx"
    $migrationDir = Join-Path $stackDir "migration"
    $legacyDir = Join-Path $stackDir "legacy"
    $legacyPublicDir = Join-Path $legacyDir "public_html"
    $legacySrcDir = Join-Path $legacyDir "src"
    $statisticsDir = Join-Path $stackDir "statistics_data"
    New-Item -Path $stackDir -ItemType Directory -Force | Out-Null
    New-Item -Path $appDir -ItemType Directory -Force | Out-Null
    New-Item -Path $nginxDir -ItemType Directory -Force | Out-Null

    Copy-Item -Path $ApiJarPath -Destination (Join-Path $appDir "polytech-lms-api.jar") -Force
    Render-Template -TemplatePath (Join-Path $script:TemplateDir "docker-compose.yml.tpl") -OutputPath (Join-Path $stackDir "docker-compose.yml") -Values @{}
    Render-Template -TemplatePath (Join-Path $script:TemplateDir "deploy-stack.sh.tpl") -OutputPath (Join-Path $stackDir "deploy-stack.sh") -Values @{}
    Render-Template -TemplatePath (Join-Path $script:TemplateDir "nginx-api.conf.tpl") -OutputPath (Join-Path $nginxDir "lms-api.conf") -Values @{ API_DOMAIN = $ApiDomain }

    if ([string]::IsNullOrWhiteSpace($script:DbPassword)) { $script:DbPassword = New-RandomSecret -Length 24 }
    if ([string]::IsNullOrWhiteSpace($script:DbRootPassword)) { $script:DbRootPassword = New-RandomSecret -Length 30 }
    if ([string]::IsNullOrWhiteSpace($script:QdrantApiKey)) { $script:QdrantApiKey = New-RandomSecret -Length 40 }
    if ([string]::IsNullOrWhiteSpace($script:GoogleApiKey)) {
        if ($script:DryRun) {
            $script:GoogleApiKey = "dryrun-google-api-key"
        } else {
            $script:GoogleApiKey = Read-RequiredValue "Google API Key를 입력해 주세요(벡터 임베딩 필수)"
        }
    }
    if ([string]::IsNullOrWhiteSpace($script:GeminiApiKey)) { $script:GeminiApiKey = $script:GoogleApiKey }
    if ([string]::IsNullOrWhiteSpace($script:StatisticsAiApiKey)) { $script:StatisticsAiApiKey = $script:GoogleApiKey }
    if ([string]::IsNullOrWhiteSpace($script:KollusClientUserId)) { $script:KollusClientUserId = "contentsummary" }
    if ([string]::IsNullOrWhiteSpace($script:LetsEncryptEmail)) { $script:LetsEncryptEmail = "admin@$ApiDomain" }

    $legacyPublicSource = Join-Path $script:RepoRoot "public_html"
    $legacySrcSource = Join-Path $script:RepoRoot "src"
    $statisticsSource = Join-Path $script:RepoRoot "통계"
    if ($script:DryRun) {
        New-Item -Path (Join-Path $legacyPublicDir "WEB-INF") -ItemType Directory -Force | Out-Null
        New-Item -Path $legacySrcDir -ItemType Directory -Force | Out-Null
        New-Item -Path $statisticsDir -ItemType Directory -Force | Out-Null
        Set-Content -Path (Join-Path $statisticsDir "입시율관리.xlsx") -Value "dryrun" -Encoding ASCII
        Set-Content -Path (Join-Path $legacyPublicDir "index.jsp") -Value "<% out.print(""dryrun""); %>" -Encoding ASCII
        Write-Info "DryRun: 레거시 웹앱/통계 폴더 복사 생략(더미 파일 생성)"
    } else {
        if (-not (Test-Path $legacyPublicSource)) {
            throw "레거시 웹 루트를 찾지 못했습니다: $legacyPublicSource"
        }
        if (-not (Test-Path $legacySrcSource)) {
            throw "레거시 Java 소스 폴더를 찾지 못했습니다: $legacySrcSource"
        }
        if (-not (Test-Path $statisticsSource)) {
            throw "통계 폴더를 찾지 못했습니다: $statisticsSource"
        }

        New-Item -Path $legacyPublicDir -ItemType Directory -Force | Out-Null
        New-Item -Path $legacySrcDir -ItemType Directory -Force | Out-Null
        New-Item -Path $statisticsDir -ItemType Directory -Force | Out-Null
        Copy-Item -Path (Join-Path $legacyPublicSource "*") -Destination $legacyPublicDir -Recurse -Force
        Copy-Item -Path (Join-Path $legacySrcSource "*") -Destination $legacySrcDir -Recurse -Force
        Copy-Item -Path (Join-Path $statisticsSource "*") -Destination $statisticsDir -Recurse -Force
        Write-Info "레거시 웹앱(public_html + src) + 통계 폴더 번들 복사 완료"
    }

    $importFlag = "false"
    if (-not [string]::IsNullOrWhiteSpace($DbDumpPath)) {
        if (-not (Test-Path $DbDumpPath)) {
            throw "지정한 DB dump 파일을 찾을 수 없습니다: $DbDumpPath"
        }
        New-Item -Path $migrationDir -ItemType Directory -Force | Out-Null
        $migrationTarget = Join-Path $migrationDir "source.sql"
        Copy-Item -Path $DbDumpPath -Destination $migrationTarget -Force
        $importFlag = "true"
        Write-Info "DB dump를 배포 번들에 포함했습니다: $migrationTarget"
    }

    $appDbUrl = "jdbc:mysql://mysql:3306/${DbName}?useSSL=false&allowPublicKeyRetrieval=true"
    $appDbUrlXml = $appDbUrl.Replace("&", "&amp;")
    $legacyResinWebPath = Join-Path $legacyPublicDir "WEB-INF\resin-web.xml"
    Render-Template -TemplatePath (Join-Path $script:TemplateDir "resin-web.xml.tpl") -OutputPath $legacyResinWebPath -Values @{
        APP_DB_URL = $appDbUrlXml
        DB_USER = $DbUser
        DB_PASSWORD = $script:DbPassword
        LEGACY_SOURCE_DIR = "/opt/polytech-lms/legacy/src"
    }
    Write-Info "레거시 Resin DB/JNDI 설정 생성 완료: $legacyResinWebPath"

    $envLines = @(
        "MYSQL_ROOT_PASSWORD=$script:DbRootPassword"
        "MYSQL_DATABASE=$DbName"
        "MYSQL_USER=$DbUser"
        "MYSQL_PASSWORD=$script:DbPassword"
        "APP_DB_URL=$appDbUrl"
        "QDRANT_API_KEY=$script:QdrantApiKey"
        "QDRANT_COLLECTION=$QdrantCollection"
        "GOOGLE_API_KEY=$script:GoogleApiKey"
        "GEMINI_API_KEY=$script:GeminiApiKey"
        "KOSIS_CONSUMER_KEY=$script:KosisConsumerKey"
        "KOSIS_CONSUMER_SECRET=$script:KosisConsumerSecret"
        "WORK24_AUTH_KEY=$script:Work24AuthKey"
        "JOBKOREA_API_KEY=$script:JobkoreaApiKey"
        "JOBKOREA_OEM_CODE=$script:JobkoreaOemCode"
        "STATISTICS_AI_API_KEY=$script:StatisticsAiApiKey"
        "KOLLUS_ACCESS_TOKEN=$script:KollusAccessToken"
        "KOLLUS_SECURITY_KEY=$script:KollusSecurityKey"
        "KOLLUS_CHANNEL_KEY=$script:KollusChannelKey"
        "KOLLUS_CLIENT_USER_ID=$script:KollusClientUserId"
        "API_DOMAIN=$ApiDomain"
        "LETSENCRYPT_EMAIL=$script:LetsEncryptEmail"
        "DB_IMPORT_ON_DEPLOY=$importFlag"
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $stackDir ".env"), ($envLines -join "`n"), $utf8NoBom)

    return $stackDir
}

function Deploy-StackToVm {
    param(
        [string]$StackDir,
        [string]$VmInstanceName,
        [string]$SshUser = ""
    )

    Write-Step "VM으로 백엔드 스택 배포"
    # 왜: 실행마다 stack-타임스탬프 폴더를 만들기 때문에,
    # 업로드한 최신 폴더를 정확히 지정하지 않으면 이전 ~/stack을 잘못 실행할 수 있습니다.
    $stackDirName = Split-Path -Path $StackDir -Leaf
    $remoteBasePath = "~/$stackDirName"
    if (Test-IsWindowsPlatform) {
        Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "ssh/putty_force_connect", "true")
    }

    # 왜: CI/운영마다 VM 로그인 계정이 다를 수 있어 단일 계정에 고정하면 배포가 바로 실패합니다.
    # 지정 계정 -> gcloud 활성계정 -> 관례 계정 -> 인스턴스 기본값 순으로 명시적으로 시도합니다.
    $targetHosts = New-Object System.Collections.Generic.List[string]
    $hasPinnedSshUser = -not [string]::IsNullOrWhiteSpace($SshUser)
    if (-not [string]::IsNullOrWhiteSpace($SshUser)) {
        $targetHosts.Add("$SshUser@$VmInstanceName")
    }
    if (-not $hasPinnedSshUser) {
        try {
            # 왜: 운영 VM SSH 계정이 newkl/ubuntu가 아닌 커스텀 계정일 수 있어,
            # 인스턴스 메타데이터의 ssh-keys에 등록된 실제 사용자 후보를 먼저 수집합니다.
            $instanceJson = Invoke-Checked -Command "gcloud" -Arguments @(
                "compute", "instances", "describe", $VmInstanceName,
                "--zone", $Zone,
                "--format=json"
            )
            $instanceObject = $instanceJson | ConvertFrom-Json
            if ($instanceObject -and $instanceObject.metadata -and $instanceObject.metadata.items) {
                foreach ($item in $instanceObject.metadata.items) {
                    if ($item.key -ne "ssh-keys") { continue }
                    $keyLines = ([string]$item.value) -split "`n"
                    foreach ($keyLine in $keyLines) {
                        if ([string]::IsNullOrWhiteSpace($keyLine)) { continue }
                        $delimiterIndex = $keyLine.IndexOf(":")
                        if ($delimiterIndex -le 0) { continue }
                        $candidateUser = $keyLine.Substring(0, $delimiterIndex).Trim()
                        if (-not [string]::IsNullOrWhiteSpace($candidateUser)) {
                            $targetHosts.Add("$candidateUser@$VmInstanceName")
                        }
                    }
                }
            }
        } catch {
            Write-Info "VM 메타데이터 ssh-keys 사용자 조회에 실패해 기본 SSH 후보만 사용합니다."
        }
        try {
            $activeAccount = (Invoke-Checked -Command "gcloud" -Arguments @("config", "get-value", "account")).Trim()
            if (-not [string]::IsNullOrWhiteSpace($activeAccount)) {
                $activeUser = $activeAccount.Split("@")[0]
                if (-not [string]::IsNullOrWhiteSpace($activeUser)) {
                    $targetHosts.Add("$activeUser@$VmInstanceName")
                }
            }
        } catch {
            Write-Info "활성 gcloud 계정 조회에 실패해 기본 SSH 후보만 사용합니다."
        }
        $targetHosts.Add("newkl@$VmInstanceName")
        $targetHosts.Add("ubuntu@$VmInstanceName")
        $targetHosts.Add("root@$VmInstanceName")
        $targetHosts.Add($VmInstanceName)
    }

    $uniqueHosts = New-Object System.Collections.Generic.List[string]
    foreach ($candidate in $targetHosts) {
        if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
        if (-not $uniqueHosts.Contains($candidate)) {
            $uniqueHosts.Add($candidate)
        }
    }

    $lastError = ""
    foreach ($targetHost in $uniqueHosts) {
        $remotePath = "$targetHost`:"
        Write-Info "VM SSH 대상 확인: $targetHost"
        try {
            # 왜: 원격 apt/docker pull 단계가 비정상 지연될 때 CI가 오래 멈추지 않도록 실행 시간을 짧게 제한합니다.
            $remoteCommand = 'if [ "$(id -u)" -eq 0 ]; then timeout 360 bash __REMOTE_PATH__/deploy-stack.sh __REMOTE_PATH__; elif sudo -n true >/dev/null 2>&1; then sudo -n timeout 360 bash __REMOTE_PATH__/deploy-stack.sh __REMOTE_PATH__; else echo ''sudo_nopasswd_required''; exit 1; fi'.Replace("__REMOTE_PATH__", $remoteBasePath)
            Invoke-Checked -Command "gcloud" -Arguments @(
                "compute", "scp",
                "--quiet",
                "--recurse",
                "--strict-host-key-checking=no",
                "--scp-flag=-oBatchMode=yes",
                "--scp-flag=-oConnectTimeout=15",
                $StackDir, $remotePath,
                "--zone", $Zone
            )
            Invoke-Checked -Command "gcloud" -Arguments @(
                "compute", "ssh", $targetHost,
                "--quiet",
                "--strict-host-key-checking=no",
                "--ssh-flag=-oBatchMode=yes",
                "--ssh-flag=-oConnectTimeout=15",
                "--ssh-flag=-T",
                "--zone", $Zone,
                # 왜: CI에서 sudo 비밀번호 프롬프트가 뜨면 배포가 무기한 대기하므로,
                # root/무비밀번호 sudo 여부를 먼저 판단해 가능한 경로로만 실행합니다.
                "--command", $remoteCommand
            )
            Write-Info "VM 배포 SSH 대상 확정: $targetHost"
            return
        } catch {
            $lastError = $_.Exception.Message
            Write-Info "SSH 대상 실패: $targetHost"
        }
    }

    throw "VM 배포 SSH/권한 검증에 실패했습니다. 시도 대상: $($uniqueHosts -join ', ') / 마지막 오류: $lastError"
}

function Ensure-FirebaseSite {
    param(
        [string]$TargetProjectId,
        [string]$TargetSiteId
    )

    Write-Step "Firebase Hosting 사이트 확인"
    $firebaseAuthArgs = Get-FirebaseAuthArgs
    $sitesText = Invoke-Checked -Command "firebase" -Arguments (@("hosting:sites:list", "--project", $TargetProjectId) + $firebaseAuthArgs)
    if ($sitesText -notmatch [regex]::Escape($TargetSiteId)) {
        Invoke-Checked -Command "firebase" -Arguments (@("hosting:sites:create", $TargetSiteId, "--project", $TargetProjectId) + $firebaseAuthArgs)
    } else {
        Write-Info "기존 Firebase 사이트를 그대로 사용합니다: $TargetSiteId"
    }
}

function Ensure-FirebaseProject {
    param([string]$TargetProjectId)

    Write-Step "Firebase 프로젝트 연결 확인"
    $firebaseAuthArgs = Get-FirebaseAuthArgs
    $listOutput = Invoke-Checked -Command "firebase" -Arguments (@("projects:list") + $firebaseAuthArgs)
    if ($listOutput -notmatch [regex]::Escape($TargetProjectId)) {
        Invoke-Checked -Command "firebase" -Arguments (@("projects:addfirebase", $TargetProjectId) + $firebaseAuthArgs)
    } else {
        Write-Info "Firebase 연결이 이미 되어 있습니다: $TargetProjectId"
    }
}

function Deploy-FirebaseHosting {
    param([string]$TargetProjectId, [string]$TargetSiteId, [string]$VmEntryUrl)

    Write-Step "Firebase Hosting 배포"
    if ([string]::IsNullOrWhiteSpace($VmEntryUrl)) {
        throw "Firebase 리다이렉트 대상 URL이 비어 있습니다."
    }
    if ($VmEntryUrl -notmatch "^https?://") {
        throw "Firebase 리다이렉트 대상 URL 형식이 올바르지 않습니다: $VmEntryUrl"
    }

    $firebaseWorkDir = Join-Path $script:GeneratedDir "firebase-work"
    $firebasePublicDir = Join-Path $firebaseWorkDir "public"
    if (Test-Path $firebaseWorkDir) {
        Remove-Item -Path $firebaseWorkDir -Recurse -Force
    }
    New-Item -Path $firebasePublicDir -ItemType Directory -Force | Out-Null

    $redirectHtml = @"
<!doctype html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <meta http-equiv="refresh" content="0;url=$VmEntryUrl" />
  <title>Polytech LMS 이동 중</title>
</head>
<body>
  <p>Polytech LMS로 이동 중입니다.</p>
  <p><a href="$VmEntryUrl">자동 이동이 안 되면 여기를 눌러 주세요.</a></p>
  <script>location.replace("$VmEntryUrl");</script>
</body>
</html>
"@
    Set-Content -Path (Join-Path $firebasePublicDir "index.html") -Value $redirectHtml -Encoding UTF8

    $firebaseJson = @{
        hosting = @{
            site = $TargetSiteId
            public = "public"
            ignore = @("firebase.json", "**/.*", "**/node_modules/**")
            redirects = @(
                @{
                    source = "**"
                    destination = $VmEntryUrl
                    type = 302
                }
            )
        }
    } | ConvertTo-Json -Depth 6
    Set-Content -Path (Join-Path $firebaseWorkDir "firebase.json") -Value $firebaseJson -Encoding UTF8

    $firebaseAuthArgs = Get-FirebaseAuthArgs
    Push-Location $firebaseWorkDir
    try {
        Invoke-Checked -Command "firebase" -Arguments (@("deploy", "--project", $TargetProjectId, "--only", "hosting", "--config", "firebase.json") + $firebaseAuthArgs)
    } finally {
        Pop-Location
    }
}

function Write-Summary {
    param(
        [string]$VmPublicIp,
        [string]$AddressName,
        [string]$TargetFirebaseSite
    )

    Write-Step "요약 파일 생성"
    if (-not (Test-Path $script:GeneratedDir)) {
        New-Item -Path $script:GeneratedDir -ItemType Directory -Force | Out-Null
    }
    $summaryPath = Join-Path $script:GeneratedDir "setup-summary.txt"
    $lines = @(
        "=== GCP/Firebase 원클릭 설정 결과 ==="
        "프로젝트: $script:ProjectId"
        "리전/존: $Region / $Zone"
        "VM 이름: $VmName"
        "VM 공인 IP: $VmPublicIp"
        "고정 IP 리소스명: $AddressName"
        "API 도메인: $ApiDomain"
        "WWW 도메인: $WwwDomain"
        "Firebase 사이트: $TargetFirebaseSite"
        "Firebase 진입 URL: https://${TargetFirebaseSite}.web.app (VM으로 302 리다이렉트)"
        "DB 이관 사용: $script:EnableDbMigration"
        "DB 이관 상태: $script:DbMigrationStatus"
        "DB dump 경로: $script:DbMigrationDumpPath"
        ""
        "[DNS에 넣을 값]"
        "1) API A 레코드: $ApiDomain -> $VmPublicIp"
        "2) Firebase 도메인 연결: Firebase 콘솔 안내값 사용"
        ""
        "[민감정보]"
        "- DB Root Password: $script:DbRootPassword"
        "- DB User Password: $script:DbPassword"
        "- Qdrant API Key: $script:QdrantApiKey"
        ""
        "경로: $summaryPath"
    )
    Set-Content -Path $summaryPath -Value ($lines -join "`n") -Encoding UTF8
    Write-Info "결과 파일: $summaryPath"
}

function Ensure-CliLoginIfNeeded {
    Write-Step "CLI 로그인"
    # 왜: 로컬 셸/CLI 조합에 따라 로그인 조회 명령이 불안정할 수 있어, 여기서는 재로그인/검사를 강제하지 않습니다.
    # 실제 인증 문제는 이후 gcloud/firebase 실행 단계에서 즉시 오류로 드러나므로 그 지점에서 명확히 확인됩니다.
    Write-Info "사전 로그인(gcloud/firebase) 상태를 가정하고 진행합니다."
}

function Main {
    Write-Host "================================================" -ForegroundColor DarkGray
    Write-Host " Polytech LMS GCP/Firebase 원클릭 셋업 시작" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor DarkGray

    Require-Tool "gcloud"
    if (-not $SkipFirebaseDeploy) {
        Require-Tool "firebase"
    }

    if ([string]::IsNullOrWhiteSpace($ProjectId)) {
        $script:ProjectId = Read-RequiredValue "GCP Project ID를 입력해 주세요"
    }
    if ([string]::IsNullOrWhiteSpace($script:FirebaseSite)) {
        # 왜: 이번 운영 기준은 Firebase를 짧은 진입 링크로만 사용하므로 기본 사이트를 고정합니다.
        $script:FirebaseSite = "epoly-kopo"
    }
    if ($script:EnableDbMigration -and $SkipVmProvision) {
        throw "DB 이관은 VM 배포 단계에서 자동 import되므로 -SkipVmProvision과 함께 사용할 수 없습니다."
    }

    Ensure-CliLoginIfNeeded

    if (-not $SkipProjectBootstrap) {
        Ensure-Project -TargetProjectId $script:ProjectId -TargetBillingAccountId $BillingAccountId
        Ensure-EnabledApis
    } else {
        Write-Step "프로젝트/결제 부트스트랩 건너뜀"
        Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "project", $script:ProjectId)
        Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "compute/region", $Region)
        Invoke-Checked -Command "gcloud" -Arguments @("config", "set", "compute/zone", $Zone)
    }

    $ipResourceName = "$VmName-ip"
    $reservedIp = Ensure-StaticIp -AddressName $ipResourceName
    $vmPublicIp = $reservedIp

    if (-not $SkipVmProvision) {
        Ensure-FirewallRules
        $vmPublicIp = Ensure-Vm -VmInstanceName $VmName -VmAddress $reservedIp
        $jarPath = Build-ApiJar
        $dbDumpPath = Resolve-DbMigrationDumpPath
        $dbDumpPath = Normalize-DbDumpIfNeeded -DumpPath $dbDumpPath
        $stackDir = Prepare-StackBundle -ApiJarPath $jarPath -DbDumpPath $dbDumpPath
        Deploy-StackToVm -StackDir $stackDir -VmInstanceName $VmName -SshUser $VmSshUser
        if ($script:EnableDbMigration) {
            if ($script:DryRun) { $script:DbMigrationStatus = "DryRun(배포 시 import 예정)" }
            else { $script:DbMigrationStatus = "완료(배포 단계에서 import 실행)" }
        }
    } else {
        Write-Step "VM 프로비저닝 건너뜀"
    }

    if (-not $SkipFirebaseDeploy) {
        $vmEntryUrl = if (-not [string]::IsNullOrWhiteSpace($ApiDomain) -and $ApiDomain -ne "api.example.com") {
            "https://$ApiDomain"
        } else {
            "http://$vmPublicIp"
        }
        Write-Info "Firebase 진입 URL은 VM으로 리다이렉트합니다: $vmEntryUrl"
        Ensure-FirebaseProject -TargetProjectId $script:ProjectId
        Ensure-FirebaseSite -TargetProjectId $script:ProjectId -TargetSiteId $script:FirebaseSite
        Deploy-FirebaseHosting -TargetProjectId $script:ProjectId -TargetSiteId $script:FirebaseSite -VmEntryUrl $vmEntryUrl
    } else {
        Write-Step "Firebase 배포 건너뜀"
    }

    Write-Summary -VmPublicIp $vmPublicIp -AddressName $ipResourceName -TargetFirebaseSite $script:FirebaseSite

    Write-Host ""
    Write-Host "[완료] 원클릭 자동 셋업이 끝났습니다." -ForegroundColor Green
    Write-Host "결과는 tools/gcp/generated/setup-summary.txt 에 정리되어 있습니다."
}

Main
