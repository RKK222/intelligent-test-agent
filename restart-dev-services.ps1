#requires -Version 5.1
[CmdletBinding()]
param(
    [Alias("Profile")]
    [ValidateSet("local", "test", "guo")]
    [string]$BackendProfile = "test",

    [string]$EnvFile = "",

    [string]$LogDir = "",

    [switch]$SkipBackendBuild,

    [switch]$SkipFrontendBuild,

    [Alias("h")]
    [switch]$Help
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$RootDir = (Resolve-Path -LiteralPath (Split-Path -Parent $PSCommandPath)).Path
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$BackendJar = Join-Path $BackendDir "test-agent-app/target/test-agent-app-0.1.0-SNAPSHOT.jar"
$BackendUrl = "http://127.0.0.1:8080"
$FrontendUrl = "http://127.0.0.1:3000"
$FrontendHost = "127.0.0.1"
$FrontendPort = "3000"
$BackendJavaDirectNetworkArgs = @(
    "-Djava.net.useSystemProxies=false",
    "-Dhttp.proxyHost=",
    "-Dhttp.proxyPort=",
    "-Dhttps.proxyHost=",
    "-Dhttps.proxyPort=",
    "-Dftp.proxyHost=",
    "-Dftp.proxyPort=",
    "-DsocksProxyHost=",
    "-DsocksProxyPort="
)

function Show-Usage {
    @"
Usage: powershell -ExecutionPolicy Bypass -File .\restart-dev-services.ps1 [-Profile local|test|guo] [-EnvFile <path>] [-LogDir <path>] [-SkipBackendBuild] [-SkipFrontendBuild] [-Help]

Compile and restart the local platform services one by one on Windows. Each
service is stopped before its new instance starts, in dependency order:
backend -> opencode-manager -> frontend.

Services managed by this script:
  backend           Spring Boot test-agent-app (java -jar, profile from -Profile).
  opencode-manager  Go opencode-manager supervisor (.\opencode-manager\bin\opencode-manager.exe run).
                    Started by default when TEST_AGENT_OPENCODE_BASE_URL is a local URL.
                    Standalone opencode serve is not started separately when the
                    manager runs, because the manager spawns opencode child processes.
  frontend          agent-web Vite dev server (corepack pnpm dev).

Defaults:
  backend profile: test
  backend env:     .env.test
  backend URL:     TEST_AGENT_BASE_URL or http://127.0.0.1:8080
  frontend URL:    TEST_AGENT_FRONTEND_URL or http://127.0.0.1:3000
  manager token:   TEST_AGENT_OPENCODE_MANAGER_TOKEN or local-manager-token (dev/test default)
  logs:            .tmp\dev-services\

Options:
  -Profile              Backend Spring profile, local, test, or guo. Default: test.
  -EnvFile              Backend dotenv file. Relative paths are resolved from the repo root.
  -LogDir               Service log directory. Relative paths are resolved from the repo root.
  -SkipBackendBuild     Restart backend without running Maven package first.
  -SkipFrontendBuild    Restart frontend without running pnpm build first.
  -Help                 Show this help.

Environment overrides:
  TEST_AGENT_START_OPENCODE_MANAGER  auto|true|false. Set false to skip the Go manager.
  TEST_AGENT_OPENCODE_MANAGER_TOKEN  Shared secret between manager and backend. Defaults to local-manager-token.
"@ | Write-Host
}

if ($Help) {
    Show-Usage
    exit 0
}

function Join-ChildPath {
    param(
        [Parameter(Mandatory = $true)][string]$Base,
        [Parameter(Mandatory = $true)][string[]]$Children
    )

    $path = $Base
    foreach ($child in $Children) {
        $path = Join-Path $path $child
    }
    return $path
}

function Resolve-RepoPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $RootDir $Path))
}

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Resolve-RepoPath ".env.$BackendProfile"
} else {
    $EnvFile = Resolve-RepoPath $EnvFile
}

if ([string]::IsNullOrWhiteSpace($LogDir)) {
    $LogDir = Join-Path $RootDir ".tmp/dev-services"
} else {
    $LogDir = Resolve-RepoPath $LogDir
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Default = ""
    )

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrEmpty($value)) {
        return $Default
    }
    return $value
}

function Set-EnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value
    )

    [Environment]::SetEnvironmentVariable($Name, $Value, "Process")
    Set-Item -LiteralPath "Env:$Name" -Value $Value
}

