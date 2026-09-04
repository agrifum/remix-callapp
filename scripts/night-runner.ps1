[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$QueuePath,

    [Parameter(Mandatory = $false)]
    [switch]$SelfTest
)

$ErrorActionPreference = "Stop"

# CallUpp Deterministic Night Runner
# Executes pre-approved independent night packs sequentially in isolated Git worktrees.

$RepoRoot = (Resolve-Path "$PSScriptRoot/..").Path
Set-Location $RepoRoot

# Helper: Ensure gh is in PATH if present in standard or discovered location
function Ensure-GhCli {
    $cmd = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $cmd) {
        $copilotGh = Get-ChildItem "$env:LOCALAPPDATA\copilot-desktop-gh-*\gh.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($copilotGh -and (Test-Path $copilotGh.FullName)) {
            $env:PATH = "$($copilotGh.DirectoryName);$env:PATH"
        } elseif (Test-Path "$env:ProgramFiles\GitHub CLI\gh.exe") {
            $env:PATH = "$env:ProgramFiles\GitHub CLI;$env:PATH"
        }
    }
}

# Helper: Ensure GH_TOKEN is bridged from GCM for the current process
function Ensure-GhToken {
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
}

Ensure-GhCli
Ensure-GhToken

# ==============================================================================
# SELF TEST MODE
# ==============================================================================
if ($SelfTest) {
    Write-Host "=== NIGHT RUNNER SELF-TEST MODE ==="

    # 1. Run Preflight
    Write-Host "[SelfTest 1/10] Running night-preflight.ps1..."
    $preflightProcInfo = New-Object System.Diagnostics.ProcessStartInfo
    $preflightProcInfo.FileName = "powershell.exe"
    $preflightProcInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot\night-preflight.ps1`""
    $preflightProcInfo.UseShellExecute = $false
    $preflightProc = [System.Diagnostics.Process]::Start($preflightProcInfo)
    $preflightProc.WaitForExit()
    if ($preflightProc.ExitCode -ne 0) {
        Write-Host "SELF_TEST: BLOCKED (Preflight failed with exit code $($preflightProc.ExitCode))"
        exit 1
    }

    # 2. Prepare temporary runtime directory
    $selfTestId = "selftest_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    $runtimeRoot = Join-Path $env:LOCALAPPDATA "CallUpp\night-runner"
    $selfTestDir = Join-Path $runtimeRoot "runs\$selfTestId"
    $selfTestWorktree = Join-Path $runtimeRoot "worktrees\$selfTestId"
    [System.IO.Directory]::CreateDirectory($selfTestDir) | Out-Null

    try {
        # 3. Resolve current origin/main
        Write-Host "[SelfTest 2/10] Resolving origin/main base SHA..."
        $baseSha = (git rev-parse origin/main).Trim()
        if (-not $baseSha -or $baseSha.Length -ne 40) {
            Write-Host "SELF_TEST: BLOCKED (Cannot resolve valid 40-char SHA for origin/main)"
            exit 1
        }
        Write-Host "Base SHA: $baseSha"

        # 4. Create detached temporary worktree
        Write-Host "[SelfTest 3/10] Creating detached temporary Git worktree at $selfTestWorktree..."
        if (Test-Path $selfTestWorktree) {
            git worktree remove --force $selfTestWorktree 2>$null
        }
        & git worktree add --detach $selfTestWorktree $baseSha
        if ($LASTEXITCODE -ne 0) {
            Write-Host "SELF_TEST: BLOCKED (Failed to create detached worktree)"
            exit 1
        }

        # 5. Place transient self-test prompt
        Write-Host "[SelfTest 4/10] Writing transient task prompt..."
        $taskPromptPath = Join-Path $selfTestWorktree ".callupp-night-task.md"
        $taskPromptContent = @"
# Self-Test Task Prompt
Execute read-only verification:
1. Run \`git rev-parse HEAD\` and report the SHA.
2. Run \`git status --short\` to verify working tree status.
3. Make NO file changes, edits, additions, or deletions.
4. The file .callupp-night-task.md is a transient runner artifact.
"@
        [System.IO.File]::WriteAllText($taskPromptPath, $taskPromptContent, [System.Text.Encoding]::UTF8)

        # 6. Invoke agy -p headless
        Write-Host "[SelfTest 5/10] Invoking fresh headless agy agent..."
        $wrapperInstruction = "Read .callupp-night-task.md completely. Run git rev-parse HEAD and git status --short. Report the observed SHA. Make no changes and modify no files. The file .callupp-night-task.md is a transient runner artifact."

        $stdoutPath = Join-Path $selfTestDir "stdout.log"
        $stderrPath = Join-Path $selfTestDir "stderr.log"

        $agyProcInfo = New-Object System.Diagnostics.ProcessStartInfo
        $agyProcInfo.FileName = "agy"
        $agyProcInfo.Arguments = "-p `"$wrapperInstruction`" --output-format json --print-timeout 3m"
        $agyProcInfo.WorkingDirectory = $selfTestWorktree
        $agyProcInfo.RedirectStandardOutput = $true
        $agyProcInfo.RedirectStandardError = $true
        $agyProcInfo.UseShellExecute = $false

        $agyProc = [System.Diagnostics.Process]::Start($agyProcInfo)
        $stdout = $agyProc.StandardOutput.ReadToEnd()
        $stderr = $agyProc.StandardError.ReadToEnd()
        $agyProc.WaitForExit()

        [System.IO.File]::WriteAllText($stdoutPath, $stdout, [System.Text.Encoding]::UTF8)
        [System.IO.File]::WriteAllText($stderrPath, $stderr, [System.Text.Encoding]::UTF8)

        # 7. Verify headless run status SUCCESS
        Write-Host "[SelfTest 6/10] Evaluating headless agent output..."
        $agentSuccess = $false
        try {
            $parsedJson = $stdout | ConvertFrom-Json -ErrorAction Stop
            if ($parsedJson.status -eq "SUCCESS") {
                $agentSuccess = $true
            } else {
                Write-Host "Agent run status was not SUCCESS: $($parsedJson.status)"
            }
        } catch {
            Write-Host "Failed to parse agent JSON output: $($_.Exception.Message)"
        }

        if (-not $agentSuccess) {
            # Check for permission-denial signature
            if ($stdout -match "permission|approval|denied" -or $stderr -match "permission|approval|denied") {
                Write-Host "SELF_TEST: BLOCKED (Antigravity permissions require interactive approval for git execution)"
            } else {
                Write-Host "SELF_TEST: BLOCKED (Headless agent execution failed: exit code $($agyProc.ExitCode))"
            }
            exit 1
        }

        # 8. Remove prompt artifact and verify worktree remains clean
        Write-Host "[SelfTest 7/10] Verifying worktree state..."
        if (Test-Path $taskPromptPath) {
            Remove-Item -Force $taskPromptPath
        }

        Set-Location $selfTestWorktree
        $statusOut = (git status --short)
        Set-Location $RepoRoot

        if ($statusOut -and $statusOut.Trim().Length -gt 0) {
            Write-Host "SELF_TEST: BLOCKED (Worktree dirty after agent run: $statusOut)"
            exit 1
        }

        # 9. Clean up worktree
        Write-Host "[SelfTest 8/10] Removing self-test worktree..."
        Start-Sleep -Seconds 2
        & git worktree remove --force $selfTestWorktree 2>$null
        if (Test-Path $selfTestWorktree) {
            Start-Sleep -Seconds 1
            Remove-Item -Recurse -Force $selfTestWorktree -ErrorAction SilentlyContinue
        }

        # 10. Clean up temporary files
        Write-Host "[SelfTest 9/10] Cleaning up temporary run files..."
        Remove-Item -Recurse -Force $selfTestDir -ErrorAction SilentlyContinue

        Write-Host "[SelfTest 10/10] Reporting final result..."
        Write-Host "SELF_TEST: PASS"
        exit 0
    } catch {
        Set-Location $RepoRoot
        Write-Host "SELF_TEST: BLOCKED ($($_.Exception.Message))"
        if (Test-Path $selfTestWorktree) {
            & git worktree remove --force $selfTestWorktree 2>$null
        }
        exit 1
    }
}

