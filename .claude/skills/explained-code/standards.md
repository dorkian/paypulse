# PayPulse engineering standards (mandatory)

Read this file before writing any backend code in this repo. Cite sections by number in the
chat explanation (for example "per S4.2"). If Ash's spec conflicts with a standard, say so in
one line with the reason, follow the standard, and record an override only if Ash insists.

**Strict does not mean heavy.** Apply each standard at the size of the task (YAGNI). Do not add a
layer, pattern or abstraction that the card does not need. A pattern is used only when it solves a
named problem that exists in the code today. Every pattern used must be named in the Javadoc and
justified in chat.

---

## S1. Structure of a service (package-by-feature, layered inside)

```
com.paypulse.<service>
├── <Service>Application
├── <feature>/                 one package per business capability (customer, invoice, ...)
│   ├── api/          controllers + request/response records        (HTTP only)
│   ├── service/      use cases, @Transactional boundary            (business flow)
│   ├── domain/       entities, value objects, domain exceptions    (business rules)
│   └── persistence/  Spring Data repositories                      (storage only)
└── config/           @ConfigurationProperties records, beans
```

Dependency direction: `api -> service -> domain` and `persistence -> domain`. Never the reverse.
`domain` knows nothing about HTTP, Kafka or Spring MVC. Enforce with an ArchUnit test (from L2).

- **S1.1 Controllers are thin.** Validate input (`@Valid`), call exactly one service method, map the
  result to a response, return the right status. No business logic, no repository access, no `@Transactional`.
- **S1.2 Services own use cases and transactions.** One public method per use case, named as a verb
  phrase (`createCustomer`, `postTransfer`). `@Transactional` here, never on controllers or repositories.
  Use `readOnly = true` for queries.
- **S1.3 Entities never leave the service layer.** Services return immutable DTO records. This prevents
  `LazyInitializationException` and accidental exposure of internal fields.
- **S1.4 DTOs are Java records.** Separate request and response types (`CreateCustomerRequest`,
  `CustomerResponse`). Never reuse an entity as a request body or response.
- **S1.5 No shared database, no shared domain classes between services.** Services talk through
  HTTP APIs or events only. `platform/*` holds infrastructure helpers and event schemas, never business logic.
- **S1.6 Sync call chains stay at most 2 hops.** Anything longer becomes an event or a saga.

## S2. Design patterns: where they belong in PayPulse

Use the named pattern, say its name in the class Javadoc (`Pattern: ...`), and say why.

| Pattern | Use it for | Do NOT use it for |
|---|---|---|
| Repository (Spring Data) | all persistence access | business logic in repository methods |
| Service layer / application service | use cases and transaction boundaries | pass-through methods that add nothing |
| DTO | every API boundary | internal calls between your own classes |
| Value object (record with validation in the compact constructor) | `Money`, `Email`, `TenantId` | entities that have identity and change over time |
| Rich domain model | invariants that must never break (ledger, invoice state) | plain CRUD (customer profile can stay simple) |
| State (enum with allowed transitions, or sealed interface) | invoice lifecycle DRAFT -> SENT -> PAID | flags and `if` chains scattered in services |
| Strategy (interface + implementations, chosen by config) | locking strategy, risk rules, notification channel | a single implementation with no second one planned |
| Adapter / Port | every external system (`PspClient`, mail, S3) behind an interface owned by the domain | internal classes |
| Anti-corruption layer | translating the PSP's or a third party's model into ours, so it never leaks inward | |
| Factory (static factory method) | creating entities that enforce invariants (`Account.open(...)`) | trivial constructors |
| Transactional Outbox | publishing an event atomically with a DB change (L3) | single-store changes |
| Idempotent Receiver | every Kafka/Rabbit consumer and every money-moving POST | read-only handlers |
| Saga (orchestration) | multi-service business transactions with compensation (L4) | anything that fits in one local transaction |
| Circuit Breaker, Retry, Bulkhead, Timeout (Resilience4j) | outbound calls | calls inside your own JVM |
| Builder | objects with many optional fields (rare; records + factory first) | everything else |