function Require-Command {
    param([Parameter(Mandatory = $true)][string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command not found: $Name"
    }
}

function Load-EnvFile {
    param([Parameter(Mandatory = $true)][string]$File)

    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) {
        throw "Missing env file: $File. Create it from the backend README example. Do not commit it."
    }

    # 安全读取 dotenv：只解析 KEY=VALUE，不执行文件内容，避免把本地密钥文件当作脚本运行。
    foreach ($rawLine in Get-Content -LiteralPath $File) {
        $line = $rawLine.TrimEnd("`r")
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            continue
        }
        if ($line.StartsWith("export ")) {
            $line = $line.Substring("export ".Length)
        }
        if (-not $line.Contains("=")) {
            throw "Invalid env line in $File`: expected KEY=VALUE."
        }

        $separatorIndex = $line.IndexOf("=")
        $key = $line.Substring(0, $separatorIndex)
        $value = $line.Substring($separatorIndex + 1)
        if ($key -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
            throw "Invalid env key in $File`: $key"
        }

        if ($value.Length -ge 2) {
            if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        Set-EnvValue $key $value
    }
}

function Get-UrlUri {
    param([Parameter(Mandatory = $true)][string]$Url)

    try {
        return [Uri]$Url
    } catch {
        throw "Invalid URL: $Url"
    }
}

function Get-UrlPort {
    param([Parameter(Mandatory = $true)][string]$Url)

    $uri = Get-UrlUri $Url
    if (-not $uri.IsDefaultPort) {
        return [string]$uri.Port
    }
    if ($uri.Scheme -eq "https") {
        return "443"
    }
    return "80"
}

function Get-UrlHost {
    param([Parameter(Mandatory = $true)][string]$Url)

    return (Get-UrlUri $Url).Host
}

function Test-LoopbackHost {
    param([AllowEmptyString()][string]$HostName)

    return $HostName -in @("127.0.0.1", "localhost", "::1")
}

function Test-RoutableIPv4 {
    param([AllowEmptyString()][string]$Ip)

    $address = $null
    if (-not [System.Net.IPAddress]::TryParse($Ip, [ref]$address)) {
        return $false
    }
    if ($address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }
    $bytes = $address.GetAddressBytes()
    return $bytes[0] -ne 127 -and -not ($bytes[0] -eq 169 -and $bytes[1] -eq 254) -and $Ip -ne "0.0.0.0"
}

function Get-LocalIPv4 {
    # 优先用 UDP socket 从系统路由表取默认出口地址；Connect 不发送业务数据，只让系统选择本地地址。
    $udpClient = $null
    try {
        $udpClient = [System.Net.Sockets.UdpClient]::new()
        $udpClient.Connect("8.8.8.8", 80)
        $ip = $udpClient.Client.LocalEndPoint.Address.ToString()
        if (Test-RoutableIPv4 $ip) {
            return $ip
        }
    } catch {
        # 路由表不可用时继续尝试主机名解析。
    } finally {
        if ($null -ne $udpClient) {
            $udpClient.Dispose()
        }
    }

    try {
        foreach ($address in [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName())) {
            $ip = $address.ToString()
            if (Test-RoutableIPv4 $ip) {
                return $ip
            }
        }
    } catch {
        return ""
    }

    return ""
}

function Test-LocalOpencodeUrl {
    param([AllowEmptyString()][string]$Url)

    if ([string]::IsNullOrWhiteSpace($Url)) {
        return $false
    }

    $hostName = Get-UrlHost $Url
    if (Test-LoopbackHost $hostName) {
        return $true
    }

    $localIPv4 = Get-LocalIPv4
    return -not [string]::IsNullOrWhiteSpace($localIPv4) -and $hostName -eq $localIPv4
}

function Test-EnvSwitch {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][scriptblock]$AutoAction,
        [Parameter(Mandatory = $true)][string]$Name
    )

    switch ($Value.ToLowerInvariant()) {
        { $_ -in @("true", "1", "yes") } { return $true }
        { $_ -in @("false", "0", "no") } { return $false }
        { $_ -in @("auto", "") } { return (& $AutoAction) }
        default { throw "Invalid $Name`: $Value" }
    }
}

function Should-StartOpencodeManager {
    $value = Get-EnvValue "TEST_AGENT_START_OPENCODE_MANAGER" "auto"
    return Test-EnvSwitch $value { -not [string]::IsNullOrWhiteSpace((Get-EnvValue "TEST_AGENT_OPENCODE_BASE_URL" "")) -and (Test-LocalOpencodeUrl (Get-EnvValue "TEST_AGENT_OPENCODE_BASE_URL" "")) } "TEST_AGENT_START_OPENCODE_MANAGER"
}

