# L0-6 guide: your first breakpoint, in two debug modes

## TL;DR (read this first)

**What is this ticket, in one minute?**
A *breakpoint* freezes your program on one line so you can look inside it: the variables, the request, the
path the code took. Until now you only ran services and read logs. This ticket teaches the two ways you will
debug for the rest of the project:

- **Mode B, the daily one:** *one* service runs on your Mac inside IntelliJ, everything else runs in Docker.
  Like a doctor examining a patient **in the office**: easy, fast, hot reload.
- **Mode A, "inside the container":** the service runs in a container, and IntelliJ **attaches from outside**
  through a debug port (5005). Like a doctor on a **video call into a hospital room**: you debug the thing
  exactly as it runs in Docker (and later in Kubernetes), with no code change.

To run services in containers we also learn **Jib**, a Maven plugin that builds a Docker image from your project
without writing a Dockerfile.

**What will work when it is done**
```
docker image ls 'paypulse/*'                        -> paypulse/customer-service:dev and paypulse/api-gateway:dev (arm64)
docker compose --profile infra --profile apps up -d  -> the 2 services run in containers
curl -i localhost:8080/api/v1/customers              -> 200 through the gateway, with X-Correlation-Id
IntelliJ "Remote JVM Debug" on localhost:5005        -> "Connected to the target VM"
curl localhost:8080/api/v1/customers/<id>            -> IntelliJ stops on your breakpoint INSIDE the container (Mode A)
stop the container, run the service from IntelliJ    -> the same curl stops on the same breakpoint (Mode B)
```

**What is NOT in this ticket:** debugging in Kubernetes (mirrord, L7), debugging Kafka consumers, hot reload tuning,
healthchecks and JVM memory tuning for containers (L7), pushing images to a registry (L7/L8).

Time: about 75 to 90 minutes (the card said 45; Jib + compose + two modes need more). Standards touched: S8.2 (no
secrets in git), S8.4 (least privilege: the debug port is reachable from your machine only), S7.4.

---

## What I verified, and what I did not
I tested these in a throwaway copy of the repo (all cleaned up afterwards), on your Mac, 2026-10-26:
1. `jib:dockerBuild` run from the command line fails on the library module `common-web` ("Main class was not found")
   unless that module sets `jib.skip=true`.
2. **Apple Silicon trap, confirmed:** with no platform set, Jib built an **amd64** image on your arm64 Mac. With the
   platform set to `arm64`, it built an `arm64` image.
3. The arm64 image starts in the compose network, reaches Postgres at `postgres:5432`, answers `/actuator/health`
   with `UP`, prints `Listening for transport dt_socket at address: 5005` when `JAVA_TOOL_OPTIONS` carries the JDWP
   flag, and a real debugger (`jdb`) attached to it without crashing it.
4. Jib 3.5.2 was the latest release on Maven Central. It is not managed by the Spring Boot BOM, so the version must be
   written down once in the root pom.

**Not verified** (the sandbox blocked my test of it): a container calling a service that runs on your Mac through
`host.docker.internal` (needed in Mode B). It is a documented Docker Desktop feature and *most likely* works; step C3
checks it for real. The IntelliJ clicks are also from knowledge of the UI, not from my screen: if a menu differs, trust
what you see and tell me.

---

## Base state
- `main` is green; PRs #1 to #6 are merged. Customer-service on 8082, gateway on 8080 (both only run from Maven so far).
- `infra/compose` has only infrastructure; there are no Dockerfiles and no Jib config yet.
- Java 21 active: `mvn -v | sed -n 3p` must say 21. IntelliJ project SDK 21.
- Infra up: `cd infra/compose && docker compose --profile infra up -d`
- Branch: `git checkout main && git pull origin main && git checkout -b l0-6-debug-modes`

---

## PART A: build images with Jib and run them in Docker

### A1. Pin the Jib version (root `pom.xml`)
- **Why:** the Boot BOM manages Spring libraries, not every Maven plugin. A plugin with no version floats, so a build
  next month could differ. One place, the root, in `<pluginManagement>` (S: versions are decided once at the root).
- **Write:** in `<build><pluginManagement><plugins>` add `com.google.cloud.tools:jib-maven-plugin` with version `3.5.2`
  (comment: not managed by the Boot BOM; version checked on Maven Central 2026-10-26).
- **Check:** `mvn -q -N validate` (root only) prints nothing.