Avoid: singletons by hand, static utility grab-bags, inheritance for reuse (prefer composition),
CQRS or event sourcing (not in scope unless a card asks), generic "BaseService/BaseEntity" hierarchies.

## S3. Domain and value types
- **S3.1** Money is `BigDecimal` plus an ISO currency, in a `Money` value object. Never `double`/`float`.
  Rounding mode is explicit (`HALF_EVEN`), scale is fixed by ADR-001.
- **S3.2** Entities protect their invariants: private fields, no public setters for invariant fields,
  state changes through intention-revealing methods (`account.debit(amount)`), which throw a domain
  exception when the rule is broken. Constructors/factories never create an invalid object.
- **S3.3** JPA entity rules: protected no-arg constructor (JPA needs it), `final` where possible, `@Version`
  for optimistic locking on anything updated concurrently, `equals/hashCode` based on the id only, no
  `@Data`, no Lombok, no bidirectional associations unless a query needs them, `LAZY` by default.
- **S3.4** Time: inject a `java.time.Clock`; store `Instant` as `TIMESTAMPTZ` in UTC. Never call
  `Instant.now()` or `LocalDate.now()` directly in business code (it makes tests non-deterministic).
- **S3.5** IDs are UUIDs generated in the application, not by the database, so events can carry them
  before the insert.
- **S3.6** Prefer immutability: records, `final` fields, unmodifiable collections returned from getters.
  No `null` returned for collections (return empty). `Optional` only as a return type, never a field or parameter.

## S4. REST API
- **S4.1 Paths:** plural nouns, kebab-case, versioned: `/api/v1/customers`, `/api/v1/invoices/{id}`.
  The gateway forwards `/api/**` unchanged. A breaking change creates `/api/v2`, never edits v1.
- **S4.2 Status codes:**
  `201` + `Location` header on create; `200` on read/update with body; `204` on delete or no body;
  `400` malformed or failed validation; `401` unauthenticated; `403` authenticated but not allowed;
  `404` not found (also for another tenant's resource, so existence isn't leaked); `409` conflict
  (duplicate, optimistic-lock clash); `422` business rule violated (insufficient funds, illegal state
  transition); `429` rate limited; `500` only for real bugs.
- **S4.3 Errors:** always RFC 9457 `ProblemDetail`: `type` = `urn:paypulse:problem:<kebab-code>`, `title`,
  `status`, `detail`, plus `correlationId` and, for validation, a list of `{field, message}`.
  One `@RestControllerAdvice` (in `common-web`) maps domain exceptions; no try/catch in controllers.
  Never return stack traces, SQL or class names.
- **S4.4 Validation:** Bean Validation annotations on request records (`@NotBlank`, `@Email`, `@Size`,
  `@Positive`), `@Valid` on the controller parameter. Business rules are checked in the domain/service,
  not in annotations.
- **S4.5 Idempotency:** any POST that moves money or creates something expensive accepts an
  `Idempotency-Key` header and stores the outcome by key.
- **S4.6 Lists:** keyset (cursor) pagination for large or growing data (`?cursor=&limit=`, max limit
  enforced); simple `page/size` only for small admin lists. Always a stable sort.
- **S4.7 JSON:** `camelCase`, ISO-8601 UTC timestamps, money as a string or object with currency
  (never a bare float), no `null` fields noise (omit absent optionals consistently).
- **S4.8** Document with OpenAPI annotations on controllers (summary, responses). Keep the spec accurate.

## S5. Persistence (PostgreSQL + Flyway)
- **S5.1 Flyway owns the schema.** `spring.jpa.hibernate.ddl-auto=validate`. Migrations are named
  `V<n>__<verb>_<what>.sql` and are **immutable once merged**: fix a mistake with a new migration.
