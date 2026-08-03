[CmdletBinding()]
param(
  [string]$SourceArchive = '',
  [string]$VersionFile = '',
  [string]$SourceChecksums = '',
  [string]$OutputDirectory = (Join-Path (Get-Location) 'lobehub-windows-client-output'),
  [Parameter(Mandatory = $true)]
  [ValidatePattern('^[0-9A-Fa-f]{40}$')]
  [string]$CertificateThumbprint,
  [string]$TimestampUrl = '',
  [switch]$Force
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$KitRoot = Split-Path -Parent $ScriptDirectory
if ([string]::IsNullOrWhiteSpace($VersionFile)) {
  $VersionFile = Join-Path $KitRoot 'version.env'
}
if ([string]::IsNullOrWhiteSpace($SourceChecksums)) {
  $SourceChecksums = Join-Path $KitRoot 'SOURCE_SHA256SUMS'
}

function Get-StateValue {
  param([string]$Path, [string]$Key)

  $matches = @(Get-Content -LiteralPath $Path | ForEach-Object {
      if ($_ -match ('^' + [regex]::Escape($Key) + '=(.+)$')) { $Matches[1] }
    })
  if ($matches.Count -ne 1 -or [string]::IsNullOrWhiteSpace($matches[0])) {
    throw "Expected exactly one non-empty $Key in $Path"
  }
  return $matches[0]
}

function Invoke-Checked {
  param([string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory)

  Push-Location $WorkingDirectory
  try {
    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
      throw "$FilePath failed with exit code $LASTEXITCODE"
    }
  }
  finally {
    Pop-Location
  }
}

function Assert-RegularFile {
  param([string]$Path, [string]$Description)

  if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw "$Description not found: $Path"
  }
  $item = Get-Item -LiteralPath $Path
  if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
    throw "$Description must not be a symbolic link or reparse point: $Path"
  }
  if ($item.Length -le 0) {
    throw "$Description must not be empty: $Path"
  }
}

foreach ($requiredFile in @($VersionFile, $SourceChecksums)) {
  Assert-RegularFile -Path $requiredFile -Description 'Required build-kit file'
}

$internalVersion = Get-StateValue -Path $VersionFile -Key 'LOBEHUB_INTERNAL_VERSION'
$forkCommit = Get-StateValue -Path $VersionFile -Key 'LOBEHUB_FORK_COMMIT'
if ($internalVersion -notmatch '^v\d+\.\d+\.\d+-platform\.\d+$') {
  throw "Invalid internal version: $internalVersion"
}
if ($forkCommit -notmatch '^[0-9a-f]{40}$') {
  throw "Invalid fork commit: $forkCommit"
}

$sourceRelativePath = "source/lobehub-$internalVersion.tar.gz"
if ([string]::IsNullOrWhiteSpace($SourceArchive)) {
  $SourceArchive = Join-Path $KitRoot ($sourceRelativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
}
Assert-RegularFile -Path $SourceArchive -Description 'Locked source archive'

$sourceEntries = @(Get-Content -LiteralPath $SourceChecksums | ForEach-Object {
    if ($_ -match '^([0-9a-f]{64})\s+\*?(\S+)$' -and $Matches[2] -eq $sourceRelativePath) {
      $Matches[1]
    }
  })
if ($sourceEntries.Count -ne 1) {
  throw "Source manifest must contain exactly $sourceRelativePath"
}
$sourceSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $SourceArchive).Hash.ToLowerInvariant()
if ($sourceSha -ne $sourceEntries[0]) {
  throw 'Locked LobeHub source archive checksum mismatch'
}

foreach ($commandName in @('node.exe', 'bun.exe', 'corepack.cmd', 'tar.exe')) {
  if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
    throw "Required command not found: $commandName"
  }
}
if ((& node.exe --version) -ne 'v24.11.1') {
  throw "Node.js v24.11.1 is required, got $(& node.exe --version)"
}
if ((& bun.exe --version) -ne '1.3.2') {
  throw "Bun 1.3.2 is required, got $(& bun.exe --version)"
}