# ==============================================================================
# PRODUCTION QUEUE EXECUTION MODE
# ==============================================================================
if (-not $QueuePath) {
    Write-Error "QueuePath parameter is required when not running with -SelfTest."
    exit 1
}

# 1. Resolve and Validate Queue Path
if (-not (Test-Path $QueuePath)) {
    Write-Error "Queue file not found: $QueuePath"
    exit 1
}

$ResolvedQueuePath = (Resolve-Path $QueuePath).Path
$PackDir = Split-Path -Parent $ResolvedQueuePath

Write-Host "=== CALLUPP NIGHT RUNNER ==="
Write-Host "Queue Manifest: $ResolvedQueuePath"
Write-Host "Pack Directory: $PackDir"

# Read UTF-8 text and parse JSON
$rawJson = [System.IO.File]::ReadAllText($ResolvedQueuePath, [System.Text.Encoding]::UTF8)
try {
    $queue = $rawJson | ConvertFrom-Json -ErrorAction Stop
} catch {
    Write-Error "Queue JSON is malformed: $($_.Exception.Message)"
    exit 1
}

# Schema validation: required top-level fields
$topAllowed = @("version", "pack_id", "base_main_sha", "created_at", "tasks")
$topProps = $queue.PSObject.Properties.Name
foreach ($p in $topProps) {
    if ($p -notin $topAllowed) {
        Write-Error "Unknown top-level property in queue: $p"
        exit 1
    }
}
foreach ($req in $topAllowed) {
    if ($req -notin $topProps) {
        Write-Error "Missing required top-level property: $req"
        exit 1
    }
}