### A2. Skip Jib in the library (`platform/common-web/pom.xml`)
- **Why:** `common-web` is a library (no `main` method). Running `jib:dockerBuild` over the whole project would fail on it.
- **Write:** a `<properties>` block with `<jib.skip>true</jib.skip>` and a comment saying why.
- **Check:** done together with A3.

### A3. Jib config in `customer-service` and `api-gateway`
- **Why:** Jib needs to know the image name, the CPU architecture and the ports. Without the architecture it builds for
  **amd64**, which on your Apple Silicon Mac runs emulated (slow) or may fail (the trap I confirmed).
- **Write** in each service pom, in `<build><plugins>`, the `jib-maven-plugin` (no version, the root has it) with:
  - `from.platforms`: one platform, `architecture arm64`, `os linux`. Comment: on an Intel/AMD CI runner this must
    change (later we make it multi-arch).
  - `to.image`: `paypulse/customer-service:dev` and `paypulse/api-gateway:dev`
  - `container.ports`: customer-service `8082` and `5005`; gateway `8080` and `5005`
  - Do NOT put the JDWP flag in the image: debug is turned on by an environment variable at run time, so the same
    image can run without it (S8.4).
- **Check:**
  ```
  mvn -q package jib:dockerBuild -DskipTests
  docker image inspect paypulse/customer-service:dev --format '{{.Architecture}}'   # arm64
  docker image inspect paypulse/api-gateway:dev --format '{{.Architecture}}'       # arm64
  ```
  (`-DskipTests` only to save time; you run the full `mvn -q verify` before the PR.)
- **Mutation check:** remove the `from` block from one service, rebuild, see `amd64`, put it back.

### A4. The `apps` profile in `infra/compose/docker-compose.yml`
- **Why:** services in containers need the same kind of definition as the infra, in their own profile so you can start
  infra alone for Mode B.
- **Write** two services with `profiles: ["apps"]`:
  - `customer-service`: `image: paypulse/customer-service:dev`; `environment`: `CUSTOMER_DB_URL: jdbc:postgresql://postgres:5432/customer`
    (inside the network the database is called `postgres`, not `localhost`), `CUSTOMER_DB_USER: ${POSTGRES_USER}`,
    `CUSTOMER_DB_PASSWORD: ${POSTGRES_PASSWORD}`, `JAVA_TOOL_OPTIONS: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`;
    `ports`: `"127.0.0.1:8082:8082"` and `"127.0.0.1:5005:5005"`; `depends_on: postgres: condition: service_healthy`;
    `mem_limit: 768m` (start here; if it restarts with `OOMKilled`, raise it).
  - `api-gateway`: `image: paypulse/api-gateway:dev`; `CUSTOMER_SERVICE_URL: ${CUSTOMER_SERVICE_URL:-http://customer-service:8082}`
    (the `:-` default lets Mode B override it from your shell); `JAVA_TOOL_OPTIONS` as above; ports
    `"127.0.0.1:8080:8080"` and `"127.0.0.1:5006:5005"` (container port 5005, host port 5006 so it does not clash); `mem_limit: 768m`.
  - Comment every non-obvious key. No healthcheck on the apps yet (the image has no `curl`; probes come in L7).
- **Why `127.0.0.1:` in front of the ports:** the debug port lets anyone who can reach it run code in your JVM. Binding to
  localhost means only your Mac can connect, not other devices on your Wi-Fi (S8.4).
- **Check:**
  ```
  cd infra/compose && docker compose config -q && echo valid
  docker compose --profile infra --profile apps up -d
  curl -i localhost:8080/api/v1/customers      # 200 and a JSON array, with X-Correlation-Id
  docker logs paypulse-customer-service-1 2>&1 | grep "Listening for transport"
  ```

### A5. README
- **Write:** add 8082, 5005 and 5006 to the ports table and a 5-line "Debugging" section pointing to this guide.

---

## PART B: Mode A, attach IntelliJ to the container

### B1. Create the Remote JVM Debug configuration
IntelliJ: **Run → Edit Configurations → + → Remote JVM Debug**. Name `customer-service (docker)`, Host `localhost`,
Port `5005`, "Use module classpath" = `customer-service`. Apply.
- **Why "use module classpath":** the debugger maps lines in the running bytecode back to your source files.

