# Household Task Manager — Technical Architecture

> Status: **Design / "how"** · Last updated: 2026-07-02
> Companion to [`SPEC.md`](./SPEC.md), which describes *what* the app does. This document describes *how* it is built: stack, structure, data model, API, and security. It is a **living draft** — sections marked _(open)_ are not yet decided.

---

## 1. Chosen stack

| Layer | Choice | Notes |
|-------|--------|-------|
| **Backend language** | **Java** (LTS — 21) | Build with **Gradle**. |
| **Backend framework** | **Spring Boot 4** (Spring Framework 7) + **Spring for GraphQL** | Netflix DGS is a viable alternative; see §4. |
| **API style** | **GraphQL** | Single typed schema; SPA is the primary consumer. |
| **Frontend** | **React SPA**, **TypeScript** | Served as static assets; talks to the GraphQL endpoint. |
| **Database** | **PostgreSQL** | Real SQL, migrations, strong multi-tenant support. |
| **Host** | **Raspberry Pi**, self-hosted | Modest resources drive several decisions below. |

### Guiding constraints (from SPEC §2)
1. **Learning is the #1 goal** — prefer approaches that teach durable fundamentals over shortcuts.
2. **Runs on a Raspberry Pi** — memory and CPU are limited; the JVM footprint is a real consideration (see §7).
3. **Security is first-class from day one** — the app may eventually be publicly exposed (see §6).

---

## 2. System overview

```
                          Raspberry Pi (home network)
  ┌─────────────┐        ┌───────────────────────────────────────────┐
  │  Browser /  │  HTTPS │  Reverse proxy (Caddy / nginx)             │
  │  Mobile web │◄──────►│   • TLS termination                        │
  └─────────────┘        │   • serves React static bundle             │
                         │   • proxies /graphql → backend             │
                         │        │                                   │
                         │        ▼                                   │
                         │  Spring Boot app (JVM)                     │
                         │   • GraphQL endpoint (/graphql)            │
                         │   • auth / session / tenancy               │
                         │   • domain + persistence                   │
                         │        │                                   │
                         │        ▼                                   │
                         │  PostgreSQL                                │
                         └───────────────────────────────────────────┘
```

- **Single deployment, multi-tenant** — one running instance serves many independent households; every request is scoped to a *current household* (SPEC §3).
- **Three processes**: reverse proxy, JVM app, Postgres. A scheduled job inside the app generates recurring task instances (see §5.3).

---

## 3. Backend structure

Layered, feature-per-**Gradle module** (**decided**): strong, compile-time-enforced separation between features. One bootable `app` module assembles the feature library modules into a single jar; features depend on `common` and on each other only through published API interfaces, never internals.

```
taskmanager/                (repo root: docs/, .github/, compose.yaml, server/)
└── server/                 gradle root: wrapper, settings.gradle.kts, build-logic/
    ├── build-logic/        convention plugin — shared Java 21 toolchain + test config
    ├── app/                @SpringBootApplication; the only bootable module; depends on features
    ├── common/             errors, pagination, shared types; no feature dependencies
    ├── auth/
    ├── household/
    ├── task/
    └── analytics/
```

Each feature module owns its package `io.github.liffeymike.taskmanager.<feature>`, its own `*.graphqls` schema fragment (Spring for GraphQL merges all schema files on the classpath), controllers, services, and entities. Grow it in stages — start with `app` + `common`, carve out each feature module as its epic is built.

Within a feature module, the conventional layering:

- **GraphQL controllers** (`@Controller` + `@SchemaMapping` / `@QueryMapping`) — thin; translate schema ↔ domain.
- **Service layer** — business rules (carry-over, recurrence, effort weighting). The interesting logic lives here.
- **Repositories** — Spring Data JPA (or JDBC) over Postgres.
- **Domain entities** — mapped to the tables in §5.

**Persistence** (**decided**): Spring Data **JPA / Hibernate** — the industry-standard ORM and a primary learning target. Accepted tradeoffs to manage:
- **N+1 with GraphQL** — lazy loading + nested resolvers is the classic trap. Mitigate with **DataLoader** batching (§4), fetch joins, and `@EntityGraph`.
- **Analytics queries** — effort-weighted aggregation is awkward in JPQL/Criteria; drop to native SQL (or add jOOQ/JdbcClient just for those queries) if it gets ugly.
- **Pi footprint** — Hibernate is the heaviest option; keep an eye on heap/startup (§7).

**Migrations**: **Flyway** — versioned, plain-SQL migrations checked into the repo.

---

## 4. API design (GraphQL)

A single schema at `/graphql`. Sketch of the core types (illustrative, not final):

