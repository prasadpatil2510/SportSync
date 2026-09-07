# Specification-driven development

Specifications are the source of truth for observable SportSync behavior. Code explains how; specifications explain what and why.

## Lifecycle

1. Create a change specification from `specs/templates/change-spec.md`.
2. Assign a stable ID such as `MATCH-003`.
3. Define scope, invariants, failure behavior and measurable acceptance criteria.
4. Register it in `specs/registry.json` before implementation.
5. Implement and test it.
6. Check acceptance criteria only after verification.
7. Record meaningful design changes under Decisions.

Status values are `draft`, `approved`, `implemented`, `verified` and `superseded`. A release may include only `verified` behavior unless explicitly labelled experimental.

Specifications must describe original SportSync behavior and must not request proprietary source code, artwork or branding from other products.
