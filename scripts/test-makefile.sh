#!/usr/bin/env bash
# Tests for the root Makefile. They use `make -n` (dry run): make PRINTS the commands it would run
# instead of running them, so these tests never start Docker or Maven and finish in under a second.
# Run: scripts/test-makefile.sh   (exit 0 = all pass)
set -u
cd "$(dirname "$0")/.."

pass=0; fail=0
# check <name> <expected exit: 0|nonzero> <fragment that must appear in the output, or ""> <make args...>
check() {
  local name=$1 want=$2 frag=$3; shift 3
  local out rc
  out=$(make -n "$@" 2>&1); rc=$?
  if [[ $want == 0 && $rc -ne 0 ]] || [[ $want == nonzero && $rc -eq 0 ]]; then
    echo "FAIL $name: exit code $rc (wanted: $want)"; fail=$((fail+1)); return
  fi
  if [[ -n $frag ]] && ! grep -qF -- "$frag" <<<"$out"; then
    echo "FAIL $name: output does not contain: $frag"; fail=$((fail+1)); return
  fi
  echo "ok   $name"; pass=$((pass+1))
}

COMPOSE='docker compose -f infra/compose/docker-compose.yml'

# up: default profile is infra; PROFILE picks another one
check "up uses profile infra by default"   0 "$COMPOSE --profile infra up -d"        up
check "up PROFILE=messaging"               0 "--profile messaging up -d"             up PROFILE=messaging
# down: stops everything, every profile (a profile that is not named is not stopped)
check "down names all five profiles"       0 "--profile infra --profile messaging --profile identity --profile obs --profile apps down" down
# dev: run ONE service from Maven; SERVICE is required and must be known
check "dev customer runs customer-service" 0 "mvn -pl services/customer-service spring-boot:run" dev SERVICE=customer
check "dev gateway runs api-gateway"       0 "mvn -pl services/api-gateway spring-boot:run"      dev SERVICE=gateway
check "dev without SERVICE fails"          nonzero "SERVICE"                          dev
check "dev with unknown SERVICE fails"     nonzero "unknown"                          dev SERVICE=nope
# test: whole build with Java 21 (Homebrew's default JDK on this Mac is 27: wrong for this project)
check "test runs mvn verify on Java 21"    0 "mvn -q verify"                          test
# JAVA_HOME is exported by the Makefile, so `make -pn` (print make'"'"'s variable database) shows its value;
# the test then asks THAT java for its version, so it proves a real JDK 21, not just a word in the file.
jh=$(make -pn test 2>/dev/null | sed -n 's/^JAVA_HOME :\{0,1\}= //p' | head -1)
if [[ -n $jh ]] && "$jh/bin/java" -version 2>&1 | grep -q '"21\.'; then echo "ok   test pins JAVA_HOME to a real JDK 21"; pass=$((pass+1))
else echo "FAIL test pins JAVA_HOME to a real JDK 21 (got: '${jh:-nothing}')"; fail=$((fail+1)); fi
# logs: follow one container's logs; SERVICE is required
check "logs follows the service"           0 "$COMPOSE logs -f --tail=100 kafka"      logs SERVICE=kafka
check "logs without SERVICE fails"         nonzero "SERVICE"                          logs
# help: lists every target, so a new teammate can discover them
check "help lists the targets"             0 "make dev SERVICE="                      help

echo "---"; echo "passed: $pass  failed: $fail"
[[ $fail -eq 0 ]]
