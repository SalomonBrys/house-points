<?php

declare(strict_types=1);

use Slim\App;
use Slim\Exception\HttpNotFoundException;
use Slim\Routing\RouteCollectorProxy;

const STATIC_MIME_TYPES = [
    'html' => 'text/html; charset=utf-8',
    'css' => 'text/css; charset=utf-8',
    'js' => 'text/javascript; charset=utf-8',
    // The compiled Compose Multiplatform front end (front/ARCHITECTURE.md §2)
    // serves these two: WebAssembly.instantiateStreaming specifically requires
    // an exact "application/wasm" Content-Type, or it fails outright.
    'wasm' => 'application/wasm',
    'mjs' => 'text/javascript; charset=utf-8',
    'json' => 'application/json',
    'svg' => 'image/svg+xml',
    'png' => 'image/png',
    'ico' => 'image/x-icon',
];

/**
 * @param array{
 *     auth: App\Controllers\AuthController,
 *     me: App\Controllers\MeController,
 *     houses: App\Controllers\HousesController,
 *     pointEvents: App\Controllers\PointEventsController,
 *     events: App\Controllers\EventsController,
 *     users: App\Controllers\UsersController,
 *     jwtAuth: App\Auth\JwtAuthMiddleware,
 *     requireAdmin: App\Auth\RequireRoleMiddleware,
 * } $deps
 */
return function (App $app, array $deps): void {
    // --- Public (no auth) ---
    $app->post('/api/auth/login', [$deps['auth'], 'login']);
    $app->post('/api/auth/refresh', [$deps['auth'], 'refresh']);
    $app->post('/api/auth/logout', [$deps['auth'], 'logout']);

    $app->get('/api/houses', [$deps['houses'], 'index']);
    $app->get('/api/events', [$deps['events'], 'index']);
    $app->get('/api/events/since', [$deps['events'], 'since']);

    // --- Any authenticated user (teacher or admin) ---
    $app->group('/api', function (RouteCollectorProxy $group) use ($deps) {
        $group->post('/houses/{houseId}/points', [$deps['pointEvents'], 'store']);
        $group->delete('/events/{id}', [$deps['pointEvents'], 'destroy']);
        $group->patch('/me/password', [$deps['me'], 'changePassword']);
    })->add($deps['jwtAuth']);

    // --- Admin only ---
    // Middleware added last runs first: jwtAuth must verify/attach claims
    // before requireAdmin reads the role off them.
    $app->group('/api', function (RouteCollectorProxy $group) use ($deps) {
        $group->post('/houses', [$deps['houses'], 'store']);
        $group->delete('/houses/{id}', [$deps['houses'], 'destroy']);
        $group->get('/users', [$deps['users'], 'index']);
        $group->post('/users', [$deps['users'], 'store']);
        $group->delete('/users/{id}', [$deps['users'], 'destroy']);
    })->add($deps['requireAdmin'])->add($deps['jwtAuth']);

    // --- Static files (everything else) ---
    // The PHP built-in server always forwards non-file requests to
    // public/index.php, so every "real" browser asset request (the page
    // itself, its CSS, its JS, ...) ends up here rather than being served
    // directly — this route is what actually serves it, reading from
    // static/ (kept outside public/ so it's never served as a raw file
    // bypassing this route). Registered last so it never shadows /api/*.
    $app->get('/{path:.*}', function ($request, $response, array $args) {
        $requestedPath = $args['path'] === '' ? 'index.html' : $args['path'];

        // /api/* is handled by the routes above; this guards the same
        // invariant explicitly in case route registration order ever changes.
        if (str_starts_with($requestedPath, 'api/')) {
            throw new HttpNotFoundException($request);
        }

        $staticDir = realpath(__DIR__ . '/static');
        $filePath = realpath($staticDir . '/' . $requestedPath);

        // realpath() resolves "..", so this also blocks path traversal
        // outside static/.
        if (
            $filePath === false
            || !str_starts_with($filePath, $staticDir . DIRECTORY_SEPARATOR)
            || !is_file($filePath)
        ) {
            throw new HttpNotFoundException($request);
        }

        $extension = strtolower(pathinfo($filePath, PATHINFO_EXTENSION));
        $contentType = STATIC_MIME_TYPES[$extension] ?? 'application/octet-stream';

        $response->getBody()->write((string) file_get_contents($filePath));

        return $response->withHeader('Content-Type', $contentType);
    });
};