function Should-StartOpencode {
    $value = Get-EnvValue "TEST_AGENT_START_OPENCODE" "auto"
    return Test-EnvSwitch $value {
        if (Should-StartOpencodeManager) {
            $false
        } else {
            Test-LocalOpencodeUrl (Get-EnvValue "TEST_AGENT_OPENCODE_BASE_URL" "")
        }
    } "TEST_AGENT_START_OPENCODE"
}

function Should-SeedDemoWorkspaces {
    $value = Get-EnvValue "TEST_AGENT_SEED_DEMO_WORKSPACES" "auto"
    return Test-EnvSwitch $value { Should-StartOpencode } "TEST_AGENT_SEED_DEMO_WORKSPACES"
}

function Get-OpencodeBinary {
    $configured = Get-EnvValue "TEST_AGENT_OPENCODE_BIN" ""
    if (-not [string]::IsNullOrWhiteSpace($configured)) {
        return $configured
    }

    $homeBin = Join-ChildPath $HOME @(".opencode", "bin", "opencode.exe")
    if (Test-Path -LiteralPath $homeBin -PathType Leaf) {
        return $homeBin
    }

    $homeBinWithoutExtension = Join-ChildPath $HOME @(".opencode", "bin", "opencode")
    if (Test-Path -LiteralPath $homeBinWithoutExtension -PathType Leaf) {
        return $homeBinWithoutExtension
    }

    $command = Get-Command "opencode" -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    return ""
}

function Normalize-CommandText {
    param([AllowEmptyString()][string]$Value)

    return $Value.Replace("\", "/")
}

function Normalize-PathForCommand {
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Normalize-CommandText ([System.IO.Path]::GetFullPath($Path)))
}

function Get-ProcessIdsMatching {
    param([Parameter(Mandatory = $true)][scriptblock]$Predicate)

    # Windows 通过 Win32_Process.CommandLine 精确匹配脚本管理的进程，避免误杀同机其他服务。
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object {
            $commandLine = $_.CommandLine
            -not [string]::IsNullOrWhiteSpace($commandLine) -and (& $Predicate $commandLine)
        } |
        ForEach-Object { [int]$_.ProcessId }
}

function Get-BackendProcessIds {
    $jarPath = Normalize-PathForCommand $BackendJar
    Get-ProcessIdsMatching {
        param([string]$CommandLine)
        $normalized = Normalize-CommandText $CommandLine
        return $normalized.Contains($jarPath) -and $CommandLine -match '(^|\s)-jar(\s|$)'
    }
}

function Get-FrontendProcessIds {
    $frontendPath = Normalize-PathForCommand $FrontendDir
    Get-ProcessIdsMatching {
        param([string]$CommandLine)
        $normalized = Normalize-CommandText $CommandLine
        return $CommandLine.Contains("@test-agent/agent-web dev") -or ($normalized.Contains($frontendPath) -and $normalized.Contains("vite"))
    }
}

function Get-OpencodeManagerProcessIds {
    $managerPath = (Normalize-PathForCommand (Join-ChildPath $RootDir @("opencode-manager", "bin", "opencode-manager")))
    Get-ProcessIdsMatching {
        param([string]$CommandLine)
        $normalized = Normalize-CommandText $CommandLine
        return $normalized.Contains($managerPath) -and $normalized -match '\brun\b'
    }
}

function Test-PortArgument {
    param(
        [Parameter(Mandatory = $true)][string]$CommandLine,
        [Parameter(Mandatory = $true)][int]$Port
    )

    $escapedPort = [regex]::Escape([string]$Port)
    return $CommandLine -match ('(^|\s)--port(\s+|=)' + $escapedPort + '(\s|$)')
}

function Get-OpencodeProcessIds {
    $baseUrl = Get-EnvValue "TEST_AGENT_OPENCODE_BASE_URL" ""
    if ([string]::IsNullOrWhiteSpace($baseUrl)) {
        return @()
    }
    $port = [int](Get-UrlPort $baseUrl)
    Get-ProcessIdsMatching {
        param([string]$CommandLine)
        return $CommandLine -match '\bopencode(\.exe|\.cmd)?\b' -and $CommandLine -match '\bserve\b' -and (Test-PortArgument $CommandLine $port)
    }
}

function Get-OpencodeManagerPortStart {
    return [int](Get-EnvValue "OPENCODE_MANAGER_PORT_START" (Get-UrlPort (Get-EnvValue "TEST_AGENT_OPENCODE_BASE_URL" "http://127.0.0.1:4096")))
}

