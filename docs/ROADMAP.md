# Household Task Manager — Build Roadmap

> Status: **Execution plan** · Last updated: 2026-07-03
> Turns [`SPEC.md`](./SPEC.md) (what) and [`ARCHITECTURE.md`](./ARCHITECTURE.md) (how) into an ordered list of work.
> Each **epic** (§) is a general area of work; each **PR** under it is a self-contained, reviewable slice that ships a working increment **including its own tests**. Order is roughly dependency order — later epics assume earlier ones.

## How to read this

- **Epic** = a general task / theme. **PR** = one pull request: a vertical slice (migration + entities + service + GraphQL/UI + tests) that is independently reviewable and mergeable.
- **Every PR includes tests** — that is a definition-of-done, not an optional extra. The kind of test is called out per PR.
- Checkboxes track progress: `- [ ]` open, `- [x]` merged.

### Testing conventions (apply to every PR)

| Layer       | Tooling                                                    | What it covers                                                                                                  |
| ----------- | ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Unit        | JUnit 5 + AssertJ + Mockito                                | Service-layer business rules in isolation (carry-over, recurrence expansion, effort weighting, token rotation). |
| Integration | Spring Boot Test + **Testcontainers 2.x (Postgres)**       | GraphQL request → resolver → service → real DB. Uses `GraphQlTester`. Shared singleton container via `@ServiceConnection` (see `AbstractIntegrationTest`). |
| Security    | Spring Security Test                                       | Auth required/exempt, tenant isolation, role gates.                                                             |
| Migration   | Flyway `migrate` + `validate` in CI against Testcontainers | Every migration applies cleanly from empty and is validated.                                                    |
| Frontend    | Vitest + React Testing Library; MSW for GraphQL mocking    | Component behaviour + query/mutation wiring.                                                                    |

A PR is **not done** until: new code has tests at the appropriate layer, `./gradlew check` (or `npm test`) is green, and Flyway `validate` passes.

---

## 0. Project scaffolding & walking skeleton

**Goal:** one command brings up a running system; one GraphQL query renders in the browser end-to-end. Establishes the CI + test harness every later PR relies on.

- [x] **PR 0.1 — Ping walking skeleton (chunk A).** Multi-module Gradle root under `server/` (Kotlin DSL; `settings.gradle.kts` including `app`; wrapper at the `server/` root); `app` is the only bootable module (Spring Boot + Spring for GraphQL + Spring Security) serving a trivial `ping: String!` query at `/graphql`, behind a placeholder `SecurityConfig` (`permitAll` + CSRF off — replaced in Epic 1). Config read from env vars (§7). Guide: `docs/features/pr-0.1a-ping-walking-skeleton.md`.
  - _Tests:_ `HttpGraphQlTester` integration test asserting `ping` resolves end-to-end through the security filter chain; context-loads smoke test; `./gradlew :app:build` green.
- [x] **PR 0.2 — `common` module + `build-logic` convention plugin (chunk B).** Add a `common` `java-library` subproject (`include("common")`, no feature deps); extract the shared Java 21 toolchain, Spring dependency-management BOM, and test config into a `build-logic` convention plugin applied by both `app` and `common`. Completes the multi-module story and removes per-module build duplication (§3).
  - _Tests:_ `common` compiles and is consumable from `app`; `./gradlew build` assembles a single boot jar from both modules; existing `app` tests stay green under the convention plugin.
- [x] **PR 0.3 — Dev environment & Flyway.** `docker compose` for app + Postgres (§7); Flyway wired with an empty baseline migration; CI runs `./gradlew check` with Testcontainers.
  - _Tests:_ Testcontainers spins up Postgres; Flyway `migrate` + `validate` pass on empty schema in CI.
- [ ] **PR 0.4 — React SPA skeleton.** Vite + TypeScript + Apollo Client + GraphQL Code Generator; a single page that calls `ping` and renders the result. Proxy config for `/graphql`.
  - _Tests:_ Vitest + RTL component test with MSW mocking `ping`; codegen runs in CI and fails on schema drift.

---

## 1. Authentication & token model

**Goal:** register / login / refresh with the two-token model (§6). Everything downstream depends on an authenticated principal.

- [ ] **PR 1.1 — Users & registration.** `users` migration + entity/repository; `register` mutation; password hashing (bcrypt/argon2); email uniqueness + validation.
  - _Tests:_ unit (hashing, duplicate-email rejection); integration (`register` persists a user, rejects dupes/invalid input).
