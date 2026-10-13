# Working with multiple AI sessions

The same workflow applies to Codex, Claude Code and other authorized contributors. It coordinates people and tools; it does not launch agents automatically or provide them with credentials.

## Authoritative state

Use TODO.md and DONE.md on the main branch of https://github.com/1uedev/task-manager. GitHub Issues link to these tasks and hold discussion. An issue assignment or label is not a reservation for an individual AI session. A GitHub account may be used by more than one tool, so record a unique session ID in the task itself.

Holger coordinates work unless he explicitly designates a coordinating session. The original requirements are still a draft. Planning tasks can produce reviewable proposals now, while confirmed product choices remain protected from silent changes.

## Starting and reserving a task

1. Read AGENTS.md, README.md, TODO.md, DONE.md and the relevant requirements and decisions.
2. Fetch current main. Select an OFFEN task within the user's authorization and check its dependencies.
3. Create a small reservation branch. Change only that task's status to IN ARBEIT, owner to your unique session ID, update date, planned work branch and next step.
4. Open a claim-only PR titled, for example, “Claim TASK-003 for codex-architecture-01”. Reference its issue without closing it.
5. The coordinator verifies that no reservation or overlapping scope is active and merges this PR. Start substantive work only after the reservation appears on main.
6. Fetch again and create the actual work branch from the updated main.

A competing claim must not overwrite an owner. If another claim wins, abandon or revise your reservation. A CI pass is not a grant of ownership. Claims are serialized through the coordinator; Markdown and GitHub issue comments are not atomic locks.

## Separate worktrees

One checkout must not be edited by two active agents. From an existing clone, a new planning worktree can be created after the claim is merged:

```sh
git fetch origin
git worktree add ../task-manager-task-003 -b task/task-003-codex-architecture origin/main
```

Use a unique directory and branch. If that branch or directory already exists, inspect it instead of resetting it. A separate clone on another machine is equally valid. Each tool must already have authorized GitHub access; never place credentials in instructions or prompts.

## Result and handoff

The result PR includes the TASK-ID, affected REQ-IDs, issue reference, scope, checks actually run, unresolved questions and limitations. Update only your task's handoff and set BEREIT ZUR PRÜFUNG. For changes affecting shared requirements, ask the coordinator to integrate overlapping work rather than replacing whole documents.

After other changes land, update your branch from main and rerun checks. Never resolve a conflict by discarding another task or owner. Strict status checks require an up-to-date branch before merging.

For interruption, set BLOCKIERT when a concrete blocker exists; otherwise document the handoff and coordinate reassignment. Record the previous owner, last known commit, changed files and next step. A stale date does not automatically release a reservation.

Completion requires the acceptance described in the task. Keep it in TODO.md until that evidence exists. A completion PR moves the task to DONE.md with the original ID, criteria, result, checks, reviewer, acceptance evidence and date. The linked issue may be closed at that point. Do not use automatic closing keywords on an ordinary draft or result PR.

## Main branch and human review

The initial policy requires pull requests, the successful repository-checks job on an up-to-date branch, resolved review conversations, and disallows force pushes and deletion of main. It applies to administrators too. Settings are verified during TASK-013; repository administrators can still change policy, so these controls are not an authorization system for individual AI sessions.

Mandatory approving reviews initially remain at zero because the owner and tools may share the same GitHub account. CODEOWNERS names @1uedev, but is not configured as an enforced approval gate. Holger still performs functional acceptance, recorded explicitly. A separate reviewer account permits a stronger review requirement later; OPEN-017 records that open decision.

## Starter prompt for a new AI session

> Work in the repository 1uedev/task-manager. First read AGENTS.md, README.md, TODO.md, DONE.md and docs/AI_WORKFLOW.md from current main. I authorize you to prepare a planning proposal for the task I specify. Inspect its dependencies, reserve it using a claim-only PR and wait for the coordinator to merge that reservation before substantive work. Use a unique session ID and your own worktree. Preserve confirmed requirements, identify open decisions and do not implement the application. Deliver a focused result PR with evidence and an updated handoff. Never mark functional acceptance on my behalf.

Choose a specific TASK-ID when using this prompt. TASK-003 concerns backend and module architecture; TASK-004 concerns identity and permissions; TASK-008 concerns AI scope. These can be drafted separately with coordinated integration and explicit cross-dependencies.

## Coordination examples

- Two AIs request TASK-003: only one reservation is merged. The other remains a proposal and must not start that task.
- Codex works on architecture while Claude works on permissions: each has a distinct task and worktree; interface questions are recorded for coordinated resolution.
- A result is merged but not functionally accepted: the task remains BEREIT ZUR PRÜFUNG, and its issue remains open.
- A session disappears: the coordinator records a handoff and explicitly reassigns the task before a new session resumes it.

## Tool documentation

Verified during setup on 13 October 2026: [Codex AGENTS.md instructions](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [Claude shared project instructions](https://code.claude.com/docs/en/memory#share-one-file-with-other-coding-tools), and [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches). These references describe tool behavior; the coordination and acceptance policy above is specific to this project.
