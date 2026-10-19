# Task Manager

Shared planning and AI collaboration workspace for an independent project management and helpdesk product for DACH SMEs. Product requirements are maintained in German. The product name is provisional.

[![Repository checks](https://github.com/1uedev/task-manager/actions/workflows/repository-checks.yml/badge.svg)](https://github.com/1uedev/task-manager/actions/workflows/repository-checks.yml)

**Current phase: backend and frontend foundations (TASK-022, DEC-055; TASK-023, DEC-056) plus the separately authorized permissions module (TASK-025, DEC-057, claim PR #69), after the isolated technical qualification (TASK-021). The identity module (local accounts, login, fixed session periods) is authorized separately through TASK-024 (DEC-058, claim PR #73), without a login page, and the adapter between identity and permissions is authorized through TASK-039 (DEC-063, claim PR #94); projects and other business modules remain future work.** Java with Spring Boot, React with TypeScript, a modular monolith and HTTP/JSON in REST style with OpenAPI are confirmed (DEC-039, DEC-042 through DEC-044). Detailed contracts, versions and the product specification still need review.

## Start here

- Find work: [available, reserved, blocked and review-ready tasks](TODO.md#aufgabenübersicht). The overview is generated from the task entries and checked by CI. Reservations become effective after their claim PR is merged.
- Technical qualification: [pinned candidates, common contracts and evidence](docs/TECHNICAL_BASELINE.md), [reproducible commands](qualification/README.md). DEC-054 authorizes this isolated proof only.
- Backend foundation: [commands, configuration, modules and migration convention](backend/README.md). DEC-055 authorizes this foundation only.
- Permissions module: [rules, Java ports, transactions, audit and integration limits](backend/PERMISSIONS.md). DEC-057 authorizes TASK-025 only.
- Frontend foundation: [commands, structure, languages, branding and checks](frontend/README.md). DEC-056 authorizes this foundation only.
- Prepare a bounded first product development order: [recommendations, open questions and planned AI tasks](docs/DEVELOPMENT_START.md). Application implementation still needs an explicit order.
- Review the broader decision round: [architecture readiness, open choices and review evidence](docs/ARCHITECTURE_READINESS.md).

- Scope review: [function inventory, V1 additions and later modules](docs/FUNCTION_INVENTORY.md).
- Human review: [requirements](REQUIREMENTS.md), [decisions and open questions](DECISIONS.md), [roadmap](ROADMAP.md).
- Identity and access review: [permissions, LDAP and portal examples](docs/PERMISSIONS.md).
- Architecture review: [confirmed technology choices, rationale and proposed details](docs/ARCHITECTURE.md).
- Tags and search review: [V1 catalog, rights, search scope and performance proposals](docs/TAGS_SEARCH.md).
- Technical contract review: [confirmed TypeScript, module and API direction with proposed details](docs/TECHNICAL_CONTRACTS.md).
- Domain review: [project workflows, fields, time corrections and requirement revisions](docs/DOMAIN_MODEL.md).
- Service review: [mail handling, customer portal and calculated SLA examples](docs/HELPDESK_SLA.md).
- Knowledge and presentation review: [wiki drafts, languages, branding and email examples](docs/WIKI_I18N_BRANDING.md).
- Product AI review: [four V1 use cases, skills, providers and confirmation](docs/AI_USE_CASES.md).
- Git and migration review: [provider variants, source permissions and staged Redmine import](docs/GIT_MIGRATION.md).
- Operations review: [Docker, recovery targets, storage calculation and proposed load tests](docs/OPERATIONS.md).
- Review policy proposal: [risk-based checks, responsibilities and coordination examples](docs/DEVELOPMENT_REVIEW.md).
- AI contributors: [shared instructions](AGENTS.md), then [workflow and starter prompts](docs/AI_WORKFLOW.md).
- Work state: [central TODO](TODO.md), [central DONE](DONE.md), [GitHub task discussions](https://github.com/1uedev/task-manager/issues).
- Contributions and checks: [CONTRIBUTING.md](CONTRIBUTING.md).

TODO.md on main is the single source for task ownership and state. Issues provide discoverable entry points and discussion. DONE.md records accepted results; it is not populated merely because a PR was merged.

## Agreed product direction

Version 1 combines tickets, time tracking, email helpdesk with SLAs, a customer portal, a modern wiki, requirements management and GitHub/GitLab links. Deployment starts with Docker and PostgreSQL, one installation per company. The confirmed direction is React with TypeScript, Java with Spring Boot, a modular monolith and an HTTP/JSON API in REST style described with OpenAPI. German and English, customer branding, LDAP and group and field permissions are included in the specification.

V1 also includes configurable expiry after a fixed duration since login, without an inactivity timeout; an extensive tag system with creation rights available to authorized external portal users; and unified search across tickets/tasks, projects, wiki and requirements (DEC-045 through DEC-050). File-content search comes later. The default for new installations is 24 hours (DEC-052); duration limits and rule-change behavior, tag governance, file-name/metadata scope and numeric performance targets remain open.

DEC-051 confirms the first internal demonstration slice: login, projects, tickets, tags and search; LDAP and the portal follow within V1. DEC-053 confirms administrator-created local accounts, including portal accounts, with an initial one-time password and mandatory change at first login. Expiry, secure delivery, recovery and bootstrap details remain open. These choices do not authorize application implementation.

Administrators will configure Markdown instructions and models from OpenAI, Anthropic/Claude or Ollama. AI proposes; authorized users confirm changes and outgoing messages. V1 includes ticket summaries, reply drafts, requirements checks and wiki revision assistance for authorized internal users. Direct AI access for external portal users comes later. Delivery order for the three providers remains open.

Initial technical reviews will be reciprocal AI reviews; additional human technical review remains open (DEC-040). Holger retains functional acceptance. Kanban, Gantt and simple dashboards are the next confirmed functional priority after V1, with early 2FA and Redmine import retained (DEC-041).

Target usage is 500–1,500 new tickets per month and roughly 15 simultaneous active users. There is no fixed RAM cap. The 1-TB SSD planning budget requires a defined attachment and retention profile. The recovery planning targets are at most one hour of data loss and four hours to restore operation after complete server failure. No performance or recovery measurements or finished application are claimed.

## Local setup

Only Git and Python 3.10 or later are needed for the documentation/repository checks below. The separate technical qualification additionally needs the versions and Docker runtime described in qualification/README.md; CI runs it on an ephemeral runner. The backend under backend/ needs Temurin 25 and a Docker runtime for its PostgreSQL integration tests, see backend/README.md. The frontend under frontend/ needs Node.js 24.22.0 and a Chromium based browser for its browser checks, see frontend/README.md. GitHub CLI is optional for creating issues and pull requests. No model keys or existing database are required.

```sh
git clone https://github.com/1uedev/task-manager.git
cd task-manager
python3 scripts/validate_repository.py
python3 scripts/task_board.py --check
python3 -m unittest discover -s tests -v
```

Use a separate clone or worktree for each concurrent AI. Task reservation and handoff examples are in docs/AI_WORKFLOW.md.

After changing task metadata, run `python3 scripts/task_board.py --write` to update the overview in TODO.md. With no option, the command prints the overview. This does not assign work, unlock blocked tasks or monitor running agents.

## Repository contents

| Path | Purpose |
|---|---|
| REQUIREMENTS.md | 89 requirements, stable IDs and draft acceptance criteria |
| DECISIONS.md | Confirmed choices, authorized scope and open decisions |
| ROADMAP.md | Initial release and later work |
| TODO.md / DONE.md | Central task ledger and accepted history |
| AGENTS.md / CLAUDE.md | Shared AI instructions and Claude import |
| docs/TECHNICAL_BASELINE.md | Isolated qualification, exact version candidates, contracts and measured evidence |
| backend/ | Backend and PostgreSQL foundation from TASK-022; empty module boundaries, no business features |
| frontend/ | React and TypeScript foundation from TASK-023; shell, languages, branding, API client and shared states, no business features |
| qualification/ / contracts/ | Synthetic test sources and OpenAPI specimen; not product modules |
| docs/DEVELOPMENT_START.md | Confirmed first slice and account/session choices, proposed tools and blocked task dependencies |
| docs/ARCHITECTURE_READINESS.md | Consolidated decision package and evidence; architecture is not yet approved |
| docs/DEVELOPMENT_REVIEW.md | Proposed technical review, acceptance boundaries and parallel-work scenarios |
| docs/AI_WORKFLOW.md | Reservation, separate worktrees, review and starter prompts |
| docs/PERMISSIONS.md | Draft groups, portal scope, field conflicts, identity lifecycle and acceptance scenarios |
| docs/ARCHITECTURE.md | Confirmed architecture choices; proposed detailed contracts, deployment and measurements |
| docs/TECHNICAL_CONTRACTS.md | Confirmed frontend/module/API direction; proposed contracts and 16 future acceptance scenarios |
| docs/TAGS_SEARCH.md | Confirmed V1 tags and performant search; proposed behavior, rights and measurements |
| docs/DOMAIN_MODEL.md | Proposed project workflows, configuration lifecycle, time records and requirement revisions |
| docs/HELPDESK_SLA.md | Mail and portal flows, confirmed queue/pause rules and proposed SLA calculations |
| docs/WIKI_I18N_BRANDING.md | Confirmed draft/publication flow and proposed editor, language, branding and template rules |
| docs/AI_USE_CASES.md | Four confirmed internal V1 use cases and proposed skills, provider, data, confirmation and operating contracts |
| docs/GIT_MIGRATION.md | Confirmed Git variants and Redmine priority; proposed read access, import mapping and cutover |
| docs/FUNCTION_INVENTORY.md | Core scope, proposed V1 priorities, later modules and AI use assessment |
| docs/OPERATIONS.md | Confirmed recovery targets and proposed deployment, backup, retention, resource and performance profiles |
| .github/ | CI, ownership and contribution templates |
| scripts/ / tests/ | Dependency-free repository validation and its regression checks |

The main branch requires pull requests with successful, current CI. Force pushes and branch deletion are disabled, including for administrators. [Workflow documentation](docs/AI_WORKFLOW.md) explains the review boundary when tools share the owner's account. Product acceptance remains with Holger (@1uedev).

No project license or commercial model has been selected. That decision remains in DECISIONS.md. This public repository must contain only material intended for public access.
