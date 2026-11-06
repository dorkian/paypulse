# PayPulse (code repo)

Synthetic B2B invoicing + BNPL platform, built as a gamified interview-prep project.
Schedule and cards (current): second-brain note
`~/Documents/workspace/second-brain/01-projects/paypulse/fast-track-plan.md` (v0.1 public 2026-12-03, v0.2 2027-01-21).
Architecture and game rules: `.../paypulse/plan.md` (its §4 schedule is superseded by the fast-track plan).
Progress board: https://trello.com/b/FGeGfVoE/paypulse-interview-quest (one card = one task).

## How to work here
- **Architect mode (2026-11-06):** Claude writes all code; Ash learns structure and tech, not syntax (see Task flow).
  Use the `explained-code` skill (`.claude/skills/explained-code/`) for its standards and plain explanation in chat,
  but skip line-by-line teaching comments. Always show real test results and exactly one next action.
- **Follow the strict standards** in `.claude/skills/explained-code/standards.md` (layering, design
  patterns, REST, persistence, messaging, testing, style, Definition of Done) and say why for every design decision.
- One card step at a time. One branch per card. Never commit to `main`; don't commit or push
  unless asked; stage specific paths, never `git add -A`.
- Java 21: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` before running Maven.
  Homebrew's default JDK on this Mac is 27, which is wrong for this project.

## Task flow (updated 2026-11-06 for the fast-track plan)
Every card is test-first. Each card in `fast-track-plan.md` is tagged **FAST** or **LEARN**; the tag picks the lane.
One card at a time either way. If a card has no tag, ask which lane before starting.

### LEARN lane = architect mode (default; core Java/Spring, agent behaviour, Kubernetes)
Done in this session. **Ash does not write code and skips syntax.** The goal is the big picture: structure,
the tech, trade-offs and failure modes. Never explain Java/Python syntax unless Ash asks. Ash's time ≈ 60–75 min per card.
1. **Brief**: name the card. One page: what it does, why it matters, a box-and-arrow diagram (ASCII or Mermaid),
   the main trade-off.
2. **Tests first**: write them, run them, show the real red output. Present them to Ash as a list of
   **test names in plain words** ("a failed transfer leaves no money moved"), not code. Then **stop**.
3. **Ash approves the test names.** Only then hand the build to a **different model in a fresh context**: spawn a subagent
   with `model: "sonnet"` (Claude Sonnet 5.5) and the card, the test file paths and `standards.md`. It may **not** edit the tests
   (check `git diff` on the test paths afterwards; revert any test edit and rerun). If it says a test is wrong, show Ash that claim and stop.
   Short comments that say *why*, not teaching comments on syntax.
4. **Review** the diff against the card and `standards.md`; report real findings only.
5. **TLDR**, then **commit** (card branch, specific paths, never `main`, never `git add -A`).
6. **Walkthrough at box level**: which part does what and where the key decision lives, with file names but no line-by-line code.
7. **Break it**: give Ash one command to trigger the card's failure (listed on the Trello card) and explain what Ash sees.
8. **Lesson box** for a 12-year-old: the idea in one sentence, one everyday comparison, the diagram, one explain-back
   question. Write it into the card's box in `fast-track-plan.md`. Save the diagram in `docs/diagrams/<card>.md`.
Models (README "Which model does what"). **Rule: the test writer and the builder are never the same model; the reviewer is never the builder's model.**
Main session = Claude Opus 5.5 (brief, tests, review, walkthrough). Builder = Claude Sonnet 5.5 subagent. If the main session is not on Opus 5.5,
say so at the start and pick a builder model that still differs from the test writer.
Weekly: offer one 10-minute "read this diff aloud" exercise (interviews show diffs and stack traces).

### FAST lane (boilerplate and plumbing, via the software-factory harness)
Harness: `~/Documents/workspace/repos/software-factory` (TDD loop enforced in code: red gate → builder may not
touch tests → green gate → read-only review → branch + `report.md`; it never merges).
1. **Spec**: write `jobs/paypulse-<card>-001/spec.md` (Given/When/Then, IDs AC1, AC2, …) and `job.json` with
   `"target": "../../../paypulse"`, `"profile": "java-maven"` (or `"python-uv"` for `agents/risk-agent`),
   `"runtimePreset": "ash-pipeline"` (test writer Claude Sonnet 4.6 via agy, builder Codex gpt-6.1-sol high, reviewer Claude Opus 4.6 via agy:
   test writer and builder are never the same model),
   `limits` (`maxBuildRounds` 3, `maxReviewRounds` 2, a `maxTokens` cap), and `protectedGlobs` covering
   `pom.xml`, `**/pom.xml`, `**/db/migration/**`, `docs/adr/**`, `CLAUDE.md`, `infra/**`, `.github/**`.
   Show Ash the spec and **stop for approval** before running.
2. **Prep once per module**: `mvn -B dependency:go-offline` with Java 21 (the Codex sandbox has no network).
3. **Run**: `node factory/cli.mjs run jobs/paypulse-<card>-001` (in the background; it can take minutes).
4. **Read** `report.md`, then summarise for Ash in under 10 lines: verdict, red and green evidence, tokens,
   top 3 findings. Re-run `mvn -q verify` in `repos/paypulse` on the factory branch yourself and report the real output.
5. **Ash decides**: merge the `factory/<job-id>` branch through a PR, or fix it and `resume`. Never merge yourself.
FAST jobs run "clean" (no planted bugs). Use FAST only while the free Codex month lasts (until about 2026-11-27,
check the billing date first) and only for code a card in the next 3 weeks needs. After that, everything runs in the LEARN lane.
If a job stops with a test dispute, show Ash the dispute file before changing anything.

Existing Flyway migrations are never edited, in either lane. CV honesty rule applies: nothing is claimed that Ash can't explain generally.

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
services/ledger-service    double-entry ledger (L1)
infra/compose              local infrastructure
```

## Verification habit
Run the build/tests and report the real output. `mvn -q verify` for code, `docker compose config -q`
for infra. Say "not run" if you did not run it.
