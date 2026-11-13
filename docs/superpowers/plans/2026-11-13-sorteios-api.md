# Sorteios API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot REST API for raffles with auditable commit-reveal draws, concurrency-safe ticket purchase, JWT auth and a self-healing public Swagger demo.

**Architecture:** Single Spring Boot 4 app, packages by feature (`auth`, `raffle`, `ticket`, `draw`, `demo`, `common`). PostgreSQL via JPA + Flyway. Stateless JWT (OAuth2 resource server, HS256). Integration tests with MockMvc against an embedded real PostgreSQL 17.

**Tech Stack:** Java 21, Spring Boot 4.1.2 (webmvc, data-jpa, security, oauth2-resource-server, validation, flyway, actuator), springdoc-openapi-starter-webmvc-ui 3.2.0, PostgreSQL 17, io.zonky.test:embedded-postgres 2.2.2 + binaries BOM 17.11.0 (test), JUnit 5, Maven Wrapper.

**Spec:** `docs/superpowers/specs/2026-11-13-sorteios-api-design.md`

## Global Constraints

- Repo: GitHub `lucasaccioly/sorteios-api` (public, MIT, `© Lucas Accioly`).
- Shell setup before any Maven command (PowerShell): `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"; $env:Path="$env:JAVA_HOME\bin;$env:Path"`. Run Maven as `.\mvnw.cmd -q -B ...`, redirect output to a log file and read its tail.
- Verified-compatible versions (spike on 2026-11-13): Boot **4.1.2**, springdoc **3.2.0**, embedded-postgres **2.2.2**, binaries BOM **17.11.0**. Do not bump.
- Base package `com.lucasaccioly.sorteios`. Code, identifiers and commits in English; API messages (ProblemDetail `detail`, validation messages) and README in pt-BR.
- No Lombok. DTOs are `record`s. Entities have explicit getters/setters.
- All IDs are `UUID` (generated in Java with `UUID.randomUUID()`).
- Money in integer cents (`long ticketPriceCents`). Instants stored as `timestamptz`, exposed as ISO-8601 UTC (`Instant`).
- Time always from an injected `java.time.Clock` bean (tests replace it with a mutable test clock).
- Errors are RFC 9457 `ProblemDetail` (`application/problem+json`). Codes: 400 validation/malformed input, 401 missing/invalid token, 404 not found **or not visible to caller**, 409 every business-rule rejection (state, number taken, limits, own raffle).
- Spec values pinned here: N numbers 10..10000; max 50 numbers per purchase request; JWT expires in 24 h; password ≥ 8; email stored lowercase and unique; page size max 100; draw scheduler every 60 s; demo maintenance every 10 min, ensures OPEN demo raffle with ≥ 10 free numbers and `drawAt` ≥ now + 1 h, else creates one with `drawAt` = now + 24 h; seed = 32 bytes from `SecureRandom`.
- Commit-reveal algorithm (exact, public contract — README script must match):
  - `seedHash = hex(SHA-256(seedBytes))`; seed revealed as `hex(seedBytes)`; hex is lowercase.
  - `ticketsLine = join(",", "<number>:<userId>" for tickets ordered by number asc)`.
  - `ticketsDigest = hex(SHA-256(utf8(ticketsLine)))`.
  - `h = SHA-256(seedBytes ‖ utf8(ticketsDigest))`; `x = unsigned 64-bit big-endian of h[0..8)`; `winnerIndex = Long.remainderUnsigned(x, soldCount)`; winner = ticket at `winnerIndex` in the number-ordered list.
- Commit after each task with the message given; end every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. Purchase after `drawAt` when the scheduler has not run yet → must be rejected (409 "Vendas encerradas") and must not slip a ticket in before the draw. (Task 4 test)
2. `quantity` larger than the free numbers left → 409, nothing saved. (Task 4 test)
3. Duplicate or out-of-range numbers in one request (`[7,7]`, `0`, `N+1`) → 400, nothing saved. (Task 4 test)
4. Register with the same email in a different case (`Ana@x.com` vs `ana@x.com`) → 409. (Task 2 test)
5. Malformed or expired JWT → 401 ProblemDetail, never 500. (Task 2 test)

---