function Get-OpencodeManagerPortEnd {
    $start = Get-OpencodeManagerPortStart
    return [int](Get-EnvValue "OPENCODE_MANAGER_PORT_END" ([string]($start + 9)))
}

function Get-OpencodeManagerStateDir {
    return Get-EnvValue "OPENCODE_MANAGER_STATE_DIR" (Join-Path $LogDir "opencode-manager-state")
}

function Get-OpencodeManagerStateProcessIds {
    $stateDir = Get-OpencodeManagerStateDir
    $processDir = Join-Path $stateDir "processes"
    if (-not (Test-Path -LiteralPath $processDir -PathType Container)) {
        return @()
    }

    foreach ($file in Get-ChildItem -LiteralPath $processDir -Filter "*.json" -File -ErrorAction SilentlyContinue) {
        $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
        if ($content -match '"pid"\s*:\s*(\d+)') {
            [int]$Matches[1]
        }
    }
}

function Get-OpencodeManagerPortRangeProcessIds {
    $start = Get-OpencodeManagerPortStart
    $end = Get-OpencodeManagerPortEnd
    Get-ProcessIdsMatching {
        param([string]$CommandLine)
        if ($CommandLine -notmatch '\bopencode(\.exe|\.cmd)?\b' -or $CommandLine -notmatch '\bserve\b') {
            return $false
        }
        for ($port = $start; $port -le $end; $port++) {
            if (Test-PortArgument $CommandLine $port) {
                return $true
            }
        }
        return $false
    }
}

function ConvertTo-UniqueProcessIds {
    param([object[]]$ProcessIds)

    $ids = @()
    foreach ($item in $ProcessIds) {
        if ($null -eq $item) {
            continue
        }
        $text = ([string]$item).Trim()
        if ([string]::IsNullOrWhiteSpace($text)) {
            continue
        }
        $ids += [int]$text
    }
    return @($ids | Sort-Object -Unique)
}

function Stop-ProcessIds {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [object[]]$ProcessIds
    )

    $ids = @(ConvertTo-UniqueProcessIds $ProcessIds)
    if ($ids.Count -eq 0) {
        Write-Host "No $Label process to stop."
        return
    }

    Write-Host "Stopping $Label`: $($ids -join ' ')"
    Stop-Process -Id $ids -ErrorAction SilentlyContinue
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $alive = @($ids | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
        if ($alive.Count -eq 0) {
            return
        }
        Start-Sleep -Milliseconds 500
    }

    $aliveIds = @($ids | Where-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue })
    if ($aliveIds.Count -gt 0) {
        Write-Host "Force stopping $Label`: $($aliveIds -join ' ')"
        Stop-Process -Id $aliveIds -Force -ErrorAction SilentlyContinue
    }
}

function Cleanup-OpencodeManagerState {
    $stateDir = Get-OpencodeManagerStateDir
    $processDir = Join-Path $stateDir "processes"
    if (Test-Path -LiteralPath $processDir -PathType Container) {
        Remove-Item -Path (Join-Path $processDir "*.json") -Force -ErrorAction SilentlyContinue
    }
}

function Stop-BackendService {
    Stop-ProcessIds "backend" @(Get-BackendProcessIds)
}

function Stop-FrontendService {
    Stop-ProcessIds "frontend" @(Get-FrontendProcessIds)
}

function Stop-OpencodeManagerService {
    Stop-ProcessIds "opencode-manager" @(Get-OpencodeManagerProcessIds)
    Stop-ProcessIds "opencode-manager managed opencode serve" @(
        Get-OpencodeManagerStateProcessIds
        Get-OpencodeManagerPortRangeProcessIds
    )
    Stop-ProcessIds "opencode serve" @(Get-OpencodeProcessIds)
    Cleanup-OpencodeManagerState
}

function Test-HttpOk {
    param([Parameter(Mandatory = $true)][string]$Url)

    try {
        $response = Invoke-WebRequest -Uri $Url -Method Get -TimeoutSec 5 -ErrorAction Stop
        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
    } catch {
        return $false
    }
}

function Get-ErrorLogPath {
    param([Parameter(Mandatory = $true)][string]$LogPath)

    $directory = Split-Path -Parent $LogPath
    $nameWithoutExtension = [System.IO.Path]::GetFileNameWithoutExtension($LogPath)
    return Join-Path $directory "$nameWithoutExtension.err.log"
}

function Write-Stderr {
    param([AllowEmptyString()][string]$Message)

    [Console]::Error.WriteLine($Message)
}

