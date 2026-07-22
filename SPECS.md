# House Points — Specifications

## 1. Overview

A points-tracking system for a school (or similar organization). Students are
divided into teams called **houses**. **Teachers** award or deduct points from
houses throughout the day; an **admin** manages the roster of houses and
teacher accounts. Every point change is recorded as an **event** so a full,
publicly viewable history is always available, alongside a live leaderboard.

The system has two parts:
- A **PHP/MySQL API**
- A **web client written in Kotlin Compose Multiplatform, targeting Compose
  for Web (Wasm & Js)** — a single browser-based app, no native Android/iOS/Desktop
  builds. It serves phone and PC users alike through a URL.

## 2. Roles & Accounts

There are exactly two roles, stored on a single `hp_users` table via a `role`
column:

- **teacher** — can award/deduct points to houses, and void their own
  erroneous point transactions.
- **admin** — a superset of teacher. Admins can do everything a teacher can,
  **plus** manage houses and teacher accounts, and void *any* teacher's
  erroneous transaction (not just their own).

An account is either `teacher` or `admin`, not a combination of separately
toggleable permissions — `admin` simply unlocks additional endpoints on top of
the teacher capabilities.

There is no self-registration and no "admin creates another admin" API flow.
The very first admin account is created out-of-band (seed script / CLI /
direct DB insert) rather than through the API.

Removing a house or a teacher account is a **soft-delete** (`active =
false`): the row is hidden from active lists and (for teachers) login is
blocked, but historical events keep referencing it unchanged and continue to
display its name normally in event history. Nothing about a house or teacher
is ever hard-deleted.

## 3. Domain Model

**hp_users**

| field | type | notes |
|---|---|---|
| id | PK | |
| username | string, unique | login identifier |
| password_hash | string | |
| role | enum(`teacher`, `admin`) | |
| display_name | string | shown in event history |
| active | bool, default true | soft-delete flag |

**hp_houses**

| field | type | notes |
|---|---|---|
| id | PK | |
| name | string | |
| active | bool, default true | soft-delete flag |

**hp_point_events** — the append-mostly ledger; a house's point total is always
`SUM(points)` over its events.

| field | type | notes |
|---|---|---|
| id | PK, auto-increment | doubles as the time-ordering/pagination key |
| house_id | FK → houses | |
| teacher_id | FK → users | the creator (kept even if the teacher is later deactivated) |
| points | signed integer, non-zero | positive = awarded, negative = deducted |
| comment | string, nullable | optional free-text note from the teacher |
| created_at | datetime | |

Voiding an erroneous transaction (see §5.3) **hard-deletes** the row —
no trace is kept, and the house total simply no longer includes it.

**hp_refresh_tokens** — backs the 72h refresh flow (see §4).

| field | type | notes |
|---|---|---|
| id | PK | |
| user_id | FK → users | |
| token_hash | string | hash of the opaque token, never store it in plaintext |
| expires_at | datetime | issued_at + 72h |
| revoked_at | datetime, nullable | set on logout / rotation |
| created_at | datetime | |

## 4. Authentication

- **Access token**: JWT, **15 minute TTL**. Claims: `sub` (user id), `role`,
  `username`, `iat`, `exp`.
- **Refresh token**: opaque random string, **72 hour TTL**, stored server-side
  (hashed) in `hp_refresh_tokens` so it can be revoked. On every refresh, the old
  token is revoked and a new one issued (rotation) — this is the standard way
  to make a long-lived, DB-backed refresh token safer against replay if it
  ever leaks.
- Logging out revokes the refresh token immediately; the access token still
  naturally expires within 15 minutes regardless.

## 5. API

All endpoints are JSON over HTTP. Below is a proposed concrete surface —
refine as needed during implementation.

### 5.1 Public (no auth)

| Method & path | Description |
|---|---|
| `GET /api/houses` | List active houses with their current point totals. |
| `GET /api/events?page_size=&before_id=&teacher_id=&house_id=` | Paginated event history, **newest first**. `page_size` default **20**, max **100**. `before_id` (optional) continues from a previous page. Response: `{ events: [...], next_id: <id, or null if no more> }` — `next_id` is the id to pass as `before_id` for the following page. `teacher_id`/`house_id` filters are optional and combine with AND. |
| `GET /api/events/since?since_id=&page_size=` | All events with `id > since_id` (that event itself excluded), **oldest first** — for clients polling for new events since they last checked. Capped at the same max page size (100). Response: `{ events: [...], last_id: <id of the last event, or null if none> }` — the client repolls using `last_id` as the new `since_id` (keeping its current one when `last_id` is null). |

### 5.2 Teacher (auth: teacher or admin)

| Method & path | Description |
|---|---|
| `POST /api/houses/{houseId}/points` | Body `{ points: <signed non-zero int>, comment?: <string> }`. Creates a point event. |
| `DELETE /api/events/{eventId}` | Void an erroneous transaction (hard delete). A teacher may only delete their **own** events; an admin may delete **any**. |

### 5.3 Admin only

| Method & path | Description |
|---|---|
| `POST /api/houses` | Body `{ name }`. Create a house. |
| `DELETE /api/houses/{houseId}` | Soft-delete (deactivate) a house. |
| `POST /api/users` | Body `{ username, password, role, display_name }`. Create a teacher or admin account; the admin sets the initial password directly. |
| `DELETE /api/users/{userId}` | Soft-delete (deactivate) an account. |

### 5.4 Any authenticated user

| Method & path | Description |
|---|---|
| `POST /api/auth/login` | `{ username, password }` → `{ access_token, refresh_token, expires_in }` |
| `POST /api/auth/refresh` | `{ refresh_token }` → new rotated pair |
| `POST /api/auth/logout` | `{ refresh_token }` → revokes it |
| `PATCH /api/me/password` | `{ current_password, new_password }` — change own password. |

## 6. Web Client (Compose for Web)

A single Kotlin Compose Multiplatform app targeting **Compose for Web
(Wasm & Js)**, served over HTTP and used from any phone or PC browser — no
installable native app.

The UI is **French only**. There is no language switcher and no English
fallback; every displayed string is authored in French from the start (see
`front/ARCHITECTURE.md` for how this is enforced in code).

Views:
- **Admin view** (authenticated, role=admin): manage houses and teacher
  accounts, award/void points, change own password.
- **Teacher view** (authenticated, role=teacher): award/void own points,
  change own password.
- **Public view**: houses + point totals, and browsable event history
  (paginated per §5.1).
- **Public display**: the same public view, left open fullscreen in a public
  area, auto-refreshing (polling `GET /api/events/since` for new events and
  re-fetching `GET /api/houses` for updated totals) — no separate build.