### Task 1: Project scaffold, test base, error handling, CI

**Files:**
- Create: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/*` (from start.spring.io: bootVersion 4.1.2, java 21, deps `web,data-jpa,security,oauth2-resource-server,validation,flyway,postgresql,actuator`; add springdoc 3.2.0, embedded-postgres 2.2.2 test, binaries BOM 17.11.0 in `dependencyManagement`)
- Create: `.gitignore` (target/, .idea/, *.iml, .env, *.log), `.gitattributes` (`* text=auto eol=lf`, `*.cmd eol=crlf`), `LICENSE` (MIT)
- Create: `src/main/java/com/lucasaccioly/sorteios/SorteiosApplication.java` (`@EnableScheduling`)
- Create: `src/main/java/com/lucasaccioly/sorteios/common/ClockConfig.java`, `common/ApiExceptionHandler.java`, `common/BusinessException.java`, `common/NotFoundException.java`
- Create: `src/main/java/com/lucasaccioly/sorteios/auth/SecurityConfig.java` (temporary: permit all — replaced in Task 2)
- Create: `src/main/resources/application.yml`, `.github/workflows/ci.yml` (no migrations yet — the first one, `V1__users.sql`, comes in Task 2)
- Test: `src/test/java/com/lucasaccioly/sorteios/support/IntegrationTest.java`, `support/MutableClock.java`, `common/ApiExceptionHandlerIT.java`

**Interfaces:**
- Produces: abstract `IntegrationTest` (annotated `@SpringBootTest @AutoConfigureMockMvc`), with `protected MockMvc mvc`, `protected JdbcTemplate jdbc`, `protected MutableClock clock`, `protected ObjectMapper json`; a single JVM-wide `EmbeddedPostgres` registered through `@DynamicPropertySource` (`spring.datasource.url`); `@BeforeEach` truncates every table in schema `public` except `flyway_schema_history` (`TRUNCATE ... RESTART IDENTITY CASCADE`, built from `information_schema.tables`) and resets the clock to `2026-01-01T12:00:00Z`.
- Produces: `MutableClock extends Clock` with `set(Instant)` and `advance(Duration)`; registered as `@Primary` `Clock` in a test `@TestConfiguration` imported by `IntegrationTest`.
- Produces: `BusinessException(String detail)` → 409; `BusinessException(String detail, Map<String,Object> properties)` → 409 with extra ProblemDetail properties; `NotFoundException()` → 404 `"Recurso não encontrado"`.
- Produces: `ApiExceptionHandler extends ResponseEntityExceptionHandler` (`@RestControllerAdvice`): `MethodArgumentNotValidException` → 400 with property `errors` = `{field: message}`; `HttpMessageNotReadableException` → 400 `"Corpo da requisição inválido"`; `ConstraintViolationException`/`MethodArgumentTypeMismatchException` → 400; `BusinessException` → 409; `NotFoundException` → 404.
- `application.yml`: `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, datasource from env `DATABASE_URL`/`DATABASE_USER`/`DATABASE_PASSWORD` with local defaults `jdbc:postgresql://localhost:5432/sorteios`/`postgres`/`postgres`, `app.jwt.secret: ${JWT_SECRET:dev-only-secret-change-me-dev-only-secret-0123456789}`, `springdoc.swagger-ui.path=/swagger-ui.html`, `management.endpoints.web.exposure.include=health`.

- [ ] **Step 1:** Generate the project into the repo root (keep `docs/`), `git init -b main`, add the files above. `IntegrationTest` + `MutableClock` as specified.
- [ ] **Step 2: Write the failing test** `ApiExceptionHandlerIT` (extends `IntegrationTest`) using a test-only controller (`@RestController` in the test package, `@Import`ed) with endpoints that throw each exception and one `@Valid` record body:
  - `businessException_returns409ProblemDetail`: status 409, content type `application/problem+json`, `$.detail` equals the message, extra property present.
  - `notFound_returns404`: `$.detail == "Recurso não encontrado"`.
  - `invalidBody_returns400WithFieldErrors`: `$.errors.name` exists.
  - `malformedJson_returns400`.
  - `health_isUp`: `GET /actuator/health` → 200 `$.status == "UP"`.
- [ ] **Step 3:** Run `.\mvnw.cmd -q -B test -Dtest=ApiExceptionHandlerIT`. Expected: FAIL (handler missing).
- [ ] **Step 4:** Implement `ClockConfig` (`Clock.systemUTC()`), exceptions and `ApiExceptionHandler`.
- [ ] **Step 5:** Run the same command. Expected: PASS.
- [ ] **Step 6:** `ci.yml`: on push/PR, `ubuntu-latest`, `actions/checkout@v5`, `actions/setup-java@v5` (temurin 21, cache maven), `./mvnw -B verify`. (Embedded Postgres runs on Linux; no service container.)
- [ ] **Step 7: Commit** `chore: scaffold Spring Boot 4 project with test base and ProblemDetail errors`

### Task 2: Users, register/login, JWT security, admin bootstrap

**Files:**
- Create: `src/main/resources/db/migration/V1__users.sql`
- Create: `auth/User.java`, `auth/Role.java`, `auth/UserRepository.java`, `auth/AuthController.java`, `auth/AuthService.java`, `auth/JwtService.java`, `auth/AdminBootstrap.java`, `auth/CurrentUser.java`
- Modify: `auth/SecurityConfig.java`
- Test: `auth/AuthIT.java`, `auth/AdminBootstrapIT.java`; add helper to `support/IntegrationTest.java`

**Interfaces:**
- Consumes: `IntegrationTest`, `BusinessException`, `Clock`.
- Produces: table `users(id uuid pk, name varchar(100) not null, email varchar(254) not null unique, password_hash varchar(100) not null, role varchar(10) not null, created_at timestamptz not null)`.
- Produces: `enum Role { USER, ADMIN }`; entity `User` (`getId(): UUID`, `getName()`, `getEmail()`, `getRole()`).
- Produces: `CurrentUser.id(Jwt jwt): UUID` (subject = user id) — controllers take `@AuthenticationPrincipal Jwt jwt`.
- Produces: `JwtService.issue(User): String` — HS256 via `NimbusJwtEncoder` over `SecretKeySpec(secret UTF-8, "HmacSHA256")`, claims `sub`=id, `name`, `roles`=[role], `iat`, `exp`=+24 h from `Clock`. Decoder: `NimbusJwtDecoder.withSecretKey(...)` with a `JwtTimestampValidator` using the same `Clock`; authorities from claim `roles` with prefix `ROLE_`.
- Produces endpoints: `POST /api/auth/register {name,email,password}` → 201 `{token, user:{id,name,email,role}}`; `POST /api/auth/login {email,password}` → 200 same shape, wrong credentials → 401 `"E-mail ou senha inválidos"`; `GET /api/me` → 200 user.
- Produces: `SecurityConfig` final rules: stateless, CSRF off, `permitAll` for `POST /api/auth/**`, `GET /api/raffles`, `GET /api/raffles/*`, `GET /api/raffles/*/numbers`, `GET /api/raffles/*/proof`, `/`, `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`; everything else authenticated. 401 and 403 bodies are ProblemDetail (custom `AuthenticationEntryPoint`/`AccessDeniedHandler`). `@EnableMethodSecurity`.
- Produces test helpers: `record TestUser(UUID id, String token)`; `IntegrationTest.register(String name, String email): TestUser` (password `"senha12345"`, calls the real endpoint) and `bearer(TestUser): String` (`"Bearer " + token`).
- Produces: `AdminBootstrap` (`ApplicationRunner`): if `ADMIN_EMAIL` and `ADMIN_PASSWORD` (properties `app.admin.email`/`app.admin.password`) are set, create the user with role ADMIN or promote the existing one; idempotent.

- [ ] **Step 1: Write the failing tests** `AuthIT`:
  - `register_returnsTokenAndUser` (201, role `USER`, email lowercased).
  - `register_duplicateEmailIgnoringCase_returns409` — `Ana@x.com` then `ana@x.com`.
  - `register_invalidInput_returns400` — blank name, bad email, password `"1234567"`.
  - `login_ok_and_wrongPassword_returns401`.
  - `me_withToken_returnsUser`; `me_withoutToken_returns401ProblemDetail`.
  - `me_withMalformedToken_returns401` (`Bearer abc`); `me_withExpiredToken_returns401` (issue, `clock.advance(Duration.ofHours(25))`).
  - `AdminBootstrapIT` (`@TestPropertySource(properties={"app.admin.email=admin@x.com","app.admin.password=admin12345"})`): admin can log in and `/api/me` role is `ADMIN`; running the runner twice keeps one row.
- [ ] **Step 2:** Run `.\mvnw.cmd -q -B test -Dtest=AuthIT,AdminBootstrapIT`. Expected: FAIL.
- [ ] **Step 3:** Implement migration, entity, repository (`findByEmail`, `existsByEmail`), services, controller, security config, bootstrap. Passwords with `BCryptPasswordEncoder`.
- [ ] **Step 4:** Run the same command. Expected: PASS. Then full `.\mvnw.cmd -q -B test`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: add JWT authentication, registration and admin bootstrap`

### Task 3: Raffles — CRUD, lifecycle, seed commitment

**Files:**
- Create: `src/main/resources/db/migration/V2__raffles.sql`
- Create: `raffle/Raffle.java`, `raffle/RaffleStatus.java`, `raffle/RaffleRepository.java`, `raffle/RaffleService.java`, `raffle/RaffleController.java`, `raffle/RaffleDtos.java` (records), `draw/Seeds.java`
- Test: `raffle/RaffleIT.java`, `draw/SeedsTest.java`

**Interfaces:**
- Consumes: `CurrentUser`, `register(...)` helper, `BusinessException`, `NotFoundException`, `Clock`.
- Produces: table `raffles(id uuid pk, organizer_id uuid not null fk users, title varchar(120) not null, description varchar(2000), prize varchar(200) not null, ticket_price_cents bigint not null check >= 0, total_numbers int not null check 10..10000, max_per_user int not null check >= 1, draw_at timestamptz not null, status varchar(20) not null, seed bytea, seed_hash char(64), tickets_digest char(64), winning_number int, winner_id uuid fk users, drawn_at timestamptz, created_at timestamptz not null, version bigint not null default 0)`; index on `(status, draw_at)`.
- Produces: `enum RaffleStatus { DRAFT, OPEN, DRAWN, CLOSED_NO_SALES, CANCELLED }`.
- Produces: `RaffleRepository.findByIdForUpdate(UUID): Optional<Raffle>` (`@Lock(PESSIMISTIC_WRITE)`), `findIdsDue(Instant now): List<UUID>` (status OPEN and drawAt ≤ now).
- Produces: `Seeds.newSeed(): byte[]` (32 bytes, `SecureRandom`), `Seeds.sha256Hex(byte[]): String`, `Seeds.hex(byte[])`.
- Produces: `RaffleService.getVisible(UUID raffleId, UUID callerOrNull): Raffle` (DRAFT visible only to organizer, else `NotFoundException`) — Task 5 hooks lazy draw into this method.
- Produces response record `RaffleResponse(id, title, description, prize, ticketPriceCents, totalNumbers, maxPerUser, drawAt, status, organizer{id,name}, soldCount, availableCount, seedHash, winningNumber, winnerName, drawnAt)` — `soldCount` from Task 4 (`TicketRepository.countByRaffleId`); until Task 4 exists, return 0 and replace in Task 4. `seedHash` null in DRAFT; winner fields null unless DRAWN; `winnerName` = first name only.
- Endpoints (spec table): `POST /api/raffles` (201, DRAFT; `drawAt` must be after now else 400), `PUT /api/raffles/{id}` (only organizer, only DRAFT else 409), `POST /api/raffles/{id}/publish` (organizer only, DRAFT → OPEN, requires `drawAt` after now else 409, generates seed + seedHash), `POST /api/raffles/{id}/cancel` (organizer or `ROLE_ADMIN`; allowed from DRAFT or from OPEN with zero sold tickets; otherwise 409 `"Não é possível cancelar uma rifa com números vendidos"`), `GET /api/raffles/{id}`, `GET /api/raffles?status=&q=&page=&size=&sort=` (never DRAFT; `q` = case-insensitive contains on title; default sort `drawAt,asc`; size > 100 → clamp to 100), `GET /api/raffles/mine` (organizer's raffles, all statuses, paged).
- Non-organizer acting on someone's raffle → 404 (not 403), except ADMIN cancel.

- [ ] **Step 1: Write the failing tests** `SeedsTest`: `newSeed` is 32 bytes and two calls differ; `sha256Hex("abc".getBytes(UTF_8)) == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"`.
- [ ] **Step 2: Write the failing tests** `RaffleIT`:
  - `create_returnsDraft_hiddenFromOthersAndPublicList`.
  - `create_invalid_returns400` (total 9 and 10001, negative price, `drawAt` in the past).
  - `update_onlyInDraft_andOnlyByOrganizer` (other user → 404; after publish → 409).
  - `publish_setsOpenAndSeedHash_neverExposesSeed` (response has 64-char `seedHash`; JSON has no `seed` field).
  - `publish_withPastDrawAt_returns409` (create, then `clock.advance` past drawAt, publish).
  - `cancel_draftOrOpenWithoutSales_ok`; `cancel_byOtherUser_returns404`; `cancel_byAdmin_ok` (admin promoted via SQL `update users set role='ADMIN'` and re-login).
  - `list_filtersByStatusAndQuery_paginates_neverDraft`; `mine_listsAllOwnStatuses`.
- [ ] **Step 3:** Run `.\mvnw.cmd -q -B test -Dtest=SeedsTest,RaffleIT`. Expected: FAIL.
- [ ] **Step 4:** Implement migration, entity (`@Version` on `version`), service, controller.
- [ ] **Step 5:** Run the same command, then full suite. Expected: PASS.
- [ ] **Step 6: Commit** `feat: add raffle lifecycle with seed commitment`

### Task 4: Tickets — purchase under concurrency

**Files:**
- Create: `src/main/resources/db/migration/V3__tickets.sql`
- Create: `ticket/Ticket.java`, `ticket/TicketRepository.java`, `ticket/TicketService.java`, `ticket/TicketController.java`, `ticket/TicketDtos.java`
- Modify: `raffle/RaffleService.java` (real `soldCount`/`availableCount`; cancel-with-sales check)
- Test: `ticket/TicketIT.java`, `ticket/TicketConcurrencyIT.java`

**Interfaces:**
- Consumes: `RaffleRepository.findByIdForUpdate`, `RaffleStatus`, `register(...)`, `Clock`.
- Produces: table `tickets(id uuid pk, raffle_id uuid not null fk raffles, user_id uuid not null fk users, number int not null, purchased_at timestamptz not null, unique (raffle_id, number))`; index on `(raffle_id, user_id)`.
- Produces: `TicketRepository.countByRaffleId(UUID): long`, `countByRaffleIdAndUserId(UUID, UUID): long`, `findByRaffleIdOrderByNumberAsc(UUID): List<Ticket>`, `findNumbersByRaffleId(UUID): Set<Integer>`.
- Produces: `TicketService.purchase(UUID raffleId, UUID userId, PurchaseRequest req): List<Ticket>` — one transaction: lock raffle row (`findByIdForUpdate`); reject unless status OPEN and `clock.instant() < drawAt` (409 `"Vendas encerradas"`); reject organizer (409); exactly one of `numbers`/`quantity` (400 otherwise); `numbers` 1..50 items, distinct, each 1..totalNumbers (400); `quantity` 1..50; per-user limit (409 `"Limite de N números por participante"`); chosen numbers already sold → 409 with property `unavailableNumbers` (sorted); `quantity` > free → 409 `"Restam apenas K números"`; random pick uniformly among free numbers with `SecureRandom`. All-or-nothing.
- Endpoints: `POST /api/raffles/{id}/tickets` → 201 `{raffleId, numbers:[...]}`; `GET /api/raffles/{id}/numbers` → `{totalNumbers, sold:[sorted ints]}` (public, DRAFT → 404); `GET /api/tickets/mine` → list `{raffleId, raffleTitle, number, purchasedAt, raffleStatus, won}`.

- [ ] **Step 1: Write the failing tests** `TicketIT`:
  - `buyChosenNumbers_ok_andNumbersMapShowsThem`.
  - `buyQuantity_picksFreeDistinctNumbers`.
  - `numberAlreadySold_returns409WithUnavailableNumbers_andSavesNothing` (request `[3,4]` when 4 sold → 3 still free).
  - `duplicateOrOutOfRange_returns400_savesNothing` (`[7,7]`, `[0]`, `[N+1]`, 51 numbers, both/neither of numbers+quantity).
  - `quantityMoreThanFree_returns409_savesNothing`.
  - `perUserLimit_returns409`.
  - `organizerCannotBuy_returns409`; `draftOrCancelled_returns404or409` (DRAFT → 404, CANCELLED → 409).
  - `afterDrawAt_beforeScheduler_returns409` (`clock.set(drawAt)`).
  - `cancelWithSales_returns409` (even as ADMIN).
  - `mine_listsOwnTickets`.
- [ ] **Step 2: Write the failing test** `TicketConcurrencyIT.twentyBuyersSameNumber_exactlyOneWins`: 20 registered users, one OPEN raffle; 20 threads released together by a `CountDownLatch` call `ticketService.purchase(raffleId, users[i].id(), numbers=[7])`; count successes vs `BusinessException`. Expected: 1 success, 19 conflicts, `countByRaffleId == 1`. Second scenario `quantityBuyersNeverCollide`: total 10, 10 threads each `quantity=1` → 10 distinct numbers, no exception. Test class is not `@Transactional`.
- [ ] **Step 3:** Run `.\mvnw.cmd -q -B test -Dtest=TicketIT,TicketConcurrencyIT`. Expected: FAIL.
- [ ] **Step 4:** Implement. A `DataIntegrityViolationException` on the unique constraint must also map to the 409 path (defence in depth).
- [ ] **Step 5:** Run the same command, then full suite. Expected: PASS.
- [ ] **Step 6: Commit** `feat: add concurrency-safe ticket purchase`

### Task 5: Draw — commit-reveal, scheduler, catch-up, proof

**Files:**
- Create: `draw/DrawAlgorithm.java`, `draw/DrawService.java`, `draw/DrawScheduler.java`, `draw/ProofController.java`, `draw/ProofResponse.java`
- Modify: `raffle/RaffleService.getVisible` (call `drawService.drawIfDue(id)` first)
- Create: `scripts/verify_draw.py`
- Test: `draw/DrawAlgorithmTest.java`, `draw/DrawIT.java`

**Interfaces:**
- Consumes: `Seeds`, `RaffleRepository.findByIdForUpdate/findIdsDue`, `TicketRepository.findByRaffleIdOrderByNumberAsc`, `Clock`.
- Produces: `DrawAlgorithm.ticketsLine(List<Ticket>)`, `DrawAlgorithm.digest(String ticketsLine): String`, `DrawAlgorithm.winnerIndex(byte[] seed, String ticketsDigest, int soldCount): int` — exactly the Global Constraints formula.
- Produces: `DrawService.drawIfDue(UUID raffleId): void` — own transaction (`REQUIRES_NEW`), locks the raffle row, no-op unless OPEN and `drawAt <= now`; zero tickets → `CLOSED_NO_SALES`; else sets `ticketsDigest`, `winningNumber`, `winnerId`, `drawnAt`, status `DRAWN`. Calling twice never changes the result.
- Produces: `DrawService.drawAllDue(): int` (loops `findIdsDue`, catches/logs per raffle).
- Produces: `DrawScheduler` — `@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)` → `drawAllDue()`; `ApplicationRunner` → `drawAllDue()` on startup. Disabled in tests via property `app.scheduling.enabled=false` (`@ConditionalOnProperty`, default true; test base sets false).
- Produces: `GET /api/raffles/{id}/proof` → 200 `ProofResponse(raffleId, seedHash, seed, ticketsDigest, tickets:[{number,userId}], soldCount, winnerIndex, winningNumber, winnerName, algorithm)` only when DRAWN; otherwise 409 `"Prova disponível após o sorteio"`. `algorithm` is the pt-BR text of the formula.
- Produces: `scripts/verify_draw.py <proof-url-or-json-file>` (Python 3 stdlib only): checks `sha256(seed) == seedHash`, recomputes digest and winner, prints `OK` or the mismatch, exit code 0/1.

- [ ] **Step 1: Write the failing test** `DrawAlgorithmTest` with a fixed vector: seed = bytes `0x00..0x1f`, tickets `[(3,u1),(7,u2),(9,u3)]` with fixed UUIDs. Compute the expected `ticketsDigest` and `winnerIndex` once with an independent implementation (`python -c` using `hashlib`, same formula) and pin both values in the test. Also: `winnerIndex` is always in `[0, soldCount)` for 1000 random seeds.
- [ ] **Step 2: Write the failing tests** `DrawIT`:
  - `due_raffle_isDrawn_by_drawAllDue_and_proofVerifies` (proof recomputed in Java with `DrawAlgorithm` + `Seeds.sha256Hex(seed) == seedHash` + winner matches a sold ticket).
  - `noSales_becomesClosedNoSales`.
  - `drawIfDue_isIdempotent` (call twice, same winner).
  - `getRaffleAfterDrawAt_triggersLazyDraw` (no scheduler call; `GET /api/raffles/{id}` returns DRAWN).
  - `proof_beforeDraw_returns409`; `seed_neverInRaffleResponse`.
  - `ticketOwnerChangedAfterDraw_proofNoLongerVerifies` (SQL update of `tickets.user_id`, recompute digest ≠ stored).
- [ ] **Step 3:** Run `.\mvnw.cmd -q -B test -Dtest=DrawAlgorithmTest,DrawIT`. Expected: FAIL.
- [ ] **Step 4:** Implement; write `verify_draw.py`.
- [ ] **Step 5:** Run the same command, then full suite. Expected: PASS. Also run `python scripts/verify_draw.py` against a proof JSON saved from a test run (write it from `DrawIT` to `target/proof-sample.json`). Expected: `OK`.
- [ ] **Step 6: Commit** `feat: add auditable commit-reveal draw with scheduler and proof endpoint`

### Task 6: Demo profile, OpenAPI polish

**Files:**
- Create: `demo/DemoSeeder.java`, `demo/DemoMaintenance.java`, `common/OpenApiConfig.java`, `common/RootRedirectController.java`
- Create: `src/main/resources/application-demo.yml`
- Test: `demo/DemoIT.java`, `common/OpenApiIT.java`

**Interfaces:**
- Consumes: `UserRepository`, `RaffleService` (create/publish), `TicketService.purchase`, `DrawService.drawIfDue`, `Clock`.
- Produces: `DemoSeeder` (`@Profile("demo")`, `ApplicationRunner`, idempotent): users `organizador@demo.dev` ("Organizadora Demo") and `participante@demo.dev` ("Participante Demo"), password `demo12345`; if the organizer has no raffles: one DRAWN raffle with ~15 sold tickets and a verifiable proof (create with `drawAt` ahead, buy as 3 helper users `bot1..3@demo.dev`, then set `drawAt` to the past via repository and call `drawIfDue`), one CLOSED_NO_SALES, one CANCELLED, one DRAFT; then `DemoMaintenance.ensureOpenRaffle()`.
- Produces: `DemoMaintenance.ensureOpenRaffle(): boolean` — `@Scheduled(fixedDelay = 600_000)` (demo profile + scheduling enabled); creates & publishes a new OPEN raffle (100 numbers, max 10 per user, `drawAt` = now + 24 h, rotating prize names) unless one exists with ≥ 10 free numbers and `drawAt` ≥ now + 1 h; returns whether it created one.
- Produces: `OpenApiConfig` — info title "Sorteios API", pt-BR description with demo credentials and "primeiro acesso pode levar ~1 min", bearer-JWT security scheme applied globally; `GET /` → 302 `/swagger-ui.html`.

- [ ] **Step 1: Write the failing tests** `DemoIT` (`@ActiveProfiles("demo")`): after context start, both demo users log in; there is ≥ 1 OPEN raffle with ≥ 10 free numbers; the DRAWN demo raffle's proof verifies; running the seeder again creates no duplicates; after buying out the OPEN raffle (or `clock.advance(23h)`), `ensureOpenRaffle()` returns true and a new OPEN raffle exists; calling it again returns false.
- [ ] **Step 2: Write the failing tests** `OpenApiIT`: `/v3/api-docs` has `components.securitySchemes.bearerAuth` and `info.title == "Sorteios API"`; `GET /` → 302 to `/swagger-ui.html`; `/swagger-ui.html` reachable without auth (2xx/3xx).
- [ ] **Step 3:** Run `.\mvnw.cmd -q -B test -Dtest=DemoIT,OpenApiIT`. Expected: FAIL.
- [ ] **Step 4:** Implement. Add `@Operation`/`@Tag` summaries in pt-BR to every controller endpoint.
- [ ] **Step 5:** Run the same command, then full suite. Expected: PASS.
- [ ] **Step 6: Commit** `feat: add self-healing demo profile and OpenAPI docs`

### Task 7: Docker, Render blueprint, README, publish

**Files:**
- Create: `Dockerfile` (stage 1 `eclipse-temurin:21-jdk` → `./mvnw -B -DskipTests package`; stage 2 `eclipse-temurin:21-jre`, non-root user, `ENTRYPOINT java -XX:MaxRAMPercentage=75 -jar app.jar`, `EXPOSE 8080`, honour `PORT` via `server.port=${PORT:8080}` in `application.yml`)
- Create: `.dockerignore`, `render.yaml` (web service, runtime docker, plan free, healthCheckPath `/actuator/health`, env vars `SPRING_PROFILES_ACTIVE=demo`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `JWT_SECRET` with `sync: false`)
- Create: `docker-compose.yml` (postgres:17 + app) — documented as optional, not verified locally (no Docker on this machine)
- Create: `README.md` (pt-BR)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1:** Write `Dockerfile`, `.dockerignore`, `render.yaml`, `docker-compose.yml`.
- [ ] **Step 2:** Local smoke run without Docker: start the app with profile `demo` against the local embedded Postgres used by FinTrack's dev script **or** a fresh `EmbeddedPostgres` launched by a small scratch script; `curl` `/actuator/health` (UP), `/v3/api-docs` (200), register → buy → `/api/tickets/mine`. Expected: all 2xx.
- [ ] **Step 3:** README sections: title + one-line pitch, badges (CI, Java 21, Spring Boot 4, PostgreSQL), **demo link + demo credentials + cold-start warning**, "Como funciona o sorteio auditável" (mermaid state diagram + formula + `verify_draw.py` usage), "Decisões técnicas" (pessimistic lock + unique constraint, commit-reveal and its stated limitation, ProblemDetail, 404 vs 403, Clock injection, embedded Postgres tests), endpoint table, "Rodando localmente", "Testes" (`./mvnw verify`, concurrency test highlight), license.
- [ ] **Step 4:** Full suite `.\mvnw.cmd -q -B verify`. Expected: PASS.
- [ ] **Step 5: Commit** `docs: add README, Dockerfile and Render blueprint`; create GitHub repo `lucasaccioly/sorteios-api` (public, description in pt-BR, topics `java spring-boot postgresql jwt rest-api`) and push `main`. Confirm CI green with `gh run list`.

### Task 8: Deploy (Neon + Render) and profile update

Operational, done with the user in the browser; no TDD.

- [ ] **Step 1:** Neon: create database `sorteios` (new project, free, region close to Render's — Render free region Oregon or Virginia; pick Virginia/`us-east` to match Neon `aws-us-east-1`). Build the JDBC URL `jdbc:postgresql://<host>/<db>?sslmode=require` (unpooled host — Flyway needs session features). User copies the connection values into Render; Claude never types secrets.
- [ ] **Step 2:** Render: New → Blueprint (or Web Service from repo) → `lucasaccioly/sorteios-api`, free plan, region Virginia. User pastes `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and a fresh `JWT_SECRET` (generated with the clipboard command). Deploy.
- [ ] **Step 3:** Verify live: `/actuator/health` UP; Swagger loads; login as `participante@demo.dev`, buy a number in the OPEN raffle; demo DRAWN proof passes `python scripts/verify_draw.py https://<app>/api/raffles/<id>/proof`.
- [ ] **Step 4:** Update `sorteios-api` README demo link and repo homepage; update profile README row "API de Sorteios" (repo + demo links, remove from 🚧 note); push both.
- [ ] **Step 5:** Ask the user whether to delete or archive `SistemaSorteio`, `SistemaSorteio1`, `SorteioProjeto`.