function Write-LogTail {
    param([Parameter(Mandatory = $true)][string]$LogPath)

    $errorLogPath = Get-ErrorLogPath $LogPath
    if (Test-Path -LiteralPath $LogPath -PathType Leaf) {
        Write-Stderr "stdout log tail:"
        Get-Content -LiteralPath $LogPath -Tail 120 -ErrorAction SilentlyContinue | ForEach-Object { Write-Stderr $_ }
    }
    if (Test-Path -LiteralPath $errorLogPath -PathType Leaf) {
        Write-Stderr "stderr log tail:"
        Get-Content -LiteralPath $errorLogPath -Tail 120 -ErrorAction SilentlyContinue | ForEach-Object { Write-Stderr $_ }
    }
}

function Wait-UntilHttpOk {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$LogPath,
        [int]$Attempts = 90
    )

    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        if (Test-HttpOk $Url) {
            Write-Host "OK $Label`: $Url"
            return
        }
        Start-Sleep -Seconds 2
    }

    Write-Stderr "Timed out waiting for $Label`: $Url"
    Write-LogTail $LogPath
    exit 1
}

function ConvertTo-PowerShellLiteral {
    param([AllowEmptyString()][string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-CurrentPowerShellPath {
    $currentProcess = Get-Process -Id $PID
    if (-not [string]::IsNullOrWhiteSpace($currentProcess.Path)) {
        return $currentProcess.Path
    }
    if ($PSVersionTable.PSEdition -eq "Core") {
        return "pwsh"
    }
    return "powershell.exe"
}

function Start-BackgroundCommand {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Command,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory = $true)][string]$LogPath
    )

    $errorLogPath = Get-ErrorLogPath $LogPath
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogPath) | Out-Null
    Set-Content -LiteralPath $LogPath -Value "" -NoNewline
    Set-Content -LiteralPath $errorLogPath -Value "" -NoNewline

    # 子 PowerShell 只负责驻留并转发输出，真实服务仍按 command line 被精确发现和清理。
    $argumentText = (@($Arguments) | ForEach-Object { ConvertTo-PowerShellLiteral ([string]$_) }) -join " "
    $script = @"