- **S5.2** Constraints live in the database as well as the code: `NOT NULL`, `UNIQUE`, `FOREIGN KEY`, `CHECK`.
  Add an index for every foreign key and every query predicate you introduce.
- **S5.3** Tables are plural snake_case (`customers`), columns snake_case, money `NUMERIC(19,4)`,
  timestamps `TIMESTAMPTZ`, ids `UUID`.
- **S5.4** `spring.jpa.open-in-view=false` (otherwise lazy loading hides in the controller). Reads that
  return lists use projections or fetch joins; check for N+1 with a query-count assertion.
- **S5.5** Locking: choose per ADR-002 (optimistic with `@Version` by default; pessimistic only where
  contention is proven). Always lock rows in a fixed order to avoid deadlocks.
- **S5.6** One database per service. No cross-service joins, no cross-service foreign keys.

## S6. Messaging (Kafka, RabbitMQ)
- **S6.1** Events are facts in the past tense (`InvoiceIssued`, `PaymentCaptured`). Commands are
  imperative (`CapturePayment`) and go to one owner.
- **S6.2** Envelope on every event: `eventId` (UUID), `eventType`, `eventVersion`, `occurredAt`,
  `aggregateId`, `correlationId`, `payload`.
- **S6.3** Never dual-write (DB then Kafka). Use the transactional outbox.
- **S6.4** Every consumer is idempotent (dedupe by `eventId`) and acknowledges only after its work commits.
- **S6.5** Key = the aggregate whose ordering matters. Schemas evolve backward-compatibly (add optional
  fields; never rename or retype).
- **S6.6** Failed messages get bounded retries with backoff, then a dead-letter topic/queue, plus an alert.
- **S6.7** Kafka = a log of facts many parties may replay. RabbitMQ = work to be done once. State
  which one you chose and why.

## S7. Resilience
- **S7.1** Every outbound call has a connect and a read timeout. No call is unbounded.
- **S7.2** Retry only idempotent operations, with exponential backoff and jitter, bounded attempts.
- **S7.3** Circuit breaker around unreliable dependencies; bulkhead to isolate them; a fallback only
  when a safe one exists (never a fake success for money).
- **S7.4** `server.shutdown=graceful` and a timeout, so rollouts don't drop requests.

## S8. Configuration and security
- **S8.1** Group settings in `@ConfigurationProperties` records with `@Validated`, not scattered `@Value`.
- **S8.2** No secrets in git. Values come from environment variables (`${...}`); `.env.example` documents them.
- **S8.3** Validate all input at the boundary; treat headers (including `X-Correlation-Id`) as untrusted.
- **S8.4** Least privilege: minimal actuator exposure, explicit CORS origins, per-service DB roles (from L2).
- **S8.5** Never log secrets, tokens, full emails/IBANs/card data. Mask or omit personal data.
- **S8.6** Authorize in the service layer by role AND tenant; check the role before looking up the resource
  so a wrong-role caller never learns whether a row exists.

## S9. Logging and observability
- **S9.1** SLF4J with `{}` placeholders, never string concatenation. No `System.out`, no `printStackTrace`.
- **S9.2** Levels: `ERROR` = needs a human; `WARN` = unexpected but handled; `INFO` = business events
  (one line per use case with ids); `DEBUG` = diagnostics. Don't log inside tight loops.
- **S9.3** Log lines carry `correlationId` (MDC). Log an exception once, where it is handled, with its stack.
- **S9.4** Metrics with Micrometer: counters/timers named `paypulse.<service>.<thing>`; low-cardinality tags only
  (never user ids or emails as tags).
- **S9.5** Separate liveness and readiness health groups.

## S10. Error handling in code
- **S10.1** Domain exceptions extend one base (`PayPulseException`) with a stable error code. Unchecked.
- **S10.2** Never `catch (Exception)` or swallow an exception. Catch the narrowest type, at the layer that
  can do something about it, or let it propagate to the advice.
