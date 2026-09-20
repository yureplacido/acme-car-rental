---
description: Run the four architecture guardians before implementing a feature
agent: feature-implementer
---

For the requested change `$ARGUMENTS`, run the repository review sequence before making implementation changes:

1. @architecture-guardian — identify bounded context and repository-wide constraints.
2. @ddd-guardian — define the domain/application/adapter boundary.
3. @tdd-guardian — define the behavior-first test sequence.
4. @quarkus-book-guardian — identify the relevant book concept and version-sensitive Quarkus APIs.

Only after the four reviews are consistent, implement the smallest TDD slice. Preserve the repository standards in `AGENTS.md` and `docs/ddd-tdd-standards.md`.

After implementation, run the relevant tests and invoke the four guardians again on the changed scope. Do not finish with unresolved architectural findings.
