---
description: Design and validate a feature before implementation
agent: feature-implementer
---

For the requested change `$ARGUMENTS`, run the repository review sequence before making implementation changes:

1. @domain-designer — define the bounded context, aggregates, value objects, invariants, use cases, ports and test-first scenarios.
2. @architecture-guardian — validate repository-wide boundaries and consistency.
3. @ddd-guardian — validate the proposed domain/application/adapter boundary.
4. @tdd-guardian — validate the behavior-first test strategy.
5. @quarkus-book-guardian — identify the relevant book concept and version-sensitive Quarkus APIs.

Only after these reviews are consistent, implement the smallest vertical TDD slice. Preserve the repository standards in `AGENTS.md` and `docs/ddd-tdd-standards.md`.

After implementation, run the relevant tests and invoke the guardians again on the changed scope. Do not finish with unresolved architectural findings.