```graphql
type User { id: ID!, email: String!, displayName: String! }

type Household { id: ID!, name: String!, members: [Membership!]! }

type Membership { id: ID!, user: User!, role: Role!, joinedAt: DateTime! }

type TaskDefinition {
  id: ID!
  title: String!
  description: String
  effort: Int!            # 1..10
  assignment: Assignment! # ANYONE | specific member
  type: TaskType!         # ONE_OFF | RECURRING
  recurrence: Recurrence  # null for one-off
}

type TaskInstance {
  id: ID!
  definition: TaskDefinition!
  dueDate: Date!          # original due date; may be in a past week
  ageWeeks: Int!          # weeks carried forward (0 = this week)
  status: InstanceStatus! # OPEN | COMPLETE
  completion: Completion
}

type Completion { by: Membership!, at: DateTime! }

type Query {
  thisWeek(householdId: ID!, filter: TaskFilter): [TaskInstance!]!
  taskDefinitions(householdId: ID!): [TaskDefinition!]!
  workload(householdId: ID!, range: DateRange!): [WorkloadSlice!]!  # "who does more?"
}

type TokenPayload { accessToken: String!, refreshToken: String!, tokenType: String!, expiresIn: Int! }

type Mutation {
  # auth (unauthenticated — exempt from the bearer requirement)
  register(input: RegisterInput!): User!
  login(email: String!, password: String!): TokenPayload!
  refresh(refreshToken: String!): TokenPayload!

  completeInstance(instanceId: ID!): TaskInstance!
  quickAddTask(householdId: ID!, input: QuickAddInput!): TaskInstance!
  reAddTask(definitionId: ID!): TaskInstance!
  # ... household, invite, definition CRUD
}
```

- **Framework choice** (**decided: Spring for GraphQL**): official, schema-first, integrates with Spring Security. **Netflix DGS** was the more feature-rich alternative (codegen, testing) but heavier; not adopted.
- **N+1 avoidance**: use **DataLoader** batching for `members`, `definition`, `completion` resolvers from the start.
- **Errors**: map domain errors to typed GraphQL errors with stable codes, not raw exceptions.

---

## 5. Concrete data model

Maps SPEC §4 nouns to tables. `household_id` appears on every tenant-scoped row to make isolation explicit and indexable (see §6).

### 5.1 Entities

| Table | Key columns | Notes |
|-------|-------------|-------|
| `users` | id, email (unique), password_hash, display_name, created_at | Global identity. |
| `refresh_tokens` | id, user_id, family_id, token_hash (unique, SHA-256), expires_at, revoked_at, replaced_by_id, created_at | Server-side refresh tokens for rotation + reuse detection (§6). Store only the hash. |
| `households` | id, name, timezone (nullable), created_at | A tenant. `timezone` reserved for the per-household extension; falls back to `app.timezone` in v1 (§5.2). |
| `memberships` | id, user_id, household_id, role, joined_at · **unique(user_id, household_id)** | Many-to-many join (SPEC §3). Completions reference this, not `users`, so "who did it" is per-household. |
| `invites` | id, household_id, code (unique), created_by, expires_at, accepted_at | Invite link/code flow. |
| `task_definitions` | id, household_id, title, description, effort (1–10), assignment_type (ANYONE\|MEMBER), assigned_membership_id (nullable), type (ONE_OFF\|RECURRING), recurrence_rule (nullable, iCal RRULE), recurrence_start (nullable, DTSTART anchor), default_due_offset, created_by, created_at, archived_at | The reusable template. `recurrence_rule` + `recurrence_start` set only for RECURRING (§5.3). |
| `task_instances` | id, household_id, definition_id, due_date (occurrence date), first_week, age_weeks, effort_snapshot, status, created_at · **unique(definition_id, due_date)** | A definition's occurrence; the thing completed. A recurring definition may produce **multiple instances per week** (§5.3); the unique constraint is the job's idempotency guard. |
| `completions` | id, instance_id, membership_id, completed_at | Immutable record of who + when. |

### 5.2 Data implications flagged in SPEC §8

