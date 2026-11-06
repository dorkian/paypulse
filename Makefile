# Developer shortcuts. `make help` lists them. Tests: scripts/test-makefile.sh (uses `make -n`, a dry run).

# Homebrew's default JDK on this Mac is 27, which is wrong for this project (Java 21).
# `:=` (not `?=`) so a JAVA_HOME already set in your shell (for example JDK 27) cannot win.
# macOS-specific: /usr/libexec/java_home picks an installed JDK by version.
JAVA_HOME := $(shell /usr/libexec/java_home -v 21)
export JAVA_HOME

COMPOSE := docker compose -f infra/compose/docker-compose.yml
PROFILE ?= infra
# SERVICE is the short name used on the command line; MODULE is the Maven module folder.
MODULE_customer := customer-service
MODULE_gateway  := api-gateway

.PHONY: help up down dev test logs

help: ## Show this list
	@echo "make up [PROFILE=infra|messaging|identity|obs|apps]   start a compose profile (default: infra)"
	@echo "make down                                             stop everything (all profiles, data kept)"
	@echo "make dev SERVICE=customer|gateway                     run one service from Maven"
	@echo "make test                                             build and test everything (Java 21)"
	@echo "make logs SERVICE=<compose service>                   follow one container's logs"
	@echo "Apps in containers need both infra and apps: make up PROFILE=infra && make up PROFILE=apps"

up: ## Start one compose profile
	$(COMPOSE) --profile $(PROFILE) up -d

# Every profile must be named, otherwise Compose ignores the services of the unnamed ones.
# No -v: volumes (your data) are kept.
down: ## Stop everything
	$(COMPOSE) --profile infra --profile messaging --profile identity --profile obs --profile apps down

dev: ## Run one service with Maven
ifndef SERVICE
	$(error SERVICE is required, for example: make dev SERVICE=customer)
endif
ifndef MODULE_$(SERVICE)
	$(error unknown SERVICE "$(SERVICE)": use customer or gateway)
endif
	mvn -pl services/$(MODULE_$(SERVICE)) spring-boot:run

test: ## Build and test everything
	mvn -q verify

logs: ## Follow one container's logs
ifndef SERVICE
	$(error SERVICE is required, for example: make logs SERVICE=kafka)
endif
	$(COMPOSE) logs -f --tail=100 $(SERVICE)