- [ ] **PR 1.2 — Access-token issuance & login.** `login` mutation returning `TokenPayload`; RS256 JWT with ephemeral startup keypair (§6); `iss/sub/iat/exp/jti/email` claims; bearer-auth security filter resolving the current user; unauthenticated mutations exempted.
  - _Tests:_ unit (JWT sign/verify, expiry); security (protected query 401s without token, 200s with; `login`/`register` exempt).
- [ ] **PR 1.3 — Refresh tokens: rotation & reuse detection.** `refresh_tokens` migration (hashed, families) + entity; `refresh` mutation that rotates within a `family_id`; presenting a revoked token revokes the whole family.
  - _Tests:_ unit (rotation links `replaced_by_id`); integration (happy-path rotation; **replayed revoked token → family revoked**); expiry honoured.

---

## 2. Households, memberships, invites & tenancy

**Goal:** multi-tenant core — the isolation property from §6. Auto personal household on signup, plus create/join/switch.

- [ ] **PR 2.1 — Households & auto personal household.** `households` + `memberships` migrations/entities (incl. nullable `timezone`, §5.2); on `register`, auto-create the user's personal household + owner membership; `myHouseholds` query.
  - _Tests:_ integration (signup yields exactly one owned household; `myHouseholds` scoped to caller).
- [ ] **PR 2.2 — Tenancy choke point.** Single guard that verifies the current user is a member of any `householdId` argument before a resolver runs; reject/deny otherwise. Establishes the "never trust client `householdId`" rule (§6).
  - _Tests:_ security (member sees data; **non-member is denied**, no leakage); unit for the guard.
- [ ] **PR 2.3 — Invites & joining.** `invites` migration/entity; `createInvite`, `acceptInvite` mutations; expiry + single-use semantics; new membership created on accept.
  - _Tests:_ unit (expired/used invite rejected); integration (invite → accept → membership + tenant access granted).