### B2. Hit the breakpoint inside the container
1. Open `CustomerController` and click in the gutter next to the first line of `get(UUID id)`.
2. Start the config with the **bug icon**. The console says **Connected to the target VM**.
3. Create a customer through the gateway and read its id from the `Location` header:
   ```
   curl -i -X POST localhost:8080/api/v1/customers -H 'Content-Type: application/json' \
     -d '{"tenantId":"11111111-1111-1111-1111-111111111111","name":"Debug","email":"debug@example.com"}'
   curl -i localhost:8080/api/v1/customers/<id>
   ```
4. IntelliJ stops on the breakpoint. **While it is paused, `curl` hangs: that is the point.** Look at `id` in the
   Variables panel, step over (F8), then resume (F9). Achievement: *First breakpoint inside a container*.
- **If IntelliJ will not connect**, prove the container side is right with `jdb -attach localhost:5005` and tell me what it prints.

### B3. `suspend=n` versus `suspend=y`
Change the compose value to `suspend=y`, run `docker compose --profile infra --profile apps up -d --force-recreate customer-service`, and watch
`docker logs`: the app **does not start** until a debugger attaches. Attach IntelliJ, it continues. Use `suspend=y` only for bugs
that happen during startup. Put `suspend=n` back.

### B4. The trap: a stale image
Edit a line in `get`, do NOT rebuild, debug again: the breakpoint turns grey or lands on the wrong line, because the container is
still running the **old** jar. The loop after every code change in Mode A is:
```
mvn -q package jib:dockerBuild -DskipTests -pl services/customer-service -am
docker compose --profile infra --profile apps up -d --force-recreate customer-service
```

---

## PART C: Mode B, the daily workflow

### C1. Free the ports
Always pass **both** profiles: the apps depend on `postgres`, which lives in `infra`; with `--profile apps` alone Compose says "depends on undefined service postgres".
`docker compose --profile infra --profile apps stop customer-service` (otherwise 8082 and 5005 clash with IntelliJ).

### C2. Run the service from IntelliJ
Create a **Spring Boot** run configuration for `CustomerServiceApplication`, JDK 21, and add the environment variables
`CUSTOMER_DB_USER` and `CUSTOMER_DB_PASSWORD` with the values from your git-ignored `infra/compose/.env`
(`POSTGRES_USER`, `POSTGRES_PASSWORD`). IntelliJ stores them in `.idea/`, which is git-ignored: never commit them.
The database URL default (`localhost:5432/customer`) already works because Postgres publishes its port.
Start it with the **bug icon**.

### C3. Point the gateway at your Mac
The gateway container's `localhost` is itself, so it cannot see your IntelliJ service. Docker Desktop provides the name
`host.docker.internal` for "the Mac". Recreate the gateway with the override:
```
CUSTOMER_SERVICE_URL=http://host.docker.internal:8082 docker compose --profile infra --profile apps up -d --force-recreate api-gateway
curl -i localhost:8080/api/v1/customers/<id>
```
Expected: the **same breakpoint** stops, this time in the IntelliJ-run process. *(Most likely works on Docker Desktop, not verified
by me; if the gateway answers 502/503, tell me the output.)*

### C4. When to use which
| | Mode B (IntelliJ runs it) | Mode A (attach to container) |
|---|---|---|
| Speed of change | seconds (hot reload) | rebuild image + recreate |
| Same as production? | no (your JDK, your env) | yes (same image) |
| Use for | writing and daily debugging | "works on my machine but not in Docker", config and env bugs |

---

## PART D: finish
- Reset: `docker compose --profile infra --profile apps down` and `unset CUSTOMER_SERVICE_URL`.
- Definition of Done (S14): `mvn -q verify` green from the root; `docker compose config -q` valid; no `.env` staged
  (`git status`); no TODOs; comments on every new compose key and Jib option.
- Commits: `build: pin jib and build arm64 images` , `infra: apps profile with debug ports on localhost`, `docs: debugging guide in README`.
- `git push -u origin l0-6-debug-modes`, open the PR, read your own diff first.
- Quiz: ask Claude "quiz me on L0-6", then tick the Trello checklist and move the card. Add the achievement to `xp-log.md`.

---

## Questions and answers (explain-back)

**Q1. Why Jib and not a Dockerfile?**
*Remember:* Jib builds the image straight from Maven, layer by layer, no Dockerfile, no Docker daemon needed to build.
*Use case:* `mvn package jib:dockerBuild` puts dependencies, resources and your classes in separate layers. Change one controller
and only the small "classes" layer is rebuilt and re-pushed, not the 500 MB of libraries. A Dockerfile that copies one fat jar
rebuilds everything every time, and every team member would have to maintain a Dockerfile per service.
*Say it:* "Jib builds optimized, layered images directly from the build, with no Dockerfile to maintain. Dependencies are cached in their own layers, so rebuilds and pushes are fast and reproducible."

