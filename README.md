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

## Status

L0 Spawn in progress: module scaffold only. Infrastructure (Compose) comes in L0-3.
