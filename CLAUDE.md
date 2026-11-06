# PayPulse (code repo)

Synthetic B2B invoicing + BNPL platform, built as a gamified interview-prep project.
Plan, architecture, levels and game rules: second-brain note
`~/Documents/workspace/second-brain/01-projects/paypulse/plan.md`.
Progress board: https://trello.com/b/FGeGfVoE/paypulse-interview-quest (one card = one task).

## How to work here
- **When Ash asks you to write code, use the `explained-code` skill** (`.claude/skills/explained-code/`).
  Ash learns by reading: code with teaching comments in the files, a plain explanation in chat,
  real test results, and exactly one next action.
- **Follow the strict standards** in `.claude/skills/explained-code/standards.md` (layering, design
  patterns, REST, persistence, messaging, testing, style, Definition of Done) and say why for every design decision.
- One card step at a time. One branch per card. Never commit to `main`; don't commit or push
  unless asked; stage specific paths, never `git add -A`.
- Java 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` before running Maven.
  Homebrew's default JDK on this Mac is 27, which is wrong for this project.

## Task flow (decided 2026-11-06, replaces the lanes of 2026-11-05)
One task at a time, in this order. Ash wants progress, not lessons on mechanics: keep explanations general and short.
1. **Start** the task: name the card and step.
2. **Write the tests first**, show them red, and describe in plain words what each test proves. Then stop.
3. **Ash confirms.** Only then write the code that makes them pass.
4. **Review** the diff against the spec and `standards.md`; report real findings.
5. **TLDR** of what was done, then **commit** (card branch, specific paths, never `main`, never `git add -A`).
The software-factory harness is optional for boilerplate; the TDD loop above is done in the session by default.
Existing Flyway migrations are never edited. CV honesty rule applies: nothing is claimed that Ash can't explain generally.

## Stack decisions (don't change without asking)
- Java 21, Spring Boot 4.x (Spring Framework 7), Spring Cloud 2025.1.x. Never Cloud 2025.0.x with Boot 4.
- Maven multi-module. Versions come from the parent BOMs. Never put `<version>` on a BOM-managed dependency.
- Money is `BigDecimal`/`NUMERIC`. Flyway owns the schema (`ddl-auto=validate`). One database per service.
- Local infra: `infra/compose` (Docker Compose profiles `infra`, `messaging`, `identity`, `obs`).
  Credentials live in the git-ignored `infra/compose/.env`; never print or commit it.

## Layout
```
pom.xml                    parent POM (BOMs, Java 21, module list)
platform/common-web        shared web library (correlation id, error handling), no business logic
services/api-gateway       edge routing, port 8080
services/customer-service  customers and tenants, port 8082
infra/compose              local infrastructure
```

## Verification habit
Run the build/tests and report the real output. `mvn -q verify` for code, `docker compose config -q`
for infra. Say "not run" if you did not run it.