`$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $(ConvertTo-PowerShellLiteral $WorkingDirectory)
& $(ConvertTo-PowerShellLiteral $Command) $argumentText >> $(ConvertTo-PowerShellLiteral $LogPath) 2>> $(ConvertTo-PowerShellLiteral $errorLogPath)
"@
    $encoded = [Convert]::ToBase64String([System.Text.Encoding]::Unicode.GetBytes($script))
    $hostPath = Get-CurrentPowerShellPath
    $hostArguments = @("-NoProfile")
    if ((Split-Path -Leaf $hostPath) -ieq "powershell.exe") {
        $hostArguments += @("-ExecutionPolicy", "Bypass")
    }
    $hostArguments += @("-EncodedCommand", $encoded)

    $process = Start-Process -FilePath $hostPath -ArgumentList $hostArguments -WindowStyle Hidden -PassThru
    return $process.Id
}

function Write-PidFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [object[]]$ProcessIds
    )

    $ids = @(ConvertTo-UniqueProcessIds $ProcessIds)
    Set-Content -LiteralPath $Path -Value ($ids -join [Environment]::NewLine)
}

function Build-Backend {
    if ($SkipBackendBuild) {
        Write-Host "Skipping backend build."
        return
    }

    Write-Host "Building backend: mvn clean package -Dmaven.test.skip=true"
    Push-Location $BackendDir
    try {
        & mvn clean package -Dmaven.test.skip=true
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

function Build-Frontend {
    if ($SkipFrontendBuild) {
        Write-Host "Skipping frontend build."
        return
    }

    Write-Host "Building frontend: corepack pnpm build"
    Push-Location $FrontendDir
    try {
        & corepack pnpm build
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

function Build-OpencodeManager {
    if (-not (Should-StartOpencodeManager)) {
        return
    }

    Require-Command "go"
    $managerDir = Join-Path $RootDir "opencode-manager"
    New-Item -ItemType Directory -Force -Path (Join-Path $managerDir "bin") | Out-Null
    Write-Host "Building opencode-manager: go build"
    Push-Location $managerDir
    try {
        & go build -o "bin/opencode-manager.exe" ./cmd/opencode-manager
        if ($LASTEXITCODE -ne 0) {
            exit $LASTEXITCODE
        }
    } finally {
        Pop-Location
    }
}

function Seed-DemoWorkspaces {
    if (-not (Should-SeedDemoWorkspaces)) {
        return
    }

    $sourceDir = Join-ChildPath $RootDir @("test-workspaces", "F-COSS")
    foreach ($version in @("20260620", "20260701")) {
        $dest = Join-ChildPath (Get-EnvValue "TEST_AGENT_ROOT" $RootDir) @("temp", "fcoss", $version)
        New-Item -ItemType Directory -Force -Path (Join-Path $dest "src/main") | Out-Null
        $readmePath = Join-Path $dest "README.md"
        if ((Test-Path -LiteralPath $sourceDir -PathType Container) -and -not (Test-Path -LiteralPath $readmePath)) {
            Copy-Item -Path (Join-Path $sourceDir "*") -Destination $dest -Recurse -Force
        } elseif (-not (Test-Path -LiteralPath $readmePath)) {
            Set-Content -LiteralPath $readmePath -Value "# F-COSS Demo Workspace"
        }
    }

    $workspaceDirs = @(
        @{ Path = Join-ChildPath (Get-EnvValue "TEST_AGENT_ROOT" $RootDir) @("temp", "fcoss", "mobile", "20260705"); SubDir = "src/mobile" },
        @{ Path = Join-ChildPath (Get-EnvValue "TEST_AGENT_ROOT" $RootDir) @("temp", "fcoss", "sync", "20260710"); SubDir = "sync" },
        @{ Path = Join-ChildPath (Get-EnvValue "TEST_AGENT_ROOT" $RootDir) @("temp", "fcoss", "report", "20260715"); SubDir = "reports" }
    )
    foreach ($entry in $workspaceDirs) {
        $dest = $entry.Path
        New-Item -ItemType Directory -Force -Path (Join-Path $dest $entry.SubDir) | Out-Null
        $readmePath = Join-Path $dest "README.md"
        if ((Test-Path -LiteralPath $sourceDir -PathType Container) -and -not (Test-Path -LiteralPath $readmePath)) {
            Copy-Item -Path (Join-Path $sourceDir "*") -Destination $dest -Recurse -Force
        } elseif (-not (Test-Path -LiteralPath $readmePath)) {
            Set-Content -LiteralPath $readmePath -Value "# F-COSS Demo Workspace"
        }
    }
}

function Derive-FrontendRuntimeSettings {
    $uri = Get-UrlUri $script:FrontendUrl
    $defaultPort = if (-not $uri.IsDefaultPort) { [string]$uri.Port } else { "3000" }
    $script:FrontendPort = Get-EnvValue "FRONTEND_PORT" $defaultPort
    $derivedHost = Get-EnvValue "FRONTEND_HOST" $uri.Host
    if ([string]::IsNullOrWhiteSpace((Get-EnvValue "FRONTEND_HOST" "")) -and -not (Test-LoopbackHost $derivedHost)) {
        $derivedHost = "0.0.0.0"
    }
    $script:FrontendHost = $derivedHost
}

function Apply-FrontendOriginDefaults {
    $defaultOrigins = "http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174"
    $origins = Get-EnvValue "TEST_AGENT_CORS_ALLOWED_ORIGINS" ""
    if ([string]::IsNullOrWhiteSpace($origins)) {
        $origins = $defaultOrigins
    }
    if (-not (("," + $origins + ",").Contains("," + $script:FrontendUrl + ","))) {
        $origins = "$origins,$script:FrontendUrl"
    }
    $loopbackOrigin = "http://127.0.0.1:$($script:FrontendPort)"
    if ($script:FrontendUrl -ne $loopbackOrigin -and -not (("," + $origins + ",").Contains("," + $loopbackOrigin + ","))) {
        $origins = "$origins,$loopbackOrigin"
    }
    Set-EnvValue "TEST_AGENT_CORS_ALLOWED_ORIGINS" $origins
}

function Apply-DetectedRuntimeIpDefaults {
    if (-not [string]::IsNullOrWhiteSpace((Get-EnvValue "TEST_AGENT_BACKEND_LISTEN_URL" ""))) {
        return
    }

    $localIPv4 = Get-LocalIPv4
    if ([string]::IsNullOrWhiteSpace($localIPv4)) {
        return
    }

    $backendPort = Get-UrlPort $script:BackendUrl
    $listenUrl = "http://$localIPv4`:$backendPort"
    Set-EnvValue "TEST_AGENT_BACKEND_LISTEN_URL" $listenUrl
    Write-Host "Defaulting TEST_AGENT_BACKEND_LISTEN_URL to detected local IPv4: $listenUrl"
}