- **S10.3** Fail fast: `Objects.requireNonNull`, argument checks in constructors/factories.
- **S10.4** Don't use exceptions for normal flow; don't return `null` to signal failure.

## S11. Testing
- **S11.1 Pyramid:** many fast unit tests (domain and service logic, no Spring context), some slice tests
  (`@WebMvcTest` for controllers, `@DataJpaTest` with Testcontainers for repositories), few integration
  tests (full context + Testcontainers), architecture tests (ArchUnit). Boot 4 moved some test
  annotations to new packages: take imports from the compiler, don't guess.
- **S11.2** Testcontainers with the real database, never H2.
- **S11.3** Names say behaviour: `shouldRejectTransferWhenFundsAreInsufficient`. Structure is
  Arrange / Act / Assert with blank lines between. One behaviour per test.
- **S11.4** Deterministic: fixed `Clock`, no `Thread.sleep` (use Awaitility), no shared mutable state, no order dependence.
- **S11.5** Assert behaviour and results, not internals. Don't mock what you own; mock outbound ports.
- **S11.6** Every bug fix starts with a failing test. Every interesting branch has a test (empty, blank, error).
- **S11.7** Integration tests are named `*IntegrationTest`; unit tests `*Test`.

## S12. Code style and format
- **S12.1** `.editorconfig` rules: UTF-8, LF, 4 spaces (2 for yml/json/md), final newline, no trailing spaces.
- **S12.2** Max line length 120. No wildcard imports. No unused imports. No commented-out code.
- **S12.3** Member order: constants, fields, constructors, public methods, private methods.
  Package-private by default; `public` only when another package needs it. `final` fields and parameters
  where it clarifies.
- **S12.4** Names: `*Controller`, `*Service`, `*Repository`, `*Request`, `*Response`, `*Exception`,
  `*Properties`. Methods are verbs, booleans read as `isX/hasX`. No abbreviations, no `Manager`/`Helper`/`Util`.
- **S12.5** Methods do one thing and fit on a screen (about 20 lines). Prefer early returns over deep nesting.
  No magic numbers: named constants or configuration.
- **S12.6** Use Java 21 where it makes code clearer: records, sealed interfaces for closed sets, pattern
  matching `switch`, `var` only when the type is obvious on the right-hand side.
- **S12.7** Commit messages follow Conventional Commits (`feat(customer): add create endpoint`),
  imperative, one logical change per commit.

## S13. Anti-patterns: refuse them, even if asked casually
Field injection (`@Autowired` on fields) - entities in API payloads - business logic in controllers -
`@Transactional` on controllers - `ddl-auto=update` - `open-in-view=true` - shared database between
services - synchronous chains longer than 2 hops - two-phase commit - floating image tags for databases -
`catch (Exception)` that swallows - `System.out` - hardcoded URLs/credentials - sleeping in tests -
God services (a service class with unrelated use cases) - generic `BaseEntity/BaseService` hierarchies.

## S14. Definition of Done: self-review before delivering
Go through this list before you hand the step over. Report any item you skipped, and why.
1. Compiles; tests pass on Java 21; `mvn -q verify` for the touched modules (real output reported).
2. Layering respected (S1); no entity outside the service layer; controller is thin.
3. Every pattern used is named in Javadoc and justified in chat (S2); nothing added "just in case".
4. Inputs validated; errors are ProblemDetail with a stable code; correct status codes (S4).
5. Transaction boundary is explicit and minimal; no remote call inside a transaction (S1.2).
6. Schema change is a new immutable Flyway migration with constraints and indexes (S5).
7. No secrets, no personal data in logs, no hardcoded config (S8, S9).
8. Tests cover happy path, each error branch, and one mutation check for the key behaviour (S11).
9. Style matches `.editorconfig` and S12; no unused imports, TODOs or commented-out code.
10. Teaching comments present where required by SKILL.md section 1, and accurate.
