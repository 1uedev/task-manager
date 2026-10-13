# Contributing

Read [AGENTS.md](AGENTS.md) and the [AI workflow](docs/AI_WORKFLOW.md) first. Requirements and architecture proposals may be drafted from the existing specification; their approval and product implementation remain separate.

## Changes

1. Choose a task from TODO.md and reserve it through a claim-only PR.
2. After the coordinator merges the reservation, work on a separate task branch from current main.
3. Keep the change limited to the task and its referenced requirements. Preserve IDs and existing history.
4. Run the repository checks and any additional checks relevant to the change.
5. Open a result PR using the template. Include limitations and a concrete next step.
6. Move the task to DONE.md only after the required acceptance is recorded.

Use branch names such as claim/task-003-codex-01 and task/task-003-codex-architecture. Each branch belongs to one task and one responsible session. Use descriptive commits. Avoid simultaneous edits to shared files; the coordinator integrates overlapping changes.

## Local verification

```sh
python3 scripts/validate_repository.py
python3 -m unittest discover -s tests -v
git diff --check
```

The validator checks requirement and task IDs, references, local document links, task metadata, duplicate ownership records across TODO/DONE and required completion evidence. Tests verify that representative invalid documents are rejected. These checks do not prove factual accuracy, authorization or application correctness.

GitHub Actions runs with read-only contents permission, no product secrets and no AI provider calls. It runs on pull requests and pushes to main. Keep the job name repository-checks stable because branch protection refers to it. Changes to the validator, workflows and agent instructions deserve explicit review.

## Review and acceptance

Holger (@1uedev) is the current functional owner. CODEOWNERS routes changes to that account. Until a separate reviewer identity is available, a mandatory GitHub approving review would prevent the owner from merging PRs created through the same account. The initial protection therefore requires a PR and CI, with zero mandatory approving reviews. This is not automatic functional acceptance.

For product decisions and completed functional tasks, record Holger's actual acceptance with an auditable reference. Do not fabricate a review, mark a checklist on his behalf or infer acceptance from a merged infrastructure PR. The final technical review policy remains OPEN-017.

## Public repository hygiene

Commit only project material intended for this repository. Keep credentials, personal access tokens, actual customer data, database dumps, exports and private session logs out of Git. The ignore file reduces accidental additions but is not a security control. Inspect staged changes before pushing. No software license has been chosen; do not select one as routine scaffolding.