function Apply-ManagerBackendPortDefaults {
    if ([string]::IsNullOrWhiteSpace((Get-EnvValue "OPENCODE_MANAGER_BACKEND_PORT" ""))) {
        Set-EnvValue "OPENCODE_MANAGER_BACKEND_PORT" (Get-UrlPort $script:BackendUrl)
    }
}

function Clear-ServiceLogs {
    Write-Host "Cleaning up old service logs..."
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    foreach ($name in @("backend", "frontend", "opencode-manager", "opencode")) {
        Remove-Item -LiteralPath (Join-Path $LogDir "$name.log") -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $LogDir "$name.err.log") -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath (Join-Path $LogDir "$name.pid") -Force -ErrorAction SilentlyContinue
    }
    Write-Host "Service logs cleared successfully."
}

function Start-Backend {
    if ([string]::IsNullOrWhiteSpace((Get-EnvValue "TEST_AGENT_OPENCODE_BASE_URL" ""))) {
        throw "TEST_AGENT_OPENCODE_BASE_URL is required in $EnvFile."
    }
    if (-not (Test-Path -LiteralPath $BackendJar -PathType Leaf)) {
        throw "Missing backend jar: $BackendJar. Run without -SkipBackendBuild or build backend first."
    }

    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    $logPath = Join-Path $LogDir "backend.log"
    Write-Host "Starting backend with profile '$BackendProfile'. Logs: $logPath"
    Write-Host "Backend JVM proxy settings are disabled for direct DB/Redis connections."
    $args = @($BackendJavaDirectNetworkArgs + @("-jar", $BackendJar, "--spring.profiles.active=$BackendProfile"))
    $wrapperProcessId = Start-BackgroundCommand -WorkingDirectory $BackendDir -Command "java" -Arguments $args -LogPath $logPath
    Write-PidFile (Join-Path $LogDir "backend.pid") @($wrapperProcessId)
    Wait-UntilHttpOk "backend" "$($script:BackendUrl)/actuator/health/readiness" $logPath 90
    Write-PidFile (Join-Path $LogDir "backend.pid") @(Get-BackendProcessIds)
}

function Get-OpencodeManagerBinaryPath {
    $exePath = Join-ChildPath $RootDir @("opencode-manager", "bin", "opencode-manager.exe")
    if (Test-Path -LiteralPath $exePath -PathType Leaf) {
        return $exePath
    }
    return (Join-ChildPath $RootDir @("opencode-manager", "bin", "opencode-manager"))
}

function Start-OpencodeManager {
    if (-not (Should-StartOpencodeManager)) {
        Write-Host "Skipping opencode-manager startup."
        return
    }

    $opencodeBinary = Get-OpencodeBinary
    if ([string]::IsNullOrWhiteSpace($opencodeBinary) -or -not (Test-Path -LiteralPath $opencodeBinary -PathType Leaf)) {
        throw "opencode binary not found or not executable. Set TEST_AGENT_OPENCODE_BIN in $EnvFile."
    }

    $managerBinary = Get-OpencodeManagerBinaryPath
    if (-not (Test-Path -LiteralPath $managerBinary -PathType Leaf)) {
        throw "opencode-manager binary not found: $managerBinary. Install Go and let this script build it, or build opencode-manager first."
    }

    $portStart = [string](Get-OpencodeManagerPortStart)
    $portEnd = [string](Get-OpencodeManagerPortEnd)
    $containerId = Get-EnvValue "OPENCODE_MANAGER_CONTAINER_ID" ([System.Environment]::MachineName)
    $managerStateDir = Get-OpencodeManagerStateDir
    $backendPort = Get-EnvValue "OPENCODE_MANAGER_BACKEND_PORT" (Get-UrlPort $script:BackendUrl)
    $version = ""
    try {
        $version = (& $opencodeBinary --version 2>$null)
    } catch {
        $version = "opencode unknown"
    }

    New-Item -ItemType Directory -Force -Path $LogDir, $managerStateDir | Out-Null
    Set-EnvValue "OPENCODE_MANAGER_CONTAINER_ID" $containerId
    Set-EnvValue "OPENCODE_MANAGER_BACKEND_PORT" $backendPort
    Set-EnvValue "OPENCODE_MANAGER_PORT_START" $portStart
    Set-EnvValue "OPENCODE_MANAGER_PORT_END" $portEnd
    Set-EnvValue "OPENCODE_MANAGER_TOKEN" (Get-EnvValue "TEST_AGENT_OPENCODE_MANAGER_TOKEN" "")
    Set-EnvValue "OPENCODE_MANAGER_STATE_DIR" $managerStateDir
    Set-EnvValue "OPENCODE_BIN" $opencodeBinary
    Set-EnvValue "OPENCODE_ALLOWED_CORS" "http://localhost:$($script:FrontendPort),http://127.0.0.1:$($script:FrontendPort)"
    Set-EnvValue "OPENCODE_MANAGER_HEARTBEAT_INTERVAL" (Get-EnvValue "OPENCODE_MANAGER_HEARTBEAT_INTERVAL" "5s")
    Set-EnvValue "OPENCODE_MANAGER_RECONNECT_INTERVAL" (Get-EnvValue "OPENCODE_MANAGER_RECONNECT_INTERVAL" "10s")

    $logPath = Join-Path $LogDir "opencode-manager.log"
    Write-Host "Starting opencode-manager for $containerId ($version). Logs: $logPath"
    $wrapperProcessId = Start-BackgroundCommand -WorkingDirectory $RootDir -Command $managerBinary -Arguments @("run") -LogPath $logPath
    Write-PidFile (Join-Path $LogDir "opencode-manager.pid") @($wrapperProcessId)
    Start-Sleep -Seconds 3
    $managerProcessIds = @(Get-OpencodeManagerProcessIds)
    Write-PidFile (Join-Path $LogDir "opencode-manager.pid") $managerProcessIds
    if ($managerProcessIds.Count -eq 0) {
        Write-Stderr "opencode-manager failed to stay running."
        Write-LogTail $logPath
        exit 1
    }
}

