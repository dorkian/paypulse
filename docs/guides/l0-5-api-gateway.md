# L0-5 guide: api-gateway, the front door

## TL;DR (read this first)

**What is this ticket, in one minute?**
Right now, to create a customer you must call `customer-service` directly on port **8082**. In a
few weeks there will be 10 services on 10 different ports. Nobody (a browser, a phone app, a
partner) should have to know that. So we build **one front door on port 8080: the api-gateway**.
Think of the reception desk in a hotel: guests (clients) talk to reception, and reception sends
them to the right room (service).

The gateway will do two jobs now:
1. **Route:** a request to `/api/v1/customers/...` is forwarded to `customer-service`.
2. **Tracking number:** every request gets a correlation ID (`X-Correlation-Id`). If the caller sent a
   good one we keep it, if it is missing or looks dangerous we make a new one. The same ID is passed to
   the service and comes back in the response, so one search finds every log line of one request.

**What will work when it is done**
```
curl -i -X POST localhost:8080/api/v1/customers ...   ->  201 Created, same as calling 8082
                                                           + header X-Correlation-Id: <uuid>
curl -i localhost:8080/api/v1/unknown                  ->  404 (no such route)
stop customer-service, call again                      ->  a fast 5xx error, not a hanging request
automated tests prove all of it, with no real service needed
```
Clients only ever use port 8080. Ports of the services become an internal detail.

**What is NOT in this ticket:** login/JWT and rate limiting (L2), the other services' routes
(added as the services appear), pretty ProblemDetail errors when the service is down (later).

Time: about 90 minutes. Standards: S4.1 (versioned paths), S7.1 (timeouts), S8.3 (untrusted headers),
S1.1 (no business logic in the edge).

---

## Base state
- `main` is green (`mvn -q verify`). `customer-service` serves `/api/v1/customers` on 8082.
- `api-gateway` is only a scaffold: starts on 8080 and has no routes.
- Java 21 active (`mvn -v | sed -n 3p`).
- Branch first: `git checkout main && git pull origin main && git checkout -b l0-5-api-gateway`

Route format changed in Spring Cloud 2025.1: the properties live under
`spring.cloud.gateway.server.webflux.*` (checked in the gateway 5.0.3 jar), not `spring.cloud.gateway.*`
as in older tutorials.

---

## PART A: build the gateway

### A1. The route and the timeouts (`services/api-gateway/src/main/resources/application.yml`)
- **Why:** the route tells the gateway where to send what (S4.1). Timeouts make sure a stuck service
  cannot hang the gateway forever (S7.1).
- **Write:** under `spring.cloud.gateway.server.webflux`:
  - one route with `id: customer-service`, `uri: ${CUSTOMER_SERVICE_URL:http://localhost:8082}`,
    predicate `Path=/api/v1/customers/**`. **No StripPrefix**: the service already uses the same
    path, so the gateway forwards it unchanged.
  - `httpclient.connect-timeout: 2000` (milliseconds) and `httpclient.response-timeout: 10s`.
  - Comment each key: what it does and what breaks without it.
- **Decision note:** do not use a catch-all `/api/**` route to customer-service. When `invoice-service`
  arrives, `/api/v1/invoices/**` must go there. One route per resource family.
- **Check:** start customer-service, then `mvn -pl services/api-gateway spring-boot:run`, then
  `curl -i localhost:8080/api/v1/customers` returns the same list as port 8082.
  (customer-service needs `CUSTOMER_DB_USER` and `CUSTOMER_DB_PASSWORD`, see B1.)

### A2. A pure function for the ID decision
- **Why:** the "keep it or replace it" rule is the part worth testing hard, so keep it free of web code (S11.1).
- **Write** `com.paypulse.gateway.correlation.CorrelationIds` with `static String resolve(String incoming)`:
  - incoming matches `^[A-Za-z0-9-]{1,64}$` -> return it unchanged
  - null, blank, too long (over 64), or any other character -> return `UUID.randomUUID().toString()`
- **Why validate here and not in common-web?** The gateway is the public edge. Anything from outside
  is untrusted (S8.3): a 10,000-character header bloats every log line, odd characters can forge log
  entries. Internal services may trust what the gateway forwards.