if ($queue.version -ne 1) {
    Write-Error "Unsupported queue version: $($queue.version) (expected 1)"
    exit 1
}
if (-not $queue.pack_id -or $queue.pack_id.Trim().Length -eq 0) {
    Write-Error "pack_id must be a non-empty string."
    exit 1
}
if ($queue.base_main_sha -notmatch "^[0-9a-f]{40}$") {
    Write-Error "base_main_sha must be exactly 40 lowercase hexadecimal characters."
    exit 1
}
if (-not $queue.created_at -or $queue.created_at.Trim().Length -eq 0) {
    Write-Error "created_at must be a non-empty string."
    exit 1
}
if ($null -eq $queue.tasks -or $queue.tasks.Count -lt 1 -or $queue.tasks.Count -gt 6) {
    Write-Error "tasks array must contain between 1 and 6 tasks."
    exit 1
}

# Validate tasks and check for duplicates
$taskAllowed = @("order", "task_id", "phase", "prompt_id", "review_id", "review_status", "authorization", "night_compatible", "independent", "branch", "prompt_file", "timeout_minutes")
$orders = @()
$taskIds = @()
$promptIds = @()
$branches = @()

foreach ($t in $queue.tasks) {
    $tProps = $t.PSObject.Properties.Name
    foreach ($tp in $tProps) {
        if ($tp -notin $taskAllowed) {
            Write-Error "Unknown task property in task $($t.task_id): $tp"
            exit 1
        }
    }
    foreach ($req in $taskAllowed) {
        if ($req -notin $tProps) {
            Write-Error "Missing required task property in task: $req"
            exit 1
        }
    }

    if ($t.order -lt 1) { Write-Error "Task order must be >= 1."; exit 1 }
    if ($t.order -in $orders) { Write-Error "Duplicate task order: $($t.order)"; exit 1 }
    $orders += $t.order

    if (-not $t.task_id -or $t.task_id.Trim().Length -eq 0) { Write-Error "task_id must be non-empty."; exit 1 }
    if ($t.task_id -in $taskIds) { Write-Error "Duplicate task_id: $($t.task_id)"; exit 1 }
    $taskIds += $t.task_id

    if (-not $t.prompt_id -or $t.prompt_id.Trim().Length -eq 0) { Write-Error "prompt_id must be non-empty."; exit 1 }
    if ($t.prompt_id -in $promptIds) { Write-Error "Duplicate prompt_id: $($t.prompt_id)"; exit 1 }
    $promptIds += $t.prompt_id

    if (-not $t.branch -or $t.branch.Trim().Length -eq 0) { Write-Error "branch must be non-empty."; exit 1 }
    if ($t.branch -in $branches) { Write-Error "Duplicate branch: $($t.branch)"; exit 1 }
    $branches += $t.branch

    if ($t.review_status -ne "PASS") { Write-Error "Task $($t.task_id) review_status must be 'PASS'."; exit 1 }
    if ($t.authorization -ne "READY") { Write-Error "Task $($t.task_id) authorization must be 'READY'."; exit 1 }
    if ($t.night_compatible -ne $true) { Write-Error "Task $($t.task_id) night_compatible must be true."; exit 1 }
    if ($t.independent -ne $true) { Write-Error "Task $($t.task_id) independent must be true."; exit 1 }

    if ($t.timeout_minutes -lt 10 -or $t.timeout_minutes -gt 180) {
        Write-Error "Task $($t.task_id) timeout_minutes must be between 10 and 180."
        exit 1
    }

    # Prompt file validation
    $pf = $t.prompt_file
    if ([System.IO.Path]::IsPathRooted($pf) -or $pf -match "^\.\." -or $pf -match "[/\\]\.\.") {
        Write-Error "Task $($t.task_id) prompt_file must be a relative path without traversal."
        exit 1
    }
    $resolvedPromptFile = Join-Path $PackDir $pf
    if (-not (Test-Path $resolvedPromptFile)) {
        Write-Error "Prompt file for task $($t.task_id) does not exist: $resolvedPromptFile"
        exit 1
    }
    $promptInfo = Get-Item $resolvedPromptFile
    if ($promptInfo.Length -eq 0) {
        Write-Error "Prompt file for task $($t.task_id) is empty: $resolvedPromptFile"
        exit 1
    }
}