function Start-Frontend {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    Set-EnvValue "HOST" $script:FrontendHost
    Set-EnvValue "PORT" $script:FrontendPort
    Set-EnvValue "VITE_TEST_AGENT_API_BASE_URL" $script:BackendUrl

    $logPath = Join-Path $LogDir "frontend.log"
    Write-Host "Starting frontend on $($script:FrontendHost):$($script:FrontendPort). Logs: $logPath"
    $wrapperProcessId = Start-BackgroundCommand -WorkingDirectory $FrontendDir -Command "corepack" -Arguments @("pnpm", "dev") -LogPath $logPath
    Write-PidFile (Join-Path $LogDir "frontend.pid") @($wrapperProcessId)
    Wait-UntilHttpOk "frontend" $script:FrontendUrl $logPath 90
    Write-PidFile (Join-Path $LogDir "frontend.pid") @(Get-FrontendProcessIds)
}

Load-EnvFile $EnvFile
$BackendUrl = Get-EnvValue "TEST_AGENT_BASE_URL" $BackendUrl
$FrontendUrl = Get-EnvValue "TEST_AGENT_FRONTEND_URL" $FrontendUrl

# 通用参数中的 $TEST_AGENT_ROOT 由 Java 进程展开；允许调用方显式覆盖以适配其他工作目录。
Set-EnvValue "TEST_AGENT_ROOT" (Get-EnvValue "TEST_AGENT_ROOT" $RootDir)
Write-Host "TEST_AGENT_ROOT set to: $(Get-EnvValue "TEST_AGENT_ROOT" "")"

Derive-FrontendRuntimeSettings
Apply-FrontendOriginDefaults
Apply-DetectedRuntimeIpDefaults
Apply-ManagerBackendPortDefaults
Set-EnvValue "SPRING_PROFILES_ACTIVE" $BackendProfile

# 开发和测试默认给 opencode-manager 一个与后端共享的 token，避免每次手配本机 dotenv。
if ([string]::IsNullOrWhiteSpace((Get-EnvValue "TEST_AGENT_OPENCODE_MANAGER_TOKEN" ""))) {
    Set-EnvValue "TEST_AGENT_OPENCODE_MANAGER_TOKEN" "local-manager-token"
    Write-Host "Defaulting TEST_AGENT_OPENCODE_MANAGER_TOKEN to local-manager-token for local opencode-manager."
}

Require-Command "corepack"
Require-Command "java"
Require-Command "mvn"

Seed-DemoWorkspaces
Clear-ServiceLogs

Write-Host "Sensitive environment values are loaded but not printed."
Write-Host "Builds run before stopping existing services; failed builds leave current services untouched."

Build-Backend
Build-OpencodeManager
Build-Frontend

# 逐个服务「先 kill 原进程再启动」，按依赖顺序：后端 -> opencode-manager -> 前端。
Stop-BackendService
Start-Backend

Stop-OpencodeManagerService
Start-OpencodeManager

Stop-FrontendService
Start-Frontend

Write-Host "Restart complete."
Write-Host "Backend:  $BackendUrl"
Write-Host "Frontend: $FrontendUrl"
Write-Host "Logs:     $LogDir"
