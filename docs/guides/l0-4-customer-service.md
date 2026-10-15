# L0-4 guide: common-web error handling + customer-service

Goal: every service answers errors the same way (RFC 9457 ProblemDetail), and `customer-service`
can create, read and list customers, with a real database, tests and `/api/v1` paths.

How to use: do ONE step, run its check, commit when a part is finished. Each step says what to
write, where, why (standard number in `.claude/skills/explained-code/standards.md`), and how to
check it. Never start the next step before the check is green.

Base state: branch `l0-4-customer-service`, `mvn -q verify` green, only `CorrelationIdFilter`
(+ 4 tests) exists in `common-web`. An earlier draft is in `git stash` (reference only, see the end).

Java 21 first: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && mvn -v | sed -n 3p`.
Boot 4 renamed some artifacts and test packages: take names from the compiler/build, don't guess.

---

## PART A: `platform/common-web` (shared by all services)

### A1. Register the filter automatically
- **Why:** a library class does nothing until Spring knows about it. Auto-configuration lets any
  service that depends on `common-web` get the filter with zero code. (S1.5)
- **Write:** `CommonWebAutoConfiguration` (`@AutoConfiguration`) with a `@Bean` that returns a
  `CorrelationIdFilter`. List the class in
  `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  (one fully-qualified class name per line).
- **Test:** `CommonWebAutoConfigurationTest` using `WebApplicationContextRunner`: the context contains the filter bean.
- **Check:** `mvn -q -pl platform/common-web test`

### A2. The error model
- **Why:** services should say *what* went wrong in domain terms; only one place decides the HTTP
  status. (S10.1)
- **Write** in package `com.paypulse.common.web.error`:
  - `enum ErrorCategory { NOT_FOUND, CONFLICT, BUSINESS_RULE }`
  - `abstract class PayPulseException extends RuntimeException` with a stable `code`
    (kebab-case string, for example `customer-not-found`) and an `ErrorCategory`.
- **Check:** `mvn -q -pl platform/common-web compile`

### A3. The error handler
- **Why:** one `@RestControllerAdvice` for all services, so no try/catch in controllers. (S4.3)
- **Write** `ProblemDetailExceptionHandler` mapping:
  - `PayPulseException` by category: NOT_FOUND to 404, CONFLICT to 409, BUSINESS_RULE to 422.
  - `MethodArgumentNotValidException` to 400 with a `errors` list of `{field, message}`.
  - any other `Exception` to 500 with a generic message. Log it once with the stack; the response must
    never contain the exception message, class name or stack.
  - every ProblemDetail: `type = urn:paypulse:problem:<code>`, `title`, `status`, `detail`, and a
    `correlationId` property read from the MDC.
- **Check:** `mvn -q -pl platform/common-web compile`

### A4. Register the handler
- **Write:** add a `@Bean` for the handler in `CommonWebAutoConfiguration`. Only for servlet web apps
  (`@ConditionalOnWebApplication(type = SERVLET)`), so the gateway (reactive) isn't affected.
- **Check:** extend the A1 test: handler bean present in a servlet context.

### A5. Test the handler
- **Write** `ProblemDetailExceptionHandlerTest` with MockMvc standalone setup and a tiny test controller:
  404, 409, 422, 400 with field errors, and 500 proving **no leak** (no stack text, no class name).
  Also assert `correlationId` is in the body and `Content-Type` is `application/problem+json`.
- **Mutation check:** change the 500 branch to return `ex.getMessage()`, watch the no-leak test fail, revert.
- **Check:** `mvn -q -pl platform/common-web test`
- **Commit:** `git add platform && git commit -m "feat(common-web): auto-registered correlation filter and ProblemDetail error handling"`

---

## PART B: `services/customer-service`

Package layout for the feature (S1): `com.paypulse.customer.customer.{api,service,domain,persistence}`
plus `com.paypulse.customer.config`.

### B1. Dependencies (pom)
- **Add** (no `<version>`, the BOM manages them): `spring-boot-starter-data-jpa`,
  `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql`
  (runtime), `io.micrometer:micrometer-registry-prometheus` (runtime), and for tests Spring Boot's
  Testcontainers support plus the Testcontainers PostgreSQL module.
- **Check:** `mvn -q -pl services/customer-service compile`
- **Note:** Boot 4 needs the Flyway *starter*; plain `flyway-core` is not enough.

### B2. `application.yml`
- **Write:** datasource from env vars (`CUSTOMER_DB_URL`, `CUSTOMER_DB_USER` with local defaults,
  `CUSTOMER_DB_PASSWORD` with **no default**); `jpa.hibernate.ddl-auto: validate`;
  `jpa.open-in-view: false` (the Boot default is **true**, so this line matters; verified in
  `spring-boot-jpa-4.0.8.jar`); `server.shutdown: graceful`; actuator exposing `health,info,prometheus`
  with liveness/readiness probes enabled. Comment each non-obvious key. (S5.4, S7.4, S8.2, S9.5)

### B3. Migration `V1__create_customers.sql` (`src/main/resources/db/migration`)
- **Write:** table `customers` (plural, S5.3): `id UUID PRIMARY KEY`, `tenant_id UUID NOT NULL`,
  `name VARCHAR(200) NOT NULL`, `email VARCHAR(320) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`,
  and `CONSTRAINT uq_customers_tenant_email UNIQUE (tenant_id, email)`. (S5.1–S5.3)
- **Why unique per tenant:** two merchants may each have a customer with the same email, and the
  database, not Java, must win a race between two requests.