### A3. The global filter
- **Why:** a `GlobalFilter` runs for every route. This is the Cross-cutting concern pattern at the edge.
- **Write** `CorrelationIdGlobalFilter implements GlobalFilter, Ordered` in the same package:
  1. read `X-Correlation-Id` from the incoming request, call `CorrelationIds.resolve(...)`
  2. build a **new** request with that header set (requests are immutable: `request.mutate().header(...).build()`)
     and continue the chain with `exchange.mutate().request(newRequest).build()`
  3. set the same header on the **response** before the chain runs (headers can't change once committed)
  4. order `Ordered.HIGHEST_PRECEDENCE`, so it runs before any other filter
- **Do not use MDC here.** The gateway is reactive (WebFlux): one request hops between threads, so a
  thread-local MDC would be wrong or leak. Put a comment saying so. Logging the ID in the gateway properly
  needs the Reactor context, planned for L6 (observability).
- **Check:** `mvn -q -pl services/api-gateway compile`

### A4. Unit test the decision
- **Write** `CorrelationIdsTest`: valid id kept; null, blank, 65 chars, a value with a space, and a value
  with a newline are each replaced by a valid UUID; two calls with null give two different UUIDs.
- **Mutation check:** change `{1,64}` to `{1,6400}`, watch the "too long" test fail, revert.
- **Check:** `mvn -q -pl services/api-gateway test`

### A5. Integration test with a fake downstream
- **Why:** proves the whole gateway (route + filter + timeouts config) without needing the real service.
- **Write** `GatewayRoutingIntegrationTest`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` and a
  `WebTestClient`. Start a tiny stub server with the JDK's `com.sun.net.httpserver.HttpServer` on port 0
  (no new dependency) that records the path and headers it receives and answers 200 with a small JSON.
  Point the route at it with `@DynamicPropertySource` (set `CUSTOMER_SERVICE_URL`).
  If `WebTestClient` is not found, add the Boot 4 WebFlux test starter; take the artifact name from the build.
- **Cases:**
  1. `GET /api/v1/customers/abc` reaches the stub with path `/api/v1/customers/abc`, response body passes through
  2. no incoming header -> the **stub received** an `X-Correlation-Id`, and the **response** has the same value
  3. a good incoming id (`abc-123`) is kept end to end
  4. a bad incoming id (contains a space and is 100 characters) is replaced by a valid UUID
  5. `GET /api/v1/nothing` -> 404 (no route)
  6. the stub also shows which `X-Forwarded-*` headers the gateway adds (print or assert them; B2 needs this)
- **Check:** `mvn -q -pl services/api-gateway test`

---

## PART B: run it for real

### B1. End to end
```
cd ~/Documents/workspace/repos/paypulse/infra/compose && docker compose --profile infra up -d
# load the DB credentials without printing them:
set -a; source .env; set +a
export CUSTOMER_DB_USER=$POSTGRES_USER CUSTOMER_DB_PASSWORD=$POSTGRES_PASSWORD
cd ../.. && mvn -pl services/customer-service spring-boot:run      # terminal 1
mvn -pl services/api-gateway spring-boot:run                       # terminal 2
curl -i -X POST localhost:8080/api/v1/customers -H 'Content-Type: application/json' \
  -d '{"tenantId":"11111111-1111-1111-1111-111111111111","name":"Gina","email":"Gina@Example.com"}'
```
Expected: `201`, an `X-Correlation-Id`, and a `Location` header. Look closely at the `Location` value.

### B2. The Location trap (you will most likely see it)
The service builds `Location` from the request it receives. Behind a gateway that request says
"I was sent to localhost:8082", so `Location` probably shows the **internal** address, not the one the
client used (8080). The gateway sends `X-Forwarded-*` headers that tell the truth, but Spring only
trusts them when you ask.
- **Write:** in `customer-service` `application.yml`: `server.forward-headers-strategy: framework`, with a comment.
- **Test:** in `CustomerApiIntegrationTest` add a case that sends `X-Forwarded-Host` and `X-Forwarded-Proto`
  and asserts `Location` uses them.
- **Check:** repeat the POST through 8080: `Location` now points to `localhost:8080/api/v1/customers/<id>`.
- **Security note:** only enable this when a trusted proxy sits in front. A service reachable directly from the
  internet would let anyone fake its own address.

### B3. Failure behaviour
Stop customer-service (Ctrl+C), then `curl -i localhost:8080/api/v1/customers`. Expect a **fast** 5xx
(connection refused is quick; a hung service would hit the 10 s response timeout). Note the body is the
gateway's own error JSON, not our ProblemDetail format. Leave it for now, write it in the commit message as a known gap.

---

## PART C: finish
- Definition of Done (S14): `mvn -q verify` green from the root; no `.env` staged; no TODOs.
- Commits: `feat(gateway): route /api/v1/customers and correlation id filter`, then
  `fix(customer): honour forwarded headers for Location`.
- `git push -u origin l0-5-api-gateway`, open the PR, read your own diff in the browser first.
- Do the quiz (ask Claude: "quiz me on L0-5"), then tick the Trello checklist and move the card.

---

## Questions and answers (explain-back)

**Q1. Why a gateway at all? Why not let clients call the services?**
*Remember:* one front door, many rooms behind it.
*Use case:* the mobile app has one base URL. Next month `invoice-service` is split in two and moves to a
new port. With a gateway only the gateway config changes; the app is untouched. The gateway is also the one
place for things every request needs: authentication (L2), rate limiting, the correlation ID.
*Say it:* "A gateway is a single entry point. It hides the internal layout, so services can move or split
without breaking clients, and it holds the cross-cutting concerns like auth, limits and tracing in one place."

**Q2. What are a Route, a Predicate and a Filter?**
*Remember:* the Route is the rule, the Predicate asks "does this request match?", the Filter changes the request/response on the way.
*Use case (our config):* route `customer-service` has predicate `Path=/api/v1/customers/**` ("match these URLs")
and goes to `http://localhost:8082`. Our `CorrelationIdGlobalFilter` is a filter that adds the tracking header.
*Say it:* "A route is an id, a destination and predicates that decide which requests it takes; filters then
modify the request or response before and after the call."

**Q3. Why is there no business logic in the gateway?**
*Remember:* the receptionist directs guests, doesn't cook dinner.
*Use case:* if the gateway checked "does this customer exist", it would need the customer database, so it
would become a second customer-service and a bottleneck that every team must change. Edge code stays generic.
*Say it:* "The gateway only does routing and cross-cutting concerns. Business rules live in the owning
service, otherwise the gateway becomes a coupled single point of change and failure."

**Q4. Why can't we reuse the `CorrelationIdFilter` from common-web in the gateway?**
*Remember:* two different engines.
*Use case:* `common-web`'s filter is a servlet filter (one thread per request, MDC works). The gateway is
reactive WebFlux: one request can jump between threads, so a thread-local MDC would lose or leak the ID. The
auto-configuration is limited to servlet apps for this reason. In the gateway we use a `GlobalFilter` and pass
the ID in headers.
*Say it:* "Servlet and reactive are different programming models. The thread-bound MDC doesn't work in
reactive code, so the gateway uses a reactive GlobalFilter and carries the ID in headers; logging it properly there needs the Reactor context."

**Q5. Why validate the incoming correlation ID at the edge?**
*Remember:* never trust what comes from outside.
*Use case:* a client sends a 10,000-character `X-Correlation-Id`: every log line of that request becomes
huge. Another sends characters that look like extra log lines and forges entries (log injection). The gateway
keeps only `[A-Za-z0-9-]{1,64}` and otherwise makes a fresh UUID.
*Say it:* "Headers are untrusted input. At the public edge I accept a safe, short ID or replace it, so logs
can't be flooded or forged. Internal services can then trust what the gateway forwards."

**Q6. Why is the service URL an environment variable?**
*Remember:* same build, different place.
*Use case:* on your laptop it is `http://localhost:8082`; in Docker Compose it is `http://customer-service:8082`;
in Kubernetes it is the Service name. One jar, no rebuild, only the environment changes (12-factor config).
*Say it:* "Addresses depend on where the app runs, so they come from the environment with a local default.
The artifact stays identical across dev, CI and production."

**Q7. Why timeouts on the gateway, and what do 2 s / 10 s mean?**
*Remember:* a call that can wait forever will.
*Use case:* `customer-service` freezes on a slow query. Without a limit, each request waits and ties up gateway
connections until the whole gateway stops answering, even for healthy services. With a 2 s connect and 10 s
response limit, callers get a fast error and the gateway stays alive.
*Say it:* "Every outbound call needs a connect and a response timeout so a slow dependency fails fast instead
of exhausting resources and taking down unrelated routes."

**Q8. Why did `Location` show the internal address, and what fixes it?**
*Remember:* the service only knows who called it (the gateway), not who the real client was.
*Use case:* client calls `localhost:8080`, gateway calls `localhost:8082`. The service builds `Location` from
what it saw, so it would say 8082. The gateway adds `X-Forwarded-Host/Proto/Port` headers; with
`server.forward-headers-strategy: framework` Spring uses them, so `Location` shows the address the client used.
Enable it only behind a trusted proxy.
*Say it:* "Behind a proxy the service sees the proxy's address. X-Forwarded headers carry the original one, and
framework forward-header support makes generated links correct. It's only safe when a trusted proxy sets them."

**Q9. Why one route per resource family and not a catch-all `/api/**`?**
*Remember:* the address decides the room.
*Use case:* with `/api/**` going to customer-service, adding `invoice-service` means fighting over who owns
which path, and a typo path hits the wrong service. `/api/v1/customers/**` and `/api/v1/invoices/**` make
ownership obvious, and an unknown path is a clean 404.
*Say it:* "Each route maps a path family to its owning service. A catch-all hides ownership and breaks as soon as
a second service appears."
