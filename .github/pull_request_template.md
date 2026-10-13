## Task and scope

- TASK-ID:
- REQ-IDs:
- Related issue (do not close before acceptance):
- AI session / author:
- PR type: reservation / planning result / infrastructure / accepted completion

## Change

Describe the concrete result and why it is needed. For a reservation, name the proposed owner and work branch.

## Verification

- [ ] python3 scripts/validate_repository.py
- [ ] python3 -m unittest discover -s tests -v
- [ ] git diff --check

Record actual results, additional checks and anything not tested. Do not check boxes for unexecuted work.

## Review and handoff

- Task status and next step:
- Open questions / dependencies:
- Functional acceptance: pending / not applicable with reason / explicit evidence
- Risks or limitations:

An implementation or planning-result PR does not by itself accept the requirements. Only move a task to DONE.md when its required acceptance is recorded.
