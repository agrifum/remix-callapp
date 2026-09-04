$ErrorActionPreference = "Stop"

# CallUpp Night Runner Read-Only Environment Preflight
# Validates host prerequisites for unattended overnight execution without modifying system configuration.

$isWindows = [System.Environment]::OSVersion.Platform -match "Win"
if (-not $isWindows) {
    Write-Host "RESULT: BLOCKED"
    Write-Host "USER_ACTION_REQUIRED: Host platform must be Windows."
    exit 1
}

$blockedReasons = @()
$userActions = @()

# 1. AC Power Check
$acOnline = $false
try {
    $wmiBatt = Get-CimInstance -Namespace root/wmi -ClassName BatteryStatus -ErrorAction SilentlyContinue
    if ($wmiBatt) {
        $acOnline = [bool]$wmiBatt.PowerOnline
    } else {
        # Desktop or systems without battery WMI
        $batt = Get-CimInstance -ClassName Win32_Battery -ErrorAction SilentlyContinue
        if ($null -eq $batt) {
            $acOnline = $true # Desktop without battery
        } else {
            $acOnline = ($batt.BatteryStatus -ne 1)
        }
    }
} catch {
    $acOnline = $false
}

$acPowerReport = if ($acOnline) { "CONNECTED (PASS)" } else { "DISCONNECTED (FAIL)" }
if (-not $acOnline) {
    $blockedReasons += "AC power is disconnected (running on battery)"
    $userActions += "Connect laptop charger to AC mains."
}

# 2. Power Timeouts Check (Sleep & Hibernate on AC must be 0)
$acSleepSec = -1
$acHibernateSec = -1
$acDisplaySec = -1
try {
    $standbyOut = (powercfg /q SCHEME_CURRENT SUB_SLEEP STANDBYIDLE) -join "`n"
    if ($standbyOut -match "Current AC Power Setting Index:\s*0x([0-9a-fA-F]+)") {
        $acSleepSec = [Convert]::ToInt32($Matches[1], 16)
    }

    $hibernateOut = (powercfg /q SCHEME_CURRENT SUB_SLEEP HIBERNATEIDLE) -join "`n"
    if ($hibernateOut -match "Current AC Power Setting Index:\s*0x([0-9a-fA-F]+)") {
        $acHibernateSec = [Convert]::ToInt32($Matches[1], 16)
    }

    $videoOut = (powercfg /q SCHEME_CURRENT SUB_VIDEO VIDEOIDLE) -join "`n"
    if ($videoOut -match "Current AC Power Setting Index:\s*0x([0-9a-fA-F]+)") {
        $acDisplaySec = [Convert]::ToInt32($Matches[1], 16)
    }
} catch {
    $blockedReasons += "Failed to query powercfg settings"
}

$acSleepReport = if ($acSleepSec -eq 0) { "NEVER (0s) (PASS)" } else { "$($acSleepSec)s (FAIL - must be 0)" }
if ($acSleepSec -ne 0) {
    $blockedReasons += "AC sleep timeout is not 0 (Never)"
    $userActions += "Set AC sleep to Never: powercfg /change standby-timeout-ac 0"
}

$acHibernateReport = if ($acHibernateSec -eq 0) { "NEVER (0s) (PASS)" } else { "$($acHibernateSec)s (FAIL - must be 0)" }
if ($acHibernateSec -ne 0) {
    $blockedReasons += "AC hibernate timeout is not 0 (Never)"
    $userActions += "Set AC hibernate to Never: powercfg /change hibernate-timeout-ac 0"
}

# 3. Lid-Close Action
$lidReport = "NOT APPLICABLE"
try {
    $lidOut = (powercfg /qh SCHEME_CURRENT SUB_BUTTONS 5ca83367-6e45-459f-a27b-476b1d01c936 2>$null) -join "`n"
    if ($lidOut -match "Current AC Power Setting Index:\s*0x([0-9a-fA-F]+)") {
        $lidIndex = [Convert]::ToInt32($Matches[1], 16)
        if ($lidIndex -eq 1) {
            $lidReport = "SLEEP - KEEP LID OPEN (WARNING)"
        } elseif ($lidIndex -eq 0) {
            $lidReport = "DO NOTHING (PASS)"
        } else {
            $lidReport = "INDEX $lidIndex - KEEP LID OPEN (WARNING)"
        }
    }
} catch {
    $lidReport = "UNKNOWN"
}

