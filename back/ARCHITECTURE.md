# Backend Architecture

This document records the PHP backend architecture decisions made for this
project and the reasoning behind each one.
See `SPECS.md` at the repo root for the functional specification.

## 1. Overall pattern: layered micro-framework

Three architectures were considered:

| Option | Description | Verdict |
|---|---|---|
| Flat procedural / router-script | Standalone PHP scripts per endpoint, manual dispatch, no framework | Rejected — no real routing, duplicated boilerplate, hard to test, doesn't scale past a handful of endpoints |
| **Micro-framework, layered (chosen)** | Slim for routing/middleware, controllers, repositories, PSR-15 auth middleware | **Chosen** |
| Full framework (Laravel/Symfony) | Batteries-included: ORM, migrations, validation, DI container | Rejected — heavier footprint and more conventions than a small API with a handful of resources needs |

**Rationale:** the middle option gives real routing, declarative
per-route/group middleware (critical for JWT auth), and testable
controllers/repositories, without the overhead of a full framework's
conventions, ORM, or startup cost. It matches the project's size: a handful
of resources (houses, users, point events, refresh tokens).

## 2. Router: Slim Framework

Considered: `bramus/router` (single-class, closure-based), `nikic/fast-route`
(matcher only, no request/response or middleware pipeline), and **Slim**
(routing + PSR-7 + PSR-15, built on `fast-route` internally).

**Chosen: Slim.** It's the only one of the three with native PSR-15 support,
which is what will let the JWT middleware be attached declaratively to a
route group instead of being manually included at the top of every protected
script, e.g.:

```php
$app->group('/api', function (RouteCollectorProxy $group) use ($deps) {
    $group->get('/houses', [$deps['houses'], 'index']);
    // ...
})->add($deps['jwtAuth']);
```

`bramus/router` would mean hand-rolling middleware dispatch; `fast-route`
alone would mean building the PSR-7 request/response plumbing and error
handling from scratch.

## 3. Authentication middleware: PSR-15

Auth will be enforced by a `JwtAuthMiddleware` class implementing
`Psr\Http\Server\MiddlewareInterface`. A PSR-15 middleware receives the
request and a handler representing "the rest of the pipeline": it can
inspect/modify the request, call `$handler->handle($request)` to continue, or
short-circuit and return a response directly (here: a `401` when the bearer
token is missing/invalid/expired).

**Rationale:** because it's a standard interface (not a bespoke pattern),
this middleware will work unchanged in any other PSR-15-compliant
router/framework, and it composes cleanly with Slim's route groups rather
than needing to be duplicated per-controller.

On success, the decoded claims will be attached to the request as an
attribute (`$request->withAttribute('jwt', $claims)`) so downstream
controllers can read `sub` (user id) and `role` without re-verifying the
token.

## 4. JWT library: firebase/php-jwt

Considered: `firebase/php-jwt` (minimal encode/decode), `lcobucci/jwt`
(object-oriented builder/parser with composable claim constraints), and
`tymon/jwt-auth`/PASETO (full auth packages or an alternative token format).

**Chosen: `firebase/php-jwt` (`^7.1`).** Simplest API for a project that
hand-rolls its own login/refresh flow rather than pulling in a framework-tied
auth package; the most widely used PHP JWT library.

Two things to keep in mind when pinning the dependency and configuring
secrets:

- Pin to `^7.1`.
- `firebase/php-jwt` 7.x enforces a **minimum HMAC key length** for HS256
  signing (rejects short secrets with `DomainException: Provided key is too
  short`), a real security hardening over 6.x. `JWT_SECRET` must be at least
  32 bytes — generate one with `openssl rand -base64 48` — and `.env.example`
  should document this requirement.

## 5. Data layer: raw PDO + repository classes

Considered: raw PDO, a query builder (e.g. `illuminate/database` standalone),
and a full ORM (Doctrine).

**Chosen: raw PDO**, wrapped in one repository class per entity (e.g. a
`UserRepository`, a `HouseRepository`, a `PointEventRepository`), each taking
a `PDO` instance via constructor injection and exposing intention-revealing
methods (`findByUsername`, `allWithTotals`, `addEntry`, ...).

**Rationale:** a small, fixed number of entities and queries. A query builder
would trim some boilerplate but add a dependency and an abstraction layer for
marginal benefit; a full ORM's relationship mapping, unit-of-work tracking,
and migration generation are unnecessary overhead for this schema size, and
would obscure the exact SQL running — undesirable when point totals need to
be trustworthy and auditable.

A small `Database` connection factory will centralize PDO construction with
safe defaults: `ERRMODE_EXCEPTION`, `FETCH_ASSOC`, `EMULATE_PREPARES => false`
(so real server-side prepared statements are used, not client-side
emulation).

## 6. Dependency wiring: manual, no DI container

All objects (PDO, `JwtService`, repositories, controllers, the auth
middleware) will be constructed by hand in the application's front
controller and passed to the router via a plain associative array, rather
than resolved through a service container (e.g. PHP-DI).

**Rationale:** the object graph is small and static — adding a full DI
container would be another dependency and another layer of indirection to
resolve constructor arguments, for a graph simple enough to wire in ~15 lines
of straightforward `new` calls.

## 7. Configuration: `vlucas/phpdotenv`

Environment variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`,
`DB_ROOT_PASS`, `JWT_SECRET`, `JWT_TTL`, `APP_DEBUG`) will be loaded from a
`.env` file via `Dotenv::createImmutable(...)->safeLoad()`. A `.env.example`
should be committed as the documented template, with `.env` itself
gitignored.

## 8. Local development environment

**PHP:** the built-in development server (`php -S localhost:8080 -t public`)
will be used directly — no Apache/nginx/PHP-FPM installed locally. Slim/PSR-7
apps run on it without any adaptation, and it avoids configuring a full web
server just to exercise the API during development.

**MySQL:** will run via Docker Compose (a single `mysql:8.0` service) rather
than being installed natively on the host, with the schema SQL file mounted
into `docker-entrypoint-initdb.d` so a fresh volume self-initializes.

**Rationale:** the app process (PHP) is cheap to run natively and benefits
from fast edit/reload cycles, while the database benefits from Docker's
disposability — `docker compose down -v` gives a clean slate whenever needed,
and no system-level MySQL service is left behind on the host. This mirrors
how the database would likely run in CI/production without requiring the
same for the PHP process itself.

## 9. Database table naming: `hp_` prefix

All tables will be prefixed (`hp_users`, `hp_houses`, `hp_point_events`,
`hp_refresh_tokens`) rather than left bare (`users`, `houses`, ...).

**Rationale:** namespacing convention to avoid collisions with generic table
names (`users`, `houses`) if this schema ever shares a MySQL instance/schema
with other applications.

## 10. Auth token strategy

- **Access token:** JWT, 120 second TTL, claims `sub`, `role`, `username`,
  `iat`, `exp` — short-lived and stateless, verified without a DB round-trip
  on every protected request.
- **Refresh token:** opaque random string, 72 hour TTL, stored **hashed** in
  `hp_refresh_tokens` (never plaintext), rotated on every use (old token
  revoked, new one issued).

**Rationale:** a stateless JWT refresh token can't be revoked before its
natural expiry — no real "logout". A DB-backed opaque token costs a lookup on
refresh but supports actual revocation (logout, force-expire), which matters
for a 72-hour window. Storing a hash rather than the raw token means a
database leak alone doesn't hand out valid refresh tokens.

## 11. Password storage

PHP's native `password_hash()` / `password_verify()` (bcrypt by default) —
no custom hashing scheme. Standard, avoids reinventing a solved problem.
