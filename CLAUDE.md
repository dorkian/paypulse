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
