# Task Manager

Shared planning and AI collaboration workspace for an independent project management and helpdesk product for DACH SMEs. Product requirements are maintained in German. The product name is provisional.

[![Repository checks](https://github.com/1uedev/task-manager/actions/workflows/repository-checks.yml/badge.svg)](https://github.com/1uedev/task-manager/actions/workflows/repository-checks.yml)

**Current phase: requirements, architecture and collaboration setup. Application implementation has not started.** The repository setup does not choose between Go and Java or accept the product specification on the owner's behalf.

## Start here

- Human review: [requirements](REQUIREMENTS.md), [decisions and open questions](DECISIONS.md), [roadmap](ROADMAP.md).
- Identity and access review: [permissions, LDAP and portal examples](docs/PERMISSIONS.md).
- Architecture review: [Go/Java comparison, modules, API and deployment proposal](docs/ARCHITECTURE.md).
- Domain review: [project workflows, fields, time corrections and requirement revisions](docs/DOMAIN_MODEL.md).
- Service review: [mail handling, customer portal and calculated SLA examples](docs/HELPDESK_SLA.md).
- Knowledge and presentation review: [wiki drafts, languages, branding and email examples](docs/WIKI_I18N_BRANDING.md).
- Product AI review: [four V1 use cases, skills, providers and confirmation](docs/AI_USE_CASES.md).
- Git and migration review: [provider variants, source permissions and staged Redmine import](docs/GIT_MIGRATION.md).
- Operations review: [Docker, recovery targets, storage calculation and proposed load tests](docs/OPERATIONS.md).
- AI contributors: [shared instructions](AGENTS.md), then [workflow and starter prompts](docs/AI_WORKFLOW.md).
- Work state: [central TODO](TODO.md), [central DONE](DONE.md), [GitHub task discussions](https://github.com/1uedev/task-manager/issues).
- Contributions and checks: [CONTRIBUTING.md](CONTRIBUTING.md).

TODO.md on main is the single source for task ownership and state. Issues provide discoverable entry points and discussion. DONE.md records accepted results; it is not populated merely because a PR was merged.

## Agreed product direction

Version 1 combines tickets, time tracking, email helpdesk with SLAs, a customer portal, a modern wiki, requirements management and GitHub/GitLab links. Deployment starts with Docker and PostgreSQL, one installation per company. React is planned; backend language remains open. German and English, customer branding, LDAP, group and field permissions and an API are included in the specification.

Administrators will configure Markdown instructions and models from OpenAI, Anthropic/Claude or Ollama. AI proposes; authorized users confirm changes and outgoing messages. V1 includes ticket summaries, reply drafts, requirements checks and wiki revision assistance for authorized internal users. Direct AI access for external portal users comes later. Delivery order for the three providers remains open.

Target usage is 500–1,500 new tickets per month and roughly 15 simultaneous active users. There is no fixed RAM cap. The 1-TB SSD planning budget requires a defined attachment and retention profile. The recovery planning targets are at most one hour of data loss and four hours to restore operation after complete server failure. No performance or recovery measurements or finished application are claimed.

## Local setup

Only Git and Python 3.10 or later are needed for the repository checks. GitHub CLI is optional for creating issues and pull requests. No application dependencies, model keys or database are required.

```sh
git clone https://github.com/1uedev/task-manager.git
cd task-manager
python3 scripts/validate_repository.py
python3 -m unittest discover -s tests -v
```

Use a separate clone or worktree for each concurrent AI. Task reservation and handoff examples are in docs/AI_WORKFLOW.md.

## Repository contents

| Path | Purpose |
|---|---|
| REQUIREMENTS.md | 86 initial requirements, stable IDs and draft acceptance criteria |
| DECISIONS.md | Confirmed choices, authorized scope and open decisions |
| ROADMAP.md | Initial release and later work |
| TODO.md / DONE.md | Central task ledger and accepted history |
| AGENTS.md / CLAUDE.md | Shared AI instructions and Claude import |
| docs/AI_WORKFLOW.md | Reservation, separate worktrees, review and starter prompts |
| docs/PERMISSIONS.md | Draft groups, portal scope, field conflicts, identity lifecycle and acceptance scenarios |
| docs/ARCHITECTURE.md | Proposed backend choice, module boundaries, API contract, deployment and measurement plan |
| docs/DOMAIN_MODEL.md | Proposed project workflows, configuration lifecycle, time records and requirement revisions |
| docs/HELPDESK_SLA.md | Mail and portal flows, confirmed queue/pause rules and proposed SLA calculations |
| docs/WIKI_I18N_BRANDING.md | Confirmed draft/publication flow and proposed editor, language, branding and template rules |
| docs/AI_USE_CASES.md | Four confirmed internal V1 use cases and proposed skills, provider, data, confirmation and operating contracts |
| docs/GIT_MIGRATION.md | Confirmed Git variants and Redmine priority; proposed read access, import mapping and cutover |
| docs/OPERATIONS.md | Confirmed recovery targets and proposed deployment, backup, retention, resource and performance profiles |
| .github/ | CI, ownership and contribution templates |
| scripts/ / tests/ | Dependency-free repository validation and its regression checks |

The main branch requires pull requests with successful, current CI. Force pushes and branch deletion are disabled, including for administrators. [Workflow documentation](docs/AI_WORKFLOW.md) explains the review boundary when tools share the owner's account. Product acceptance remains with Holger (@1uedev).

No project license or commercial model has been selected. That decision remains in DECISIONS.md. This public repository must contain only material intended for public access.