**Q2. What is JDWP and what do `server=y,suspend=n,address=*:5005` mean?**
*Remember:* JDWP is the debugging wire. The JVM is the server, IntelliJ is the client.
*Use case:* `JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` makes the JVM open TCP port 5005 and wait for a debugger (`server=y`), start normally without waiting (`suspend=n`), and listen on all network interfaces inside the container (`address=*`). IntelliJ's Remote JVM Debug connects to it from outside.
*Say it:* "JDWP is the Java Debug Wire Protocol. With server=y the JVM listens on a port, and an IDE attaches to inspect and pause it, even inside a container."

**Q3. When do you use `suspend=y`?**
*Remember:* y = wait for me before starting; n = start now.
*Use case:* a bug in a Flyway migration or a bean that fails while the app is *starting*: with `suspend=n` the app is already past that code before you attach. With `suspend=y` the JVM stops before `main`, you attach, set the breakpoint, then continue.
*Say it:* "suspend=y freezes the JVM at startup until a debugger attaches, so I can debug startup problems. I use suspend=n normally so containers start by themselves."

**Q4. Why is an open debug port dangerous, and what did we do about it?**
*Remember:* a debug port is a remote-code-execution door.
*Use case:* anyone who reaches port 5005 can attach and run code inside your service, read secrets from memory, even pause it. On a café Wi-Fi, a port published on all interfaces is reachable by other people. We publish it as `127.0.0.1:5005:5005`, so only your Mac can connect, and we enable the flag through an environment variable, never baked into the image, so a production image has no debug port.
*Say it:* "A debug port gives full control of the JVM, so I only publish it on localhost in dev, enable it by an environment variable, and never ship it in production images."

**Q5. Why did we have to set `arm64`?**
*Remember:* Jib's default is amd64, your Mac is arm64.
*Use case:* I built the same service twice: with no platform Jib produced an `amd64` image; with `arm64` it produced `arm64`. An amd64 image on Apple Silicon runs through emulation (slower, sometimes crashes). On an Intel CI runner the opposite is needed, which is why CI will later build for both.
*Say it:* "Images are built for a CPU architecture. Jib defaults to amd64, so on Apple Silicon I set arm64 explicitly; in CI I'd build multi-arch images."

**Q6. Why does the container connect to `postgres:5432` and not `localhost:5432`?**
*Remember:* inside a container, `localhost` is the container itself.
*Use case:* `CUSTOMER_DB_URL` in the app profile is `jdbc:postgresql://postgres:5432/customer`. Compose gives every service a DNS name equal to its service name on a private network. Mode B (on your Mac) uses `localhost:5432` because Postgres publishes that port to the Mac. Same app, different address, chosen by an environment variable.
*Say it:* "Each container has its own localhost. Containers reach each other by service name on the compose network, so the database address comes from an environment variable that differs per mode."

**Q7. Mode A or Mode B, when?**
*Remember:* B to write code, A to reproduce Docker-only problems.
*Use case:* you are building the invoice endpoint: Mode B, change a line, restart in seconds. A bug appears only in Docker (a wrong env var, a missing file in the image): Mode A, because you debug the exact image that runs.
*Say it:* "Day to day I run one service in the IDE for speed. When a problem only shows up in the container I attach a remote debugger to the real image."

**Q8. Why `host.docker.internal` in Mode B?**
*Remember:* it is the container's name for "the Mac".
*Use case:* the gateway runs in Docker but customer-service runs in IntelliJ on your Mac. The gateway's `localhost` would be itself. `http://host.docker.internal:8082` is the address Docker Desktop provides for the host machine, so one env var override reroutes the gateway to your IDE.
*Say it:* "From a container, host.docker.internal reaches services on my laptop, so I can run one service locally and the rest in Docker."

**Q9. Why `jib.skip` in `common-web`?**
*Remember:* a library has no main class, so it has no image.
*Use case:* running `jib:dockerBuild` over the whole project made Maven stop at `common-web` with "Main class was not found". The skip flag says "this module never becomes an image", so one command builds the images for all services.
*Say it:* "Jib builds images for runnable modules. Shared libraries are marked jib.skip so a project-wide build only produces service images."
