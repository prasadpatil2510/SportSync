# SportSync AI development contract

This file governs all AI-assisted changes in this repository.

## Required workflow

1. Read `specs/README.md`, `specs/registry.json` and every specification related to the requested change.
2. If behavior is new or changing, create or update a specification before editing implementation code.
3. Give the specification a stable ID and list it in `specs/registry.json` with its implementation paths.
4. Implement the smallest coherent change that satisfies its acceptance criteria.
5. Add or update automated tests for cricket rules and backend behavior.
6. Run the quick harness. Run the full harness before a release.
7. Do not mark acceptance criteria complete until evidence exists.
8. Keep Android testing, backend testing and production environments isolated.

## Definition of done

- The specification is current and acceptance criteria are checked.
- Cricket-rule tests pass.
- Worker dry-run validation passes.
- Android staging APK compiles for user-facing changes.
- Secret scanning passes.
- Database changes use a forward-only numbered migration.
- Public API or persisted-schema changes include compatibility notes.
- No credentials, tester identities, APKs or generated files are committed.

## Safety invariants

- Never decrement a score or delete a delivery except through an explicit correction command recorded in an audit trail.
- Every future scoring command must have an idempotency key and expected match revision before production launch.
- The shared organiser PIN is testing-only and must never be described as production authentication.
- Do not copy proprietary code, assets or branding from another cricket product.
- Do not modify the separate auction, OBS or legacy scorer projects from this repository.

## Verification commands

```text
node scripts/verify-specs.mjs
node scripts/secret-scan.mjs
cd worker && pnpm test && pnpm run check
cd android && gradle assembleStagingDebug
```
