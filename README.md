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

Requires Java 21 and Maven 3.9+.

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

Credentials come from `infra/compose/.env`. Keycloak is on 8180 so it does not clash with the gateway on 8080.

## Status

L0 Spawn in progress: Maven module scaffold and local infrastructure (Compose) are in place. Next: shared web library and customer-service (L0-4).
