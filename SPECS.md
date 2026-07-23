# House Points — Specifications

## 1. Overview

A points-tracking system for a school (or similar organization). Students are
divided into teams called **houses**. **Teachers** award or deduct points from
houses throughout the day; an **admin** manages the roster of houses and
teacher accounts. Every point change is recorded as an **event** so a full,
publicly viewable history is always available, alongside a live leaderboard.
Each event's comment is not written by the teacher — it is generated
automatically by an AI (see §5.5), which invents a witty, in-character,
ironic one-liner for the occasion.

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

There is no self-registration. The very first admin account is created
out-of-band, via the bootstrap script `back/src/bin/create-admin.php`. After
that, `POST /api/teachers` (admin-only) can create additional accounts with
role `teacher` or `admin` — the API does allow an admin to create another
admin — though the current Admin UI only ever creates `teacher` accounts (see
§6).

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
| comment | string, NOT NULL | AI-generated one-liner (see §5.5) — never supplied by the client |
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

- **Access token**: JWT, **15 minute TTL** (configurable via `JWT_TTL` in
  `.env`). Claims: `sub` (user id), `role`, `username`, `iat`, `exp`.
- **Refresh token**: opaque random string, **72 hour TTL** (fixed in code, not
  env-configurable), stored server-side
  (hashed) in `hp_refresh_tokens` so it can be revoked. On every refresh, the old
  token is revoked and a new one issued (rotation) — this is the standard way
  to make a long-lived, DB-backed refresh token safer against replay if it
  ever leaks.
- Logging out revokes the refresh token immediately; the access token still
  naturally expires within 15 minutes regardless.

## 5. API

All endpoints are JSON over HTTP. This is the current implemented surface.

### 5.1 Public (no auth)

| Method & path | Description |
|---|---|
| `POST /api/auth/login` | `{ username, password }` → `{ access_token, refresh_token, expires_in }` |
| `POST /api/auth/refresh` | `{ refresh_token }` → new rotated pair |
| `POST /api/auth/logout` | `{ refresh_token }` → revokes it |
| `GET /api/houses` | List active houses with their current point totals. |
| `GET /api/teachers` | List active teacher accounts (`id`, `username`, `display_name`). |
| `GET /api/events?page_size=&before_id=&teacher_id=&house_id=` | Paginated event history, **newest first**. `page_size` default **20**, max **100**. `before_id` (optional) continues from a previous page. Response: `{ events: [...], next_id: <id, or null if no more> }` — `next_id` is the id to pass as `before_id` for the following page. `teacher_id`/`house_id` filters are optional and combine with AND. |
| `GET /api/events/since?since_id=&page_size=` | All events with `id > since_id` (that event itself excluded), **oldest first** — for clients polling for new events since they last checked. Capped at the same max page size (100). Response: `{ events: [...], last_id: <id of the last event, or null if none> }` — the client repolls using `last_id` as the new `since_id` (keeping its current one when `last_id` is null). |

### 5.2 Teacher (auth: teacher or admin)

| Method & path | Description |
|---|---|
| `POST /api/houses/{houseId}/points` | Body `{ points: <signed non-zero int> }`. Creates a point event; `comment` is generated server-side (see §5.5) and is not a request parameter. |
| `DELETE /api/events/{eventId}` | Void an erroneous transaction (hard delete). A teacher may only delete their **own** events; an admin may delete **any**. |

### 5.3 Admin only

| Method & path | Description |
|---|---|
| `POST /api/houses` | Body `{ name }`. Create a house. |
| `DELETE /api/houses/{houseId}` | Soft-delete (deactivate) a house. |
| `POST /api/teachers` | Body `{ username, password, role, display_name }`. Create a teacher or admin account; the admin sets the initial password directly. |
| `DELETE /api/teachers/{userId}` | Soft-delete (deactivate) an account. |

### 5.4 Any authenticated user

| Method & path | Description |
|---|---|
| `PATCH /api/me/password` | `{ current_password, new_password }` — change own password. No frontend screen calls this yet (see §6) — currently API-only. |

### 5.5 AI-generated comments

Every point event's `comment` is written by Claude (model `claude-sonnet-5`, via the
official Anthropic PHP SDK), not the teacher — there is no `comment` request
parameter on §5.2's endpoint. Generation is **synchronous**, inline in the
same request that awards/deducts the points (no job queue), so that request
takes a few extra seconds while the model responds.

The teacher and house names and the signed point delta are passed to the
model, which is **not** told the real reason for the change — it invents a
plausible, often absurd, pretext. The system prompt (currently instructing:
French only, one sentence, ≤250 characters, an incisive/ironic/almost-mean
tone, and to mention the teacher by name) lives in `back/src/AIPrompt.txt`, a
plain text file colocated with `.env` so it can be tuned by a non-programmer
without touching code or redeploying.

If the Anthropic API call fails or times out, the point event still records —
with a plain, deterministic French fallback sentence ("`{Teacher} a
ajouté/retiré {N} points à {House}.`") instead of blocking the request. A
Claude outage never prevents awarding points; `comment` is always non-empty.

Requires `ANTHROPIC_API_KEY` set in `.env`.

## 6. Web Client (Compose for Web)

A single Kotlin Compose Multiplatform app targeting **Compose for Web
(Wasm & Js)**, served over HTTP and used from any phone or PC browser — no
installable native app.

The UI is **French only**. There is no language switcher and no English
fallback; every displayed string is authored in French from the start (see
`front/ARCHITECTURE.md` for how this is enforced in code).

Views (routed via a single shared shell — top bar + navigation drawer — in
`front/src/commonMain/kotlin/ui/AppRoot.kt`):

- **Classement** (public, no auth) — the app's default/start screen, and where
  logout returns to. A grid of house cards (name + points), sortable by name
  or by points, with adjustable columns-per-row and font size (handy when
  projected). Auto-refreshes every minute by re-fetching `GET /api/houses`
  (it does **not** poll `GET /api/events/since`), plus a manual reload button.
  Renders inside the normal top bar/drawer chrome like every other screen —
  there is no separate fullscreen or chrome-less "public display" mode.
- **Historique** (public, no auth) — intended for the paginated event history
  (`GET /api/events`, §5.1); **not implemented yet**, currently a "coming
  soon" placeholder.
- **Connexion**: the login form (username/password), shown in the drawer only
  when logged out.
- **Enseignant** (authenticated, teacher or admin): award or remove points
  from a house via a single signed-amount input behind a confirmation dialog
  — "removing" is just a negative amount on the same
  `POST /api/houses/{houseId}/points` call, not a separate action. Also shows
  an in-memory list of that session's own transactions as a running reminder;
  it resets whenever the teacher navigates away (by design, not persisted).
  There is currently no UI to void/undo an already-recorded transaction
  (`DELETE /api/events/{eventId}`, §5.2, exists on the backend but no screen
  calls it yet), and no change-own-password UI.
- **Administrateur** (authenticated, admin only): manage the roster of houses
  and teacher accounts (add/remove both). Accounts created here are always
  `role: teacher` — the API itself also accepts `role: admin` (§2), but this
  screen never sends it. No points UI here: an admin uses the same
  **Enseignant** screen for that (the drawer shows both entries to an admin
  account, since every admin is also a teacher). No change-own-password UI.