$normalizedThumbprint = $CertificateThumbprint.ToUpperInvariant()
$certificateMatches = @()
foreach ($store in @(
    @{ Path = 'Cert:\CurrentUser\My'; Machine = $false },
    @{ Path = 'Cert:\LocalMachine\My'; Machine = $true }
  )) {
  foreach ($certificate in @(Get-ChildItem -Path $store.Path -ErrorAction SilentlyContinue)) {
    if ($certificate.Thumbprint -eq $normalizedThumbprint) {
      $certificateMatches += [pscustomobject]@{ Certificate = $certificate; Machine = $store.Machine }
    }
  }
}
if ($certificateMatches.Count -ne 1) {
  throw 'The signing thumbprint must resolve to exactly one certificate in CurrentUser/My or LocalMachine/My'
}
$signingCertificate = $certificateMatches[0].Certificate
if (-not $signingCertificate.HasPrivateKey -or $signingCertificate.NotAfter -le (Get-Date)) {
  throw 'The signing certificate must have a private key and must not be expired'
}
$codeSigningOid = '1.3.6.1.5.5.7.3.3'
if (-not @($signingCertificate.EnhancedKeyUsageList | Where-Object { $_.ObjectId.Value -eq $codeSigningOid })) {
  throw 'The selected certificate is not valid for code signing'
}

