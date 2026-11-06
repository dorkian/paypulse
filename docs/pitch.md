# 60-second pitch (L0)

Rule: say only what exists today. The roadmap is "next", never "built". Update the "Today" lines at the end of each level.

## Say it (about 60 seconds, first person)

"I'm building PayPulse, a fintech platform with invoicing and buy-now-pay-later, plus a risk and observability side.
It's my own project to go deep on the parts of backend work that matter most: transactions, messaging and operability.

Today it runs as Spring Boot 4 services on Java 21 in a Maven multi-module build. There is an API gateway and a customer
service with Flyway migrations, errors in the standard problem-details format, and a correlation id that follows every
request. The whole stack runs locally in Docker Compose: Postgres, Kafka, RabbitMQ, Keycloak and a Grafana observability
stack. I build the images with Jib, and I can debug the same service two ways: attached to the container, or running in
my IDE with the rest in Docker.

Next I'm building the ledger: double-entry bookkeeping, locking under concurrent transfers, and then Kafka events.
Everything is synthetic data, and it's a learning project, not production work."

## Today (L0 done) / Next (not built)
- Today: gateway, customer-service, shared web library, Compose infra, Jib images, two debug modes, Makefile.
- Next: ledger, security and tenants, Kafka and sagas, Angular, observability, Kubernetes, AWS.

## Do not say
- "production", "ten services running", "deployed on Kubernetes or AWS": none of that exists yet.
- Anything about the ledger as done: L1 has not started.
