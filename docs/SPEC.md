# Household Task Manager — Product Specification

> Status: **Design / "what, not how"** · Last updated: 2026-07-01
> This document describes *what* the application does and *for whom*. It deliberately contains no implementation, stack, or architecture decisions — those come later.

## 1. One-liner

A self-hosted, multi-tenant household task manager organized around a **weekly rhythm** — letting the people in a household capture, assign, and track recurring and one-off chores, with authenticated completion tracking and effort-weighted "who does more?" analytics.

## 2. Goals & constraints

| Priority | Goal / constraint |
|----------|-------------------|
| #1 | **Learning** — this is primarily a project to learn from. |
| #2 | **Self-hosting** — runs on a **Raspberry Pi** in the home (modest resources). |
| Stretch | **Public accessibility** — therefore **security is a first-class concern from day one**. |
| Platform | Responsive **web + mobile**. |
| UX | Common operations are **a couple of clicks** from entering the app. |

## 3. Users & access

- **User** — anyone can self-register (email/password, individual accounts). On signup a user automatically gets their **own personal household**, so a solo user is fully functional immediately (this covers the one-person-household requirement with no special-casing).
- **Household** — has 1+ members. A user can **create additional households** and **invite** others (invite link/code).
- **Membership is many-to-many** — a user can belong to several households at once (e.g. a shared "Home" *and* a private personal list) and **switch between them**. Every view is scoped to the **current household**.
- **Authenticated completions** — because completions record an authenticated user, the workload analytics are trustworthy and the app is safe to eventually expose publicly.

## 4. Domain model (the nouns)

> A **household** has **members**. Members create **task definitions** (one-off or recurring). Each week, definitions produce **task instances** that live in that week. A member completes an instance, recording **who** and **when**. Incomplete instances carry forward keeping their due date. Analytics aggregate completed instances by member, weighted by effort.

- **User** — an account; self-registers; gets a personal household on signup.
- **Household** — a tenant; groups members and their tasks.
- **Membership** — joins a user to a household (via invite).
- **Task definition** — the reusable description of a task:
  - **Title** (required)
  - **Description** (optional)
  - **Effort** — integer **1–10**, the relative cost/burden of the task.
  - **Due date** — optional; defaults to the **end of the current week**.
  - **Assignment** — a specific member **or** "anyone" (any member can complete it).
  - **Type** — **one-off** or **recurring** (schedule-driven, e.g. "every Tuesday", "every N weeks").
- **Task instance** — a definition's appearance in a specific week; the thing a user actually completes.
  - Recurring definitions **auto-generate** an instance each applicable week.
  - One-off / past tasks can be **re-added** to the current week in one click.
  - An incomplete instance **carries forward** to the next week, **keeping its original due date** (shown as overdue), with its **age** (weeks carried) tracked.
- **Completion** — records **who** completed an instance and **when**.

## 5. Key product decisions

| Area | Decision |
|------|----------|
| **Recurrence** | Support **both** true recurring tasks (schedule-driven, auto-generated weekly) **and** one-click re-add of past/template tasks. |
| **Carry-over** | Incomplete tasks **keep their original due date**, show as **overdue**, and track how many weeks they've been carried. |
| **Fairness** | **Report-only** — show who did what; no auto-assignment or rotation. |
| **Effort** | Every task carries an **effort score (1–10)**; analytics are **effort-weighted**, not just task counts. |
| **Reminders** | **In-app only** for v1 (overdue / due-today badges & lists). No push/email yet (Pi constraint). |
| **Analytics headline** | **"Who does more?"** — effort-weighted workload split per member over time. Neglected-tasks and completion-rate views are secondary/later. |

## 6. Screens & flows

Navigation has **four destinations**: **This Week** (home) · **Manage / Chores** · **Analytics** · **Household switcher** (+ settings & invites).

### 6.1 This Week — home (landing screen)
- Shows the **current week only** (no week paging; history lives in Analytics).
- Scoped to the **current household** (the user's chosen default on open; switchable).
- **Default shows everyone's tasks**, with a quick **"mine"** filter (assigned-to-me + "anyone").
- **Ordering:** ① nagging / overdue (carried-over, aged) → ② incomplete (this week) → ③ complete (dimmed / collapsed at the bottom).
- **Primary interaction:** one tap to **mark complete** → records the current user + timestamp.
- **Quick-add modal** (medium-precedence button, not occupying the screen) for **once-off tasks in the current week**.

### 6.2 Manage / Chores
- Lists all **task definitions** — recurring chores and re-addable templates.
- Where you **create / edit recurrence**, set effort & defaults, and **re-add a past task** to the current week.
- Deliberately separate from the quick-add modal: *frequent quick adds* vs *occasional setup*.

### 6.3 Analytics
- Its own page. Flagship view: **"who does more?"** — effort-weighted workload split per member over time.

## 7. Explicit non-goals (v1)

- No push / email / external notifications (in-app only).
- No automatic task assignment, rotation, or fairness enforcement.
- No paging through past/future weeks on the home screen.

## 8. Open questions / next phase ("how")

Not yet decided — the next phase moves from *what* to *how*:
1. **Architecture & stack** — chosen through the learning-goal and Raspberry-Pi lenses.
2. **Concrete data model** — turning §4 nouns into entities & relationships (note: effort-weighting and carry-over/age tracking have data implications).
3. **Security architecture** — auth, multi-tenant isolation between households, and what "safe to expose publicly" requires.