$signToolCommand = Get-Command signtool.exe -ErrorAction SilentlyContinue
if ($signToolCommand) {
  $signTool = $signToolCommand.Source
}
else {
  $kitsRoot = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
  $signTool = Get-ChildItem -Path $kitsRoot -Filter signtool.exe -Recurse -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -match '\\x64\\signtool\.exe$' } |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($signTool)) {
  throw 'signtool.exe was not found in PATH or the Windows 10/11 SDK'
}

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$outputItem = Get-Item -LiteralPath $OutputDirectory
if (-not $outputItem.PSIsContainer -or ($outputItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
  throw "Output must be a real directory: $OutputDirectory"
}
$outputDirectoryFull = $outputItem.FullName
$clientOutput = Join-Path $outputDirectoryFull 'lobehub-windows-x64.exe'
$evidenceOutput = Join-Path $outputDirectoryFull 'windows-authenticode-verification.txt'
if (-not $Force -and ((Test-Path -LiteralPath $clientOutput) -or (Test-Path -LiteralPath $evidenceOutput))) {
  throw 'Output already exists; use -Force for these exact client output files'
}

$buildDirectory = Join-Path ([IO.Path]::GetTempPath()) ("lobehub-windows-build-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $buildDirectory | Out-Null
try {
  $archivePaths = @(& tar.exe -tzf $SourceArchive)
  if ($LASTEXITCODE -ne 0 -or $archivePaths.Count -eq 0) {
    throw 'Unable to list the locked source archive'
  }
  foreach ($archivePath in $archivePaths) {
    if ($archivePath.StartsWith('/') -or $archivePath -match '(^|/)\.\.($|/)') {
      throw "Unsafe path in source archive: $archivePath"
    }
  }
  Invoke-Checked -FilePath 'tar.exe' -Arguments @('-xzf', $SourceArchive, '-C', $buildDirectory) -WorkingDirectory $buildDirectory
  $sourceRoot = Join-Path $buildDirectory "lobehub-$internalVersion"
  $sourcePackage = Join-Path $sourceRoot 'package.json'
  if (-not (Test-Path -LiteralPath $sourcePackage -PathType Leaf)) {
    throw 'Extracted source package.json is missing'
  }
  if ((Get-Content -LiteralPath $sourcePackage -Raw | ConvertFrom-Json).version -ne $internalVersion.Substring(1)) {
    throw 'Source package version does not match the release lock'
  }

  Invoke-Checked -FilePath 'corepack.cmd' -Arguments @('pnpm@10.33.0', 'install', '--frozen-lockfile', '--node-linker=hoisted') -WorkingDirectory $sourceRoot
  Invoke-Checked -FilePath 'corepack.cmd' -Arguments @(
    'pnpm@10.33.0', '--dir', 'apps/desktop', 'exec', 'vitest', 'run',
    'src/main/const/enterpriseClientPolicy.test.ts',
    'src/main/controllers/__tests__/RemoteServerConfigCtr.test.ts',
    'src/main/controllers/__tests__/AuthCtr.test.ts'
  ) -WorkingDirectory $sourceRoot
  Invoke-Checked -FilePath 'corepack.cmd' -Arguments @(
    'pnpm@10.33.0', '--dir', 'apps/cli', 'exec', 'vitest', 'run',
    'src/enterpriseClientPolicy.test.ts', 'src/program.test.ts',
    'src/commands/login.test.ts', 'src/commands/logout.test.ts', 'src/settings/index.test.ts'
  ) -WorkingDirectory $sourceRoot
  Invoke-Checked -FilePath 'corepack.cmd' -Arguments @(
    'pnpm@10.33.0', 'run', 'workflow:set-desktop-version', $internalVersion.Substring(1), 'stable'
  ) -WorkingDirectory $sourceRoot

  $env:APP_URL = 'http://chat.internal'
  $env:DATABASE_URL = 'postgresql://lobehub-build@127.0.0.1:5432/lobehub'
  $randomBytes = New-Object byte[] 32
  $randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
  try { $randomGenerator.GetBytes($randomBytes) } finally { $randomGenerator.Dispose() }
  $env:KEY_VAULTS_SECRET = [Convert]::ToBase64String($randomBytes)
  $env:LOBEHUB_ENTERPRISE_OFFLINE = '1'
  $env:NEXT_TELEMETRY_DISABLED = '1'
  $env:TELEMETRY_DISABLED = '1'
  $env:UPDATE_CHANNEL = 'stable'
  $env:UPDATE_SERVER_URL = 'http://chat.internal/disabled-updates'
  $env:CSC_IDENTITY_AUTO_DISCOVERY = 'false'

  Invoke-Checked -FilePath 'corepack.cmd' -Arguments @('pnpm@10.33.0', '--dir', 'apps/desktop', 'run', 'build:main') -WorkingDirectory $sourceRoot
  Invoke-Checked -FilePath 'corepack.cmd' -Arguments @(
    'pnpm@10.33.0', '--dir', 'apps/desktop', 'exec', 'electron-builder',
    '--win', 'nsis', '--x64', '--config', 'electron-builder.mjs', '--publish', 'never'
  ) -WorkingDirectory $sourceRoot

  $releaseDirectory = Join-Path $sourceRoot 'apps\desktop\release'
  $installerFiles = @(Get-ChildItem -LiteralPath $releaseDirectory -Filter '*-setup.exe' -File)
  if ($installerFiles.Count -ne 1) {
    throw "Expected exactly one Windows setup EXE, found $($installerFiles.Count)"
  }

  $signArguments = @('sign', '/fd', 'SHA256', '/sha1', $normalizedThumbprint, '/s', 'My')
  if ($certificateMatches[0].Machine) { $signArguments += '/sm' }
  if (-not [string]::IsNullOrWhiteSpace($TimestampUrl)) {
    $signArguments += @('/tr', $TimestampUrl, '/td', 'SHA256')
  }
  $signArguments += $installerFiles[0].FullName
  Invoke-Checked -FilePath $signTool -Arguments $signArguments -WorkingDirectory $releaseDirectory

  $signature = Get-AuthenticodeSignature -LiteralPath $installerFiles[0].FullName
  if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
    throw "Get-AuthenticodeSignature returned $($signature.Status)"
  }
  if ($signature.SignerCertificate.Thumbprint -ne $normalizedThumbprint) {
    throw 'Signed file certificate thumbprint does not match the requested enterprise certificate'
  }

  Copy-Item -LiteralPath $installerFiles[0].FullName -Destination $clientOutput -Force
  $clientSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $clientOutput).Hash.ToLowerInvariant()
  $subject = $signature.SignerCertificate.Subject -replace '[\r\n]+', ' '
  $timestampEvidence = 'NotRequested'
  if (-not [string]::IsNullOrWhiteSpace($TimestampUrl)) { $timestampEvidence = $TimestampUrl }
  $evidenceLines = @(
    'AUTHENTICODE_STATUS=Valid',
    "AUTHENTICODE_SUBJECT=$subject",
    "AUTHENTICODE_THUMBPRINT=$normalizedThumbprint",
    "AUTHENTICODE_FILE_SHA256=$clientSha",
    "LOBEHUB_INTERNAL_VERSION=$internalVersion",
    "LOBEHUB_FORK_COMMIT=$forkCommit",
    'CLIENT_ARCHITECTURE=x64',
    'CLIENT_EXECUTION_MODE=disabled',
    "AUTHENTICODE_TIMESTAMP=$timestampEvidence"
  )
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [IO.File]::WriteAllText($evidenceOutput, (($evidenceLines -join "`n") + "`n"), $utf8NoBom)
}
finally {
  if (Test-Path -LiteralPath $buildDirectory) {
    Remove-Item -LiteralPath $buildDirectory -Recurse -Force
  }
}

Write-Host "Enterprise-signed Windows client: $clientOutput"
Write-Host "Authenticode evidence: $evidenceOutput"