# 4. Disk Space Check (>= 10 GB)
$repoRoot = (Resolve-Path "$PSScriptRoot/..").Path
$repoDrive = (Get-Item $repoRoot).PSDrive
$localAppDataDrive = (Get-Item $env:LOCALAPPDATA).PSDrive

$repoFreeGB = [math]::Round($repoDrive.Free / 1GB, 2)
$localAppFreeGB = [math]::Round($localAppDataDrive.Free / 1GB, 2)

$diskReport = "Repo ($($repoDrive.Name):): ${repoFreeGB}GB; Runtime ($($localAppDataDrive.Name):): ${localAppFreeGB}GB"
if ($repoFreeGB -lt 10 -or $localAppFreeGB -lt 10) {
    $blockedReasons += "Insufficient free disk space (minimum 10 GB required)"
    $userActions += "Free at least 10 GB on drives $($repoDrive.Name): and $($localAppDataDrive.Name):"
    $diskReport += " (FAIL)"
} else {
    $diskReport += " (PASS)"
}

# 5. Network Check (github.com port 443)
$netOk = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $iar = $tcp.BeginConnect("github.com", 443, $null, $null)
    $success = $iar.AsyncWaitHandle.WaitOne(5000, $false)
    if ($success -and $tcp.Connected) {
        $tcp.EndConnect($iar)
        $netOk = $true
    }
    $tcp.Close()
} catch {
    $netOk = $false
}
$netReport = if ($netOk) { "CONNECTIVITY OK (PASS)" } else { "UNREACHABLE (FAIL)" }
if (-not $netOk) {
    $blockedReasons += "Cannot establish TCP connection to github.com:443"
    $userActions += "Check internet connection and firewall access to github.com."
}

# 6. Windows Pending Reboot Check
$rebootWU = Test-Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\WindowsUpdate\Auto Update\RebootRequired'
$rebootCBS = Test-Path 'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Component Based Servicing\RebootPending'
$restartReport = if (-not $rebootWU -and -not $rebootCBS) { "NONE (PASS)" } else { "PENDING REBOOT (FAIL)" }
if ($rebootWU -or $rebootCBS) {
    $blockedReasons += "Pending Windows restart / reboot required"
    $userActions += "Restart the computer to complete pending Windows updates."
}

# 7. Git Availability
$gitCmd = Get-Command git -ErrorAction SilentlyContinue
$gitReport = if ($gitCmd) { "AVAILABLE ($((git --version).Trim())) (PASS)" } else { "MISSING (FAIL)" }
if (-not $gitCmd) {
    $blockedReasons += "git CLI is missing from PATH"
    $userActions += "Install Git for Windows and ensure it is on PATH."
}

# 8. GitHub CLI (gh) Discovery & Auth Check
$ghCmd = Get-Command gh -ErrorAction SilentlyContinue
if (-not $ghCmd) {
    # Check known local installation directories
    $copilotGh = Get-ChildItem "$env:LOCALAPPDATA\copilot-desktop-gh-*\gh.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($copilotGh -and (Test-Path $copilotGh.FullName)) {
        $env:PATH = "$($copilotGh.DirectoryName);$env:PATH"
        $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
    } elseif (Test-Path "$env:ProgramFiles\GitHub CLI\gh.exe") {
        $env:PATH = "$env:ProgramFiles\GitHub CLI;$env:PATH"
        $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
    }
}
$ghReport = if ($ghCmd) { "AVAILABLE ($($ghCmd.Source)) (PASS)" } else { "MISSING (FAIL)" }
if (-not $ghCmd) {
    $blockedReasons += "gh CLI is missing"
    $userActions += "Install GitHub CLI (gh) and ensure it is discoverable."
}