# 2. Base Validation against origin/main
Write-Host "Fetching origin/main to verify base commit..."
& git fetch origin main --prune
$currentOriginMain = (git rev-parse origin/main).Trim()
if ($currentOriginMain -ne $queue.base_main_sha) {
    Write-Error "GLOBAL BLOCKER: origin/main ($currentOriginMain) differs from queue base_main_sha ($($queue.base_main_sha))."
    exit 1
}
Write-Host "Base SHA verified: $currentOriginMain (matches queue)"

# 3. Environment Preflight
Write-Host "Running environment preflight..."
$preflightProcInfo = New-Object System.Diagnostics.ProcessStartInfo
$preflightProcInfo.FileName = "powershell.exe"
$preflightProcInfo.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$PSScriptRoot\night-preflight.ps1`""
$preflightProcInfo.UseShellExecute = $false
$preflightProc = [System.Diagnostics.Process]::Start($preflightProcInfo)
$preflightProc.WaitForExit()
if ($preflightProc.ExitCode -ne 0) {
    Write-Error "GLOBAL BLOCKER: Environment preflight failed. Night runner aborted."
    exit 1
}

# 4. Prepare Runtime Directories
$runtimeRoot = Join-Path $env:LOCALAPPDATA "CallUpp\night-runner"
$packRunDir = Join-Path $runtimeRoot "runs\$($queue.pack_id)"
$worktreesRoot = Join-Path $runtimeRoot "worktrees"

[System.IO.Directory]::CreateDirectory($packRunDir) | Out-Null
[System.IO.Directory]::CreateDirectory($worktreesRoot) | Out-Null

$startedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$taskResults = @()

# Sort tasks by order
$sortedTasks = $queue.tasks | Sort-Object -Property order

foreach ($task in $sortedTasks) {
    Write-Host "`n========================================================"
    Write-Host "EXECUTING TASK $($task.order)/$($sortedTasks.Count): $($task.task_id) (Phase: $($task.phase))"
    Write-Host "========================================================"

    $sanitizedTaskId = $task.task_id -replace '[^a-zA-Z0-9_\-]', '_'
    $taskWorktreePath = Join-Path $worktreesRoot $sanitizedTaskId
    $taskLogDir = Join-Path $packRunDir $sanitizedTaskId
    [System.IO.Directory]::CreateDirectory($taskLogDir) | Out-Null

    $taskRecord = [ordered]@{
        order = $task.order
        task_id = $task.task_id
        prompt_id = $task.prompt_id
        phase = $task.phase
        branch = $task.branch
        result = "BLOCKED"
        final_head = $null
        pr_number = $null
        pr_url = $null
        ci = "NOT_RUN"
        worktree_retained = $false
        worktree_path = $null
        log_directory = $taskLogDir
        reason = ""
    }

    # Pre-task collision check
    $remoteBranchOut = & git ls-remote --heads origin $task.branch
    $remoteBranchExists = ($null -ne $remoteBranchOut -and "$remoteBranchOut".Trim().Length -gt 0)
    $localBranchOut = & git branch --list $task.branch
    $localBranchExists = ($null -ne $localBranchOut -and "$localBranchOut".Trim().Length -gt 0)
    $openPrJson = & gh pr list --head $task.branch --state open --json number 2>$null
    $openPrCount = 0
    if ($openPrJson) {
        $parsedPrs = $openPrJson | ConvertFrom-Json -ErrorAction SilentlyContinue
        if ($parsedPrs) { $openPrCount = $parsedPrs.Count }
    }
    $worktreeDirExists = Test-Path $taskWorktreePath

    if ($remoteBranchExists -or $localBranchExists -or $openPrCount -gt 0 -or $worktreeDirExists) {
        $taskRecord.reason = "Precheck collision: RemoteBranchExists=$remoteBranchExists, LocalBranchExists=$localBranchExists, OpenPRs=$openPrCount, WorktreeDirExists=$worktreeDirExists"
        Write-Host "TASK BLOCKED: $($taskRecord.reason)"
        $taskResults += [PSCustomObject]$taskRecord
        continue
    }

    # Create isolated Git worktree from exact base SHA
    Write-Host "Creating worktree at $taskWorktreePath from $($queue.base_main_sha)..."
    & git worktree add -b $task.branch $taskWorktreePath $queue.base_main_sha
    if ($LASTEXITCODE -ne 0) {
        $taskRecord.reason = "Failed to create worktree."
        $taskResults += [PSCustomObject]$taskRecord
        continue
    }

    $taskRecord.worktree_path = $taskWorktreePath
    $taskRecord.worktree_retained = $true

    try {
        # Copy approved prompt into worktree as transient .callupp-night-task.md
        $sourcePromptFile = Join-Path $PackDir $task.prompt_file
        $destPromptFile = Join-Path $taskWorktreePath ".callupp-night-task.md"
        Copy-Item -Path $sourcePromptFile -Destination $destPromptFile -Force

        $wrapperInstruction = "Read .callupp-night-task.md completely and execute it as the authoritative task prompt. The Night Runner has already created the isolated worktree and branch from the exact task base. If the inner prompt asks to create the worktree or branch, treat that setup action as already satisfied after verifying current branch and SHA. The file .callupp-night-task.md is a transient runner artifact and must not be staged, committed, modified, or treated as an out-of-scope source change. All other scope, file allowlists, stop conditions, and handoff requirements in the inner prompt remain binding. Do not merge, do not start the next phase, and return a concise final handoff."

        Write-Host "Invoking Antigravity headless agent (Timeout: $($task.timeout_minutes)m)..."
        $stdoutFile = Join-Path $taskLogDir "stdout.log"
        $stderrFile = Join-Path $taskLogDir "stderr.log"

        $agyInfo = New-Object System.Diagnostics.ProcessStartInfo
        $agyInfo.FileName = "agy"
        $agyInfo.Arguments = "-p `"$wrapperInstruction`" --output-format json --print-timeout `"$($task.timeout_minutes)m`""
        $agyInfo.WorkingDirectory = $taskWorktreePath
        $agyInfo.RedirectStandardOutput = $true
        $agyInfo.RedirectStandardError = $true
        $agyInfo.UseShellExecute = $false

        $agyProc = [System.Diagnostics.Process]::Start($agyInfo)
        $stdout = $agyProc.StandardOutput.ReadToEnd()
        $stderr = $agyProc.StandardError.ReadToEnd()
        $agyProc.WaitForExit()

        [System.IO.File]::WriteAllText($stdoutFile, $stdout, [System.Text.Encoding]::UTF8)
        [System.IO.File]::WriteAllText($stderrFile, $stderr, [System.Text.Encoding]::UTF8)

        # Remove transient prompt file
        if (Test-Path $destPromptFile) {
            Remove-Item -Force $destPromptFile
        }

        # Check headless result
        $agentPassed = $false
        try {
            $parsed = $stdout | ConvertFrom-Json -ErrorAction Stop
            if ($parsed.status -eq "SUCCESS") {
                $agentPassed = $true
            } else {
                $taskRecord.reason = "Agent status was $($parsed.status)"
            }
        } catch {
            $taskRecord.reason = "Failed to parse agent JSON output: $($_.Exception.Message)"
        }

        if (-not $agentPassed) {
            Write-Host "TASK BLOCKED: $($taskRecord.reason)"
            $taskResults += [PSCustomObject]$taskRecord
            continue
        }

        # Verify worktree clean
        Set-Location $taskWorktreePath
        $localStatus = (git status --short)
        $localHead = (git rev-parse HEAD).Trim()
        Set-Location $RepoRoot

        if ($localStatus -and $localStatus.Trim().Length -gt 0) {
            $taskRecord.reason = "Task worktree has uncommitted/untracked changes after agent run."
            Write-Host "TASK BLOCKED: $($taskRecord.reason)"
            $taskResults += [PSCustomObject]$taskRecord
            continue
        }
        $taskRecord.final_head = $localHead

        # Check GitHub PR
        Write-Host "Locating PR for branch $($task.branch)..."
        $prsJson = & gh pr list --head $task.branch --base main --state open --json number,url,headRefOid,state 2>$null
        $prs = if ($prsJson) { $prsJson | ConvertFrom-Json -ErrorAction SilentlyContinue } else { @() }

        if ($null -eq $prs -or $prs.Count -ne 1) {
            $taskRecord.reason = "Expected exactly 1 open PR for branch $($task.branch), found $($prs.Count)"
            Write-Host "TASK BLOCKED: $($taskRecord.reason)"
            $taskResults += [PSCustomObject]$taskRecord
            continue
        }

        $pr = $prs[0]
        $taskRecord.pr_number = $pr.number
        $taskRecord.pr_url = $pr.url

        if ($pr.headRefOid -ne $localHead) {
            $taskRecord.reason = "PR headRefOid ($($pr.headRefOid)) does not match local HEAD ($localHead)"
            Write-Host "TASK BLOCKED: $($taskRecord.reason)"
            $taskResults += [PSCustomObject]$taskRecord
            continue
        }

        # Monitor CI
        Write-Host "Monitoring GitHub Actions CI for PR #$($pr.number)..."
        $ciOut = & gh pr checks $pr.number --watch --fail-fast --interval 10 2>&1
        if ($LASTEXITCODE -eq 0) {
            $taskRecord.ci = "PASS"
            $taskRecord.result = "CANDIDATE_FOR_CONTROL_PLANE_REVIEW"
            $taskRecord.reason = "All checks passed. Ready for Control Plane review."
            Write-Host "TASK SUCCESS: CANDIDATE_FOR_CONTROL_PLANE_REVIEW"

            # Clean up completed worktree
            Write-Host "Cleaning up completed worktree..."
            Start-Sleep -Seconds 2
            & git worktree remove $taskWorktreePath 2>$null
            if (Test-Path $taskWorktreePath) {
                Start-Sleep -Seconds 1
                Remove-Item -Recurse -Force $taskWorktreePath -ErrorAction SilentlyContinue
            }
            $taskRecord.worktree_retained = $false
            $taskRecord.worktree_path = $null

            # Remove local tracking branch safely
            & git branch -D $task.branch 2>$null
        } else {
            $taskRecord.ci = "FAIL"
            $taskRecord.reason = "GitHub Actions checks failed for PR #$($pr.number)"
            Write-Host "TASK BLOCKED: $($taskRecord.reason)"
        }
    } catch {
        Set-Location $RepoRoot
        $taskRecord.reason = "Unexpected error during task execution: $($_.Exception.Message)"
        Write-Host "TASK BLOCKED: $($taskRecord.reason)"
    }

    $taskResults += [PSCustomObject]$taskRecord
}

$finishedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

# Calculate overall pack result
$allCandidates = ($taskResults | Where-Object { $_.result -eq "CANDIDATE_FOR_CONTROL_PLANE_REVIEW" }).Count
$totalTasks = $taskResults.Count

$overallResult = if ($allCandidates -eq $totalTasks -and $totalTasks -gt 0) {
    "COMPLETE"
} elseif ($allCandidates -gt 0) {
    "PARTIAL"
} else {
    "BLOCKED"
}

$summaryObj = [ordered]@{
    pack_id = $queue.pack_id
    base_main_sha = $queue.base_main_sha
    started_at = $startedAt
    finished_at = $finishedAt
    result = $overallResult
    tasks = $taskResults
}

$summaryPath = Join-Path $packRunDir "summary.json"
$summaryJson = $summaryObj | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText($summaryPath, $summaryJson, [System.Text.Encoding]::UTF8)

Write-Host "`n========================================================"
Write-Host "NIGHT PACK SUMMARY: $overallResult"
Write-Host "Summary JSON: $summaryPath"
Write-Host "========================================================"