- [ ] **PR 2.4 — Roles & authorization.** Role gating for admin actions (invite, rename household, edit others' definitions) per §6.
  - _Tests:_ security (non-admin blocked from admin mutations; admin allowed).

---

## 3. Task definitions

**Goal:** the reusable templates (SPEC §4) — create/edit/archive, effort, assignment. Recurrence _fields_ land here; the _engine_ is Epic 5.

- [ ] **PR 3.1 — Definition CRUD.** `task_definitions` migration/entity (all §5.1 columns incl. `recurrence_rule`/`recurrence_start` as inert strings for now); create/edit/archive mutations; `taskDefinitions(householdId)` query; effort 1–10 validation; ANYONE-vs-MEMBER assignment validation.
  - _Tests:_ unit (effort bounds, assignment/assigned-member consistency); integration (CRUD round-trip, tenant-scoped list, archive hides from active list).

---

## 4. Task instances & "This Week"

**Goal:** the thing users actually complete (SPEC §6.1). One-off flow first; recurrence generation comes in Epic 5.

- [ ] **PR 4.1 — Instances & the This-Week query.** `task_instances` + `completions` migrations/entities (incl. `effort_snapshot`, §5.2; unique `(definition_id, due_date)`); `thisWeek(householdId, filter)` query with §6.1 ordering (nagging/overdue → open → complete) and the "mine" filter; Monday-ISO week helper (§5.2).
  - _Tests:_ unit (week-boundary math, ordering, "mine" filter incl. ANYONE); integration (query returns current-week instances scoped + ordered).
- [ ] **PR 4.2 — Complete an instance.** `completeInstance` mutation → writes a `completion` (who + when, per-membership); idempotent/guarded against double-complete; effort snapshot captured.
  - _Tests:_ unit (double-complete guard); integration + security (only a permitted member can complete; ANYONE allowed for any member).
- [ ] **PR 4.3 — Quick-add.** `quickAddTask` mutation — creates a one-off definition (or inline instance) in the current week per SPEC §6.1 quick-add modal.
  - _Tests:_ integration (quick-add appears in `thisWeek`, defaults to end-of-week due date).
- [ ] **PR 4.4 — Re-add a past task.** `reAddTask(definitionId)` mutation — one-click re-add of a past/template definition into the current week (SPEC §5, §6.2).
  - _Tests:_ integration (re-add creates a current-week instance from an existing definition).

---

## 5. Recurrence & the weekly job

**Goal:** schedule-driven auto-generation + carry-over (SPEC §5, ARCH §5.3). The most logic-heavy area — RRULE via ical4j, idempotent job.

- [ ] **PR 5.1 — RRULE expansion service.** ical4j-backed service answering "which occurrence dates fall in week W?" for a definition's `RRULE` + `DTSTART`; week-resolution + `BYDAY`→due-weekday mapping; multiple occurrences/week supported.
  - _Tests:_ unit-heavy — weekly, `INTERVAL=2` phase, `BYDAY=MO,TH` (multi/week), `UNTIL`/`COUNT` bounds, year-boundary weeks.
- [ ] **PR 5.2 — Generation & carry-over job.** Spring `@Scheduled` week-boundary job: generate one instance per occurrence date (idempotent on `(definition_id, occurrence_date)`), carry incomplete instances forward keeping original due date; `ageWeeks` computed-on-read (§9 #5).
  - _Tests:_ unit (carry-over keeps due date, age derivation); integration (**re-running the job is idempotent — no duplicate instances**; incomplete carries forward, complete does not).

---

## 6. Analytics — "who does more?"

**Goal:** the flagship effort-weighted workload split (SPEC §6.3, ARCH §4).

- [ ] **PR 6.1 — Workload aggregation.** `workload(householdId, range)` query — effort-weighted completion totals per membership over a date range, bucketed by Monday-week; native SQL if JPQL gets ugly (ARCH §3). Aggregates over `effort_snapshot`, not live effort.
  - _Tests:_ unit (weighting math, bucketing); integration (fixture completions → expected per-member slices; snapshot isolation from later effort edits); security (tenant-scoped).

---

## 7. Frontend application shell & screens

**Goal:** the four SPEC §6 destinations, consuming the GraphQL API. Auth/session plumbing first, then screens. (Slices may land as backend endpoints become available.)

- [ ] **PR 7.1 — Auth flows & session plumbing.** Register/login/logout screens; access token in memory + refresh handling; Apollo auth link + automatic refresh on 401; protected routing.
  - _Tests:_ RTL + MSW (login stores token, refresh-on-expiry, logout clears; unauthenticated redirect).
- [ ] **PR 7.2 — App shell & household switcher.** Nav for the four destinations; current-household app context; switcher + settings/invites entry (SPEC §6).
  - _Tests:_ RTL (switching household re-scopes; nav renders destinations).
- [ ] **PR 7.3 — This Week screen.** Landing screen: ordered list (§6.1), "mine" filter, one-tap complete with optimistic cache update, quick-add modal.
  - _Tests:_ RTL + MSW (ordering, filter, optimistic complete updates the list, quick-add flow).
- [ ] **PR 7.4 — Manage / Chores screen.** Definition list + create/edit form (effort, assignment, recurrence builder), re-add action (§6.2).
  - _Tests:_ RTL + MSW (create/edit validation, re-add calls mutation).
- [ ] **PR 7.5 — Analytics screen.** "Who does more?" effort-weighted view over a range (§6.3). _(Follow the `dataviz` skill before building any chart.)_
  - _Tests:_ RTL + MSW (renders workload slices for a range).

---

## 8. Hardening & deployment (the Pi)

**Goal:** make it safe to run — and eventually to expose (SPEC §2, ARCH §6–7).

- [ ] **PR 8.1 — Query protection & rate limiting.** GraphQL depth/complexity limits + basic rate limiting (§6) to protect the Pi.
  - _Tests:_ integration (over-deep/over-complex query rejected; rate limit trips).
- [ ] **PR 8.2 — Production packaging.** Executable jar; `systemd` unit against system Postgres; Caddy reverse proxy (TLS + static bundle + `/graphql` proxy); all config via env (§7).
  - _Tests:_ smoke — jar boots against a Postgres from env; documented run-book; (CI builds the jar).
- [ ] **PR 8.3 — Backups.** Scheduled `pg_dump` to a separate location + a verified restore procedure (§7).
  - _Tests:_ restore-verification script exercised against a dump.

### Extension goals (post-v1, tracked but not scheduled)

- Persisted JWT signing keypair (survive Pi reboot) — ARCH §6.
- Per-household timezone (activate the reserved `households.timezone`) — ARCH §5.2.
- Email verification + HSTS before public exposure — ARCH §6.
- GraalVM native image for footprint — ARCH §7.
- Secondary analytics: neglected-tasks, completion-rate — SPEC §5.

---

## Suggested execution order

Epics 0 → 5 form the critical path to a usable single-household app; **1 (auth)** and **2 (tenancy)** are the security spine and should land before any tenant data. Frontend (Epic 7) can trail each backend epic by one slice. Epic 8 is pre-exposure hardening. Within an epic, PRs are listed in dependency order.