# 9. GitHub Auth Check
$ghAuthOk = $false
if ($ghCmd) {
    # First ensure GH_TOKEN is populated from GCM if not already set
    if (-not $env:GH_TOKEN) {
        try {
            $pinfo = New-Object System.Diagnostics.ProcessStartInfo
            $pinfo.FileName = "git"
            $pinfo.Arguments = "credential fill"
            $pinfo.RedirectStandardInput = $true
            $pinfo.RedirectStandardOutput = $true
            $pinfo.UseShellExecute = $false
            $p = [System.Diagnostics.Process]::Start($pinfo)
            $p.StandardInput.WriteLine("protocol=https`nhost=github.com`n")
            $p.StandardInput.Close()
            $credOut = $p.StandardOutput.ReadToEnd()
            $p.WaitForExit()
            $tokenMatch = $credOut -split "`n" | Where-Object { $_ -match "^password=(.*)" } | ForEach-Object { $Matches[1] }
            if ($tokenMatch) {
                $env:GH_TOKEN = $tokenMatch.Trim()
            }
        } catch {}
    }

    try {
        $ghProcInfo = New-Object System.Diagnostics.ProcessStartInfo
        $ghProcInfo.FileName = $ghCmd.Source
        $ghProcInfo.Arguments = "auth status"
        $ghProcInfo.RedirectStandardOutput = $true
        $ghProcInfo.RedirectStandardError = $true
        $ghProcInfo.UseShellExecute = $false
        $ghProc = [System.Diagnostics.Process]::Start($ghProcInfo)
        $ghProc.WaitForExit()
        if ($ghProc.ExitCode -eq 0) {
            $ghAuthOk = $true
        }
    } catch {}
}
$ghAuthReport = if ($ghAuthOk) { "AUTHENTICATED (PASS)" } else { "NOT AUTHENTICATED (FAIL)" }
if (-not $ghAuthOk -and $ghCmd) {
    $blockedReasons += "GitHub CLI is not authenticated for github.com"
    $userActions += "Authenticate GitHub CLI via 'gh auth login' or ensure GCM credentials are valid."
}

# 10. GCM Discovery
$gcmFound = $false
$gcmPath = "C:\Program Files\Git\mingw64\bin\git-credential-manager.exe"
if (Test-Path $gcmPath) {
    $gcmFound = $true
} else {
    $gcmCmd = Get-Command git-credential-manager -ErrorAction SilentlyContinue
    if ($gcmCmd) { $gcmFound = $true; $gcmPath = $gcmCmd.Source }
}
$gcmReport = if ($gcmFound) { "DISCOVERED ($gcmPath) (PASS)" } else { "NOT FOUND (WARNING)" }

# 11. Antigravity CLI (agy)
$agyCmd = Get-Command agy -ErrorAction SilentlyContinue
$agyReport = if ($agyCmd) { "AVAILABLE ($($agyCmd.Source)) (PASS)" } else { "MISSING (FAIL)" }
if (-not $agyCmd) {
    $blockedReasons += "agy CLI is missing from PATH"
    $userActions += "Ensure Antigravity CLI (agy) is installed and available on PATH."
}

# 12. Headless Canary Test
$canaryOk = $false
if ($agyCmd) {
    try {
        $canaryJson = agy -p "Reply with exactly CALLUPP_HEADLESS_OK. Do not use tools." --output-format json --print-timeout 2m 2>$null
        if ($canaryJson) {
            $parsed = $canaryJson | ConvertFrom-Json -ErrorAction SilentlyContinue
            if ($parsed -and $parsed.status -eq "SUCCESS" -and $parsed.response -match "CALLUPP_HEADLESS_OK") {
                $canaryOk = $true
            }
        }
    } catch {
        $canaryOk = $false
    }
}
$canaryReport = if ($canaryOk) { "CANARY PASSED (CALLUPP_HEADLESS_OK) (PASS)" } else { "FAILED (FAIL)" }
if (-not $canaryOk -and $agyCmd) {
    $blockedReasons += "agy headless authentication canary failed"
    $userActions += "Run 'agy' interactively once to verify cached session authentication."
}

# Final Summary Output
$isReady = ($blockedReasons.Count -eq 0)
$resultStr = if ($isReady) { "READY" } else { "BLOCKED" }

Write-Host "RESULT: $resultStr"
Write-Host "AC_POWER: $acPowerReport"
Write-Host "AC_SLEEP: $acSleepReport"
Write-Host "AC_HIBERNATE: $acHibernateReport"
Write-Host "LID: $lidReport"
Write-Host "DISK: $diskReport"
Write-Host "NETWORK: $netReport"
Write-Host "WINDOWS_RESTART: $restartReport"
Write-Host "GIT: $gitReport"
Write-Host "GH: $ghReport"
Write-Host "GITHUB_AUTH: $ghAuthReport"
Write-Host "GCM: $gcmReport"
Write-Host "AGY: $agyReport"
Write-Host "AGY_HEADLESS: $canaryReport"

if ($userActions.Count -gt 0) {
    Write-Host "USER_ACTION_REQUIRED: $($userActions -join '; ')"
} else {
    Write-Host "USER_ACTION_REQUIRED: NONE"
}

if ($isReady) {
    exit 0
} else {
    exit 1
}
