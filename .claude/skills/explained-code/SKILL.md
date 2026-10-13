---
name: explained-code
description: Use in the PayPulse repo whenever Ash asks to write, implement, add or scaffold code from a spec or a Trello card step (for example "write the code with spec", "do L0-4 step 3", "implement this part", "create the filter / entity / controller / config"). Writes the code with teaching comments inside the files AND a plain-language walkthrough in chat, then runs and verifies it. Ash learns by reading, not by typing.
metadata:
  provenance: self-improving-skills

---
# explained-code: write it, explain it, prove it works

Ash is an intermediate developer who learns by **reading clear code and clear explanations**, then
says it aloud later (interview prep). So every piece of code you write must teach. Do not write
silent code. Do not write a wall of theory either.

Applies to this repo only (`paypulse`). The reference for tone and depth is
`platform/common-web/src/main/java/com/paypulse/common/web/CorrelationIdFilter.java`: numbered
step comments, a "why" next to every non-obvious line, a SECURITY note where one is real.

## 0. Before writing (3 lines in chat)
- Which card/step this is (for example "L0-4 step 3") and what it unlocks.
- Which files you will create or change.
- One sentence on how it fits the architecture (`plan.md` in the second-brain note).

Do **one step at a time**. Stop after each step. Never run ahead to the next one.

## 1. Rules for the code and the comments in the files

**Explain in the codebase, not only in chat.** A reader opening the file next month has no chat.

1. **Every class** gets a Javadoc: what it is, why it exists, where it is used or registered.
2. **Every non-obvious line or block** gets a comment about the **why**, not the what.
   Bad: `// set header`. Good: `// set before doFilter: after the response is committed headers can't change`.
3. **Flows get numbered step comments** (`// 1.`, `// 2.` ...) so the order is visible.
4. **Name the trap**: if there is a classic mistake here (MDC not cleared, self-invocation skipping
   `@Transactional`, `ddl-auto=update`, float for money), say it in a comment where it would happen.
5. **Config files are code too**: comment every non-obvious key in `application.yml`,
   `docker-compose.yml`, `pom.xml` and Flyway SQL (why this value, what breaks if it is wrong).
6. **Tests teach**: each test method has a one-line comment saying what behaviour it proves, and
   one test per interesting branch (including the "empty/blank/error" ones).
7. **Do not over-comment**: no comments on getters, trivial assignments or imports. No stale or
   wrong comments: a wrong comment is worse than none. No commented-out code.
8. **Plain words.** Define a term the first time it appears in a file (MDC, BOM, outbox).

Code defaults (change only if Ash says so):
- Java 21, Spring Boot 4.x, Spring Cloud 2025.1.x, Maven multi-module, package root `com.paypulse`.
- Versions come from the parent BOMs: never write a `<version>` on a BOM-managed dependency.
- Constructor injection, no field `@Autowired`. No Lombok (explicit code is easier to read and to explain).
- Money is `BigDecimal` / `NUMERIC`, never `double`. Schema is owned by Flyway, `ddl-auto=validate`.
- Boot 4 renamed several starters (for example `spring-boot-starter-webmvc`,
  `spring-cloud-starter-gateway-server-webflux`). **Don't guess artifact names or APIs: let the
  build prove them.** If unsure, try it and fix from the error.

## 2. Verify before you claim anything
- Run the build/tests yourself with Java 21 (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`),
  for example `mvn -q -pl <module> test`, and for infra `docker compose config -q`.
- Report the **real** result: command, outcome, test counts. If something fails, say so with the output.
- For anything with behaviour worth proving, also do one **mutation check** (break the key line in a
  throwaway copy, watch the test fail, discard the copy) and report it in one line.
- Never say "this should work". Either you ran it, or say "not run".

## 3. The chat explanation (after the code), in this order

Keep it short, simple and structured. Short sentences. Each section a few lines.

1. **What I built**: one line per file (path + purpose).
2. **How it works**: walk through it in the order things happen at runtime (request in, ... response out).
   Use the project's real names. A tiny example with real values beats an abstract description.
3. **Why this way**: the choice made and the one alternative rejected, in one line each.
4. **Traps**: 1 to 3 things that commonly go wrong here, and how this code avoids them.
5. **Run it**: exact commands and the expected output. Say what you actually saw when you ran them.
6. **Interview angle**: the 2 or 3 questions this step answers, with a **60-second spoken answer**
   for the main one, written the way Ash would say it (first person, plain, honest about scope).
7. **Check yourself**: 3 short questions (no answers inline). Put the answers at the very end under
   a line `--- answers ---` so Ash can try first.
8. **Next**: exactly one next action (usually "tell me when committed" or "ready for step N+1").

## 4. Keep the learning real (do not skip)
Reading is allowed instead of typing, but a card only counts as done when Ash has:
- run the code himself at least once (the "Run it" commands),
- answered the "Check yourself" questions aloud, and
- recorded the 2-minute explain-back from the card.

If Ash says "next" without running or answering, say once, in one line, that the explain-back is
still open, and carry on if he insists. Do not lecture.

## 5. Hygiene
- One branch per card (`l0-4-customer-service`). Never commit to `main`.
- Do not commit or push unless asked. When asked, stage specific paths, never `git add -A`.
- Never touch or print `infra/compose/.env`. `.env.example` is the committed one.
- Don't create docs/README files nobody asked for.
- If Ash asks for a "PR with planted bugs" (AI-PR drill), that is a different mode: don't use this
  skill's teaching comments to hint at the bugs, and keep the answer key out of the chat.

## 6. When the request is vague
Ask at most one short question (which card step?). If the card text in Trello or `plan.md` answers
it, read that instead of asking.