### B4. Domain entity `Customer` (`.../domain`)
- **Write:** `@Entity @Table(name = "customers")`; protected no-arg constructor for JPA; a **static
  factory** `Customer.create(tenantId, name, email, Instant now)` that null-checks, trims the name and
  **lowercases + trims the email**; `id` generated in the app (`UUID.randomUUID()`); getters only;
  `equals/hashCode` based on `id`. (S3.2, S3.3, S3.5, S10.3). No `Instant.now()` inside (S3.4).
- **Pattern to name in Javadoc:** Factory method.

### B5. `CustomerRepository` (`.../persistence`)
- **Write:** an interface extending `JpaRepository<Customer, UUID>`. Add the one query the list needs
  (newest first, limited by a `Pageable`). Pattern: Repository.

### B6. Domain exceptions (`.../domain`)
- **Write:** `CustomerNotFoundException` (NOT_FOUND, code `customer-not-found`) and
  `DuplicateCustomerException` (CONFLICT, code `customer-email-taken`), both extending `PayPulseException`.

### B7. `Clock` bean (`.../config`)
- **Write:** `@Configuration` exposing `Clock.systemUTC()`. Why: tests can inject a fixed clock. (S3.4)

### B8. DTO records (`.../api`)
- **Write:** `CreateCustomerRequest(tenantId, name, email)` with `@NotNull`, `@NotBlank`, `@Email`,
  `@Size(max=...)` matching the column sizes; `CustomerResponse(id, tenantId, name, email, createdAt)`
  with a static `from(Customer)`. (S1.4, S4.4)
- **Temporary:** `tenantId` comes from the request until L2 (then from the login token). Comment it.

### B9. `CustomerService` (`.../service`)
- **Write:** `@Service`, constructor-injected repository + `Clock`. Methods:
  - `createCustomer(request)`: `@Transactional`; build via `Customer.create(..., clock.instant())`;
    save and **flush**; translate `DataIntegrityViolationException` to `DuplicateCustomerException`;
    one INFO log with the new id (never the email, S8.5); return `CustomerResponse`.
  - `getCustomer(id)`: `@Transactional(readOnly = true)`; throw `CustomerNotFoundException`.
  - `listCustomers(limit)`: read-only; **cap the limit** (default 50, max 100). (S4.6)
- Returns DTOs only, never entities. (S1.2, S1.3)

### B10. `CustomerController` (`.../api`)
- **Write:** `@RestController @RequestMapping("/api/v1/customers")`. `POST` returns **201 + `Location`**
  built with `ServletUriComponentsBuilder.fromCurrentRequest()`; `GET /{id}`; `GET ?limit=`.
  `@Valid @RequestBody`. No business logic, no repository. (S1.1, S4.1, S4.2)

### B11. Integration test
- **Delete** the old `CustomerServiceApplicationTests` (it needs a live database, so it fails on a clean machine).
- **Write** `CustomerApiIntegrationTest` with a Testcontainers PostgreSQL (real DB, never H2, S11.2). Cases:
  1. create returns 201 and a `Location` header
  2. get returns 200 with the same data
  3. unknown id returns 404 `application/problem+json` with `correlationId`
  4. the same (tenant, email) twice returns 409
  5. the same email with different case also returns 409 (normalization works)
  6. invalid email returns 400 with a field error
  7. every response has an `X-Correlation-Id` header
- **Mutation check:** remove the `lowercase` in the factory, watch case 5 fail, revert.
- **Check:** `mvn -q -pl services/customer-service -am verify` (needs Docker running)

### B12. (optional) `CustomerServiceTest`
- **Write:** Mockito unit test of the service: email lowercased, unique violation becomes
  `DuplicateCustomerException`, limit is capped. Fixed `Clock`. (S11.1)
- **Commit:** `git add services && git commit -m "feat(customer): customers API v1 with Flyway, Testcontainers tests"`

---

## PART C: run it for real, then finish

### C1. Run and try it
```
export CUSTOMER_DB_PASSWORD=<the POSTGRES_PASSWORD value from infra/compose/.env>
mvn -pl services/customer-service spring-boot:run
curl -i -X POST localhost:8082/api/v1/customers -H 'Content-Type: application/json' \
  -d '{"tenantId":"11111111-1111-1111-1111-111111111111","name":"Mario","email":"Mario@Example.com"}'
curl -i localhost:8082/api/v1/customers/<id-from-Location>
curl -s localhost:8082/actuator/health ; curl -s localhost:8082/actuator/prometheus | head
```
Expected: 201 + `Location` + `X-Correlation-Id`; the email is stored lowercase; the same POST twice gives
409 `application/problem+json`.

### C2. Definition of Done (S14), then PR
- `mvn -q verify` green from the root; no `.env` staged; no TODOs.
- `git push -u origin l0-4-customer-service`, open the PR, review your own diff in the browser first.
- Explain-back (2 min): why a service layer? why `ddl-auto=validate`? why does the handler never return
  `ex.getMessage()` for a 500? why `open-in-view=false`?
- Tick the checklist on the Trello card L0-4 and move it to Done.

---

## About the stashed draft
`git stash list` shows `l0-4 draft from VS Code ...`. It is reference only: it has useful ideas but
deviates from the standards (flat package, no service layer, `/customers` path, `Instant.now()`).
Peek without applying: `git stash show -p stash@{0} -- platform/common-web`.
Delete it when done: `git stash drop stash@{0}`.
