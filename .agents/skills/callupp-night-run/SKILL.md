---
name: callupp-night-run
description: Executes a finite CallUpp night pack that was already approved by the CallUpp AI Control Plane. Use only for explicitly supplied night-pack queue files; never choose tasks, merge PRs, or start additional phases.
---

# CallUpp Night Run Skill

This skill executes an explicitly supplied, pre-approved CallUpp night pack queue manifest using the deterministic repository Night Runner.

## Protocol & Guardrails

1. **Explicit QueuePath Required:**
   - The user or scheduling trigger must provide a concrete, absolute path to an existing `queue.json` manifest.
   - Never invent, select, generate, or modify tasks or queues autonomously.

2. **Strict Non-Goals:**
   - Never merge any Pull Request.
   - Never push directly to `main` or modify `main`.
   - Never use `--dangerously-skip-permissions`.
   - Never use legacy Antigravity Workflows.
   - Never broaden or reinterpret task scope beyond the supplied prompt files.

3. **Execution Steps:**
   - **Step 1: Environment Preflight:**
     Execute the read-only environment check:
     ```powershell
     powershell -ExecutionPolicy Bypass -File .\scripts\night-preflight.ps1
     ```
     If preflight returns `BLOCKED` (non-zero exit code), immediately halt execution and report the blocked status and required user actions. Do not proceed to run tasks.

   - **Step 2: Night Runner Execution:**
     Invoke the runner with the supplied queue path:
     ```powershell
     powershell -ExecutionPolicy Bypass -File .\scripts\night-runner.ps1 -QueuePath "<QueuePath>"
     ```

   - **Step 3: Summary & Reporting:**
     Read the generated summary at `%LOCALAPPDATA%\CallUpp\night-runner\runs\<pack-id>\summary.json`.
     Report the overall pack result (`COMPLETE`, `PARTIAL`, or `BLOCKED`) and each task's status.
     Explicitly clarify to the user that `CANDIDATE_FOR_CONTROL_PLANE_REVIEW` means the branch and PR passed all CI checks and are ready for manual Control Plane review; it does **not** mean the task is accepted, approved, or merged into `main`.
