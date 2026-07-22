<?php

declare(strict_types=1);

use Slim\App;
use Slim\Routing\RouteCollectorProxy;

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
        $group->post('/users', [$deps['users'], 'store']);
        $group->delete('/users/{id}', [$deps['users'], 'destroy']);
    })->add($deps['requireAdmin'])->add($deps['jwtAuth']);
};