- **Effort snapshotting** _(open, leaning yes)_: store `effort_snapshot` on the instance so historical analytics don't shift when a definition's effort is later edited. Analytics aggregate over instance snapshots, not live definition values.
- **Carry-over / age**: an incomplete instance keeps its original `due_date`; `age_weeks` is derived from (current week − first_week). Decide whether age is **stored and incremented** by the weekly job or **computed on read** — computed-on-read is simpler and avoids drift.
- **Week identity** (**decided**):
  - **Boundary**: **Monday-start ISO-8601 week** (`java.time` `WeekFields.ISO` / `IsoFields`). "End of current week" (default due date) = the Sunday of that week.
  - **Week key**: represent a week as its **Monday `LocalDate`** — sorts naturally, ±7-day arithmetic, no week-number/year-boundary edge cases. Used for the generation job's idempotency key, carry-over, and analytics bucketing.
  - **Storage zone**: all instants stored in **UTC** (`Instant` / `timestamptz`).
  - **Definition zone**: a **single configured application timezone** (`app.timezone`, default = the Pi's local zone) decides what calendar day/week a moment falls in — i.e. drives "today", "overdue", "due today", the week rollover, and RRULE `BYDAY`→due-date. Chosen for simplicity; valid because households are expected to share one region.
  - **Extension hedge**: add a **nullable `households.timezone` column now** that falls back to `app.timezone`. v1 ignores it; going **per-household** later (for geographically diverse tenants / public exposure) becomes a behaviour change, not a migration. Per-*user* zones would only ever affect display formatting — the canonical week stays a household concept.

### 5.3 Recurrence & the weekly job

- **Recurrence encoding** (**decided: RRULE**): store an **iCalendar RFC 5545 `RRULE`** string in `task_definitions.recurrence_rule` (e.g. `FREQ=WEEKLY;INTERVAL=2;BYDAY=TU`). Chosen as the industry standard — future-proof, interoperable (potential calendar export), and good learning value. Details:
  - **Library** (**decided: ical4j**): use **ical4j** to parse/expand rules rather than hand-rolling expansion.
  - **Anchor**: pair the rule with a **`DTSTART`** (a `recurrence_start` date on the definition) so `INTERVAL` phase (which weeks an "every N weeks" rule fires) is well-defined.
  - **Weekly grain**: the app only consumes the rule at **week resolution** — the job asks "does this rule have an occurrence within week W?" and ignores time-of-day. `BYDAY` maps to the instance's **due weekday** within the week.
  - **Multiple occurrences per week** (**decided: yes**): a rule may fire more than once in a week (e.g. `BYDAY=MO,TH`). The job generates **one instance per occurrence date**, so a definition can have several instances in the same week — the generation/idempotency key is **(definition_id, occurrence_date)**, and `task_instances.due_date` holds the specific occurrence date.
  - **Optional end**: `UNTIL`/`COUNT` are supported by the format for free if bounded recurrences are ever wanted.
- A **scheduled task** (Spring `@Scheduled`) runs at each week boundary to: (1) expand each recurring definition's `RRULE` against the target week and generate an instance **per occurrence date**, (2) carry incomplete instances forward. Must be **idempotent** (safe to re-run after a Pi reboot/downtime) — key on **(definition_id, occurrence_date)** (enforce with a unique constraint on `task_instances`).

---

## 6. Security architecture

Treated as first-class per SPEC §2; assume eventual public exposure.

- **Authentication**: email + password. Hash with **bcrypt/argon2** (never store plaintext). Consider email verification before public exposure.
- **Tokens — two-token model** (following the `ChoreManager/services/users/.../auth` reference architecture):
  - **Access token** — a short-lived **JWT** (15 min), sent as an **`Authorization: Bearer <token>`** header on **every request**. The backend validates the signature + expiry and resolves the current user from `sub` — **no server-side session lookup** on the hot path.
    - Signing: **RS256** (asymmetric). The issuer holds the private key; verifiers use the public key (exposed via key id / `kid` header for rotation). **v1**: generate an **ephemeral RSA keypair at startup** (as in the reference) — simplest; each restart invalidates outstanding access tokens, tolerable given the 15-min TTL + refresh. **Extension goal**: persist the keypair so a Pi reboot doesn't force re-auth.
    - Claims: `iss`, `sub` (user id), `iat`, `exp`, `jti`, plus `email`. Household scoping is **not** in the token — it is verified per-request against `memberships` (see isolation below).
  - **Refresh token** — an **opaque random string** (32 secure-random bytes, hex), 30-day TTL, stored **server-side hashed** (SHA-256, never plaintext). Exchanged at a `refresh` endpoint for a new access token.
  - **Rotation + reuse detection (token families)** — each refresh **rotates**: the presented token is revoked and a new one issued in the **same `family_id`**, linked via `replaced_by_id`. If an **already-revoked** token is presented (i.e. a stolen/replayed token), the **entire family is revoked** — this is the mechanism that lets otherwise-stateless JWT auth actually revoke a compromised session.
  - **SPA storage**: keep the access token in memory; the refresh token is the persistent credential — guard against XSS accordingly.
  - **API shape** (**decided**): auth is exposed as **GraphQL mutations** (`register`, `login`, `refresh`) returning the token payload — not REST. (The reference uses REST endpoints; here everything goes through the single `/graphql` endpoint.) These mutations are **unauthenticated** and must be exempt from the bearer-token requirement in the security config.
- **Multi-tenant isolation** — the core security property. Every tenant-scoped query **must** filter by a `household_id` the current user is a verified member of. Options, strongest first:
  - Enforce at a **single choke point** (a tenancy filter / service guard) so no resolver can forget it.
  - Consider Postgres **Row-Level Security** as defence-in-depth later.
  - Never trust a `householdId` from the client without checking membership.
- **Authorization**: membership role gates admin actions (invite, rename household, edit others' definitions). Completions allowed for assignee or any member when assignment is ANYONE.
- **Transport**: TLS at the reverse proxy; HSTS before public exposure.
- **Input**: GraphQL query **depth/complexity limits** and rate limiting to protect a small Pi from abusive queries.
- **Secrets**: DB credentials and signing keys via environment/`.env` outside the repo.

---

## 7. Deployment & the Pi

- **Packaging**: Gradle builds an executable jar.
- **Dev vs prod split** (**decided**): **develop with `docker compose`, deploy with `systemd`.**
  - **Development** — `docker compose` brings up app + Postgres (+ proxy) for a reproducible, one-command local environment that matches nobody's machine specifically and everyone's generally.
  - **Production (Pi)** — run the jar directly as a **systemd** service against a **system-installed Postgres**, avoiding container overhead on constrained hardware. systemd handles start-on-boot, restarts, and logging (journald).
  - **Keep the two in sync**: the app must read all environment-specific config (DB URL/credentials, JWT secret, ports) from **environment variables**, so the same jar runs unchanged under compose or systemd. Aim to keep Postgres versions aligned between the compose image and the Pi package.
- **JVM footprint** — the main Pi risk. Mitigations: cap the heap (`-Xmx`), tune the container/service memory, and keep an eye on startup time. **GraalVM native image** (via Spring AOT) is a future option to slash memory/startup at the cost of build complexity — noted, not adopted for v1.
- **Backups**: scheduled `pg_dump` to a separate disk/location; verify restores.
- **Reverse proxy**: **Caddy** (automatic HTTPS, tiny config) or nginx. Serves the React bundle and proxies `/graphql`.

---

## 8. Frontend structure (React SPA)

- **Build**: Vite + TypeScript. Output is static files served by the proxy.
- **GraphQL client** (**decided: Apollo Client**): the batteries-included, industry-standard client — **normalized cache** (entities keyed by id, shared across queries so mutations like "mark complete" can update lists automatically), rich devtools, first-class mutations/optimistic UI/pagination. Tradeoffs accepted: larger bundle (~30–40 KB gzipped) and more cache concepts to learn (type/field policies).
- **Type safety**: **GraphQL Code Generator** to produce TS types + typed hooks from the schema — one source of truth end to end.
- **Routing** maps to SPEC §6's four destinations: This Week (home), Manage/Chores, Analytics, Household switcher.
- **State**: current household is app-wide context; server data via the GraphQL client cache; minimal local UI state.

---

## 9. Open decisions (rolled up)

| # | Decision | Current lean |
|---|----------|--------------|
| 1 | Persistence: JPA vs jOOQ/JDBC (**decided: JPA**) | jOOQ/native SQL for analytics if needed |
| 2 | GraphQL: Spring for GraphQL vs DGS (**decided: Spring for GraphQL**) | — |
| 3 | Auth: JWT access + rotating refresh token, via **GraphQL mutations** (**decided**) | Ephemeral signing key for v1; persisted key is an extension goal |
| 4 | Recurrence: iCal RRULE via **ical4j**, multiple occurrences/week allowed (**decided**) | — |
| 5 | Age: stored vs computed-on-read | Computed |
| 6 | Effort snapshot on instance | Yes |
| 7 | Week definition + timezone (**decided**) | Mon/ISO week, Monday-LocalDate key, UTC storage, single app timezone; per-household tz is an extension |
| 8 | Dev/deploy (**decided**) | docker compose for dev, systemd for prod |
| 9 | Frontend GraphQL client (**decided: Apollo Client**) | — |

---

## 10. Next steps

1. Resolve any remaining highest-leverage open decisions (the data-model and schema blockers — #2, #4, #7 — are now decided).
2. Write the initial Flyway migration for §5 tables.
3. Stand up a walking skeleton: Spring Boot + one GraphQL query + Postgres + a React page that reads it.
