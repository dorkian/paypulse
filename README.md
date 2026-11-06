# PayPulse

Synthetic B2B invoicing + buy-now-pay-later platform with its own risk and observability side.
A gamified interview-prep project: every level closes a real gap found in job applications.
All data is synthetic. Not a real financial product.

Plan, architecture and game rules live in the second-brain note
`01-projects/paypulse/plan.md`.

## Layout

```
pom.xml                    parent POM: Java 21, Spring Boot 4 + Spring Cloud 2025.1 BOMs, module list
platform/common-web        shared web library (errors, correlation id), no business logic
services/api-gateway       Spring Cloud Gateway, port 8080
services/customer-service  customers and tenants, port 8082
```

## Build and run

```bash
mvn -q verify                                      # build + test everything
mvn -pl services/customer-service spring-boot:run  # run one service
```

Requires Java 21 and Maven 3.9+. Shortcuts (`make help` lists them; Java 21 is pinned for you):

```bash
make up                       # infra profile (Postgres, Redis); PROFILE=messaging|identity|obs|apps for the others
make dev SERVICE=customer     # run one service from Maven (customer or gateway)
make test                     # build and test everything
make logs SERVICE=kafka       # follow one container's logs
make down                     # stop everything, data kept
```

The Makefile has its own tests: `scripts/test-makefile.sh` (dry run, finishes in a second).

## Local infrastructure

```bash
cd infra/compose
cp .env.example .env    # first time only
docker compose --profile infra --profile messaging --profile identity --profile obs up -d
```

Each group is a Compose profile, so you can start only what you need (for example `--profile infra`).

| Port | Service | Profile | URL / address |
|------|---------|---------|---------------|
| 5432 | PostgreSQL 16 | infra | `jdbc:postgresql://localhost:5432/paypulse` |
| 6379 | Redis | infra | `redis://localhost:6379` |
| 9092 | Kafka (KRaft, single node) | messaging | `localhost:9092` (containers: `kafka:19092`) |
| 5672 | RabbitMQ (AMQP) | messaging | `amqp://localhost:5672` |
| 15672 | RabbitMQ management | messaging | http://localhost:15672 |
| 8085 | Redpanda Console (Kafka UI) | messaging | http://localhost:8085 |
| 8180 | Keycloak | identity | http://localhost:8180 |
| 3000 | Grafana (otel-lgtm) | obs | http://localhost:3000 |
| 4317 | OTLP gRPC (otel-lgtm) | obs | `http://localhost:4317` |
| 4318 | OTLP HTTP (otel-lgtm) | obs | `http://localhost:4318` |
| 8080 | api-gateway (container) | apps | http://localhost:8080 (bound to 127.0.0.1 only) |
| 8082 | customer-service (container) | apps | http://localhost:8082 (bound to 127.0.0.1 only) |
| 5005 | customer-service debug (JDWP) | apps | attach IntelliJ "Remote JVM Debug" to `localhost:5005` |
| 5006 | api-gateway debug (JDWP) | apps | attach to `localhost:5006` |

Credentials come from `infra/compose/.env`. Keycloak is on 8180 so it does not clash with the gateway on 8080.

## Debugging

Two modes, full walkthrough in `docs/guides/l0-6-debug-modes.md`.
- **Mode B (daily):** `docker compose --profile infra up -d`, run one service from IntelliJ with the bug icon.
- **Mode A (same as Docker):** `mvn -q package jib:dockerBuild -DskipTests`, then
  `docker compose --profile infra --profile apps up -d`, then attach IntelliJ to port 5005 (gateway: 5006).
- Always pass **both** `--profile infra --profile apps`: the apps depend on `postgres`, which lives in `infra`.
- Debug ports are published on `127.0.0.1` only: a debug port lets whoever reaches it run code in the JVM.
- After a code change in Mode A, rebuild the image, otherwise the container runs the old jar.

## Status

L0 Spawn in progress: Maven module scaffold and local infrastructure (Compose) are in place. Next: shared web library and customer-service (L0-4).
