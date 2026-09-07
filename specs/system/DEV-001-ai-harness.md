# DEV-001 — AI implementation harness

Status: verified

## Intent

Constrain AI-assisted development with executable checks and traceable specifications so a plausible-looking change cannot be treated as complete without evidence.

## Controls

- Repository-specific instructions in `AGENTS.md`.
- Machine-readable specification registry.
- Specification shape and implementation-path validation.
- Secret and credential scanning over tracked files.
- Cricket-rule unit tests.
- Worker dry-run packaging validation.
- Android staging compilation.
- Pull-request checklist and continuous integration.

## Acceptance criteria

- [x] Every registered specification has a unique ID, valid status and existing implementation paths.
- [x] Every registered specification contains acceptance criteria.
- [x] Tracked secrets and prohibited generated artifacts fail the harness.
- [x] Worker rules run as deterministic unit tests.
- [x] GitHub verifies specifications, backend and Android on each pull request.
- [x] AI instructions prohibit declaring completion without verification evidence.

## Limitations

The harness reduces implementation errors; it cannot prove that an incomplete specification captures every real cricket rule. Human acceptance testing remains required.
