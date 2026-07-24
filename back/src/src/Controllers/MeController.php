<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Auth\JwtService;
use App\Auth\RefreshTokenService;
use App\Repositories\UserRepository;
use App\Support\JsonResponder;
use App\Support\ValidatesInput;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class MeController
{
    use JsonResponder;
    use ValidatesInput;

    private const DISPLAY_NAME_MAX = 190;

    public function __construct(
        private readonly UserRepository $users,
        private readonly JwtService $jwtService,
        private readonly RefreshTokenService $refreshTokens,
    ) {
    }

    public function changePassword(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();
        $currentPassword = (string) ($body['current_password'] ?? '');

        if ($currentPassword === '') {
            return $this->json($response, ['error' => 'current_password is required'], 422);
        }

        if (($error = $this->validatePassword($body['new_password'] ?? null, 'new_password')) !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $claims = $request->getAttribute('jwt');
        $userId = (int) $claims['sub'];
        $user = $this->users->findActiveById($userId);

        if ($user === null || !password_verify($currentPassword, $user['password_hash'])) {
            return $this->json($response, ['error' => 'Current password is incorrect'], 401);
        }

        $this->users->updatePassword($userId, password_hash((string) $body['new_password'], PASSWORD_DEFAULT));

        // Force other sessions to re-authenticate after a password change, then
        // immediately issue a fresh pair for *this* session — same shape as
        // AuthController::tokenPairResponse/refresh — so the device the user
        // just changed their password from isn't logged out too.
        $this->refreshTokens->revokeAllForUser($userId);

        return $this->json($response, [
            'access_token' => $this->jwtService->issue([
                'sub' => $user['id'],
                'role' => $user['role'],
                'username' => $user['username'],
                'display_name' => $user['display_name'],
            ]),
            'refresh_token' => $this->refreshTokens->issue($userId),
            'expires_in' => $this->jwtService->ttlSeconds(),
        ]);
    }

    public function changeDisplayName(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();

        if (($error = $this->validateRequiredString($body['display_name'] ?? null, 'display_name', self::DISPLAY_NAME_MAX)) !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $claims = $request->getAttribute('jwt');
        $userId = (int) $claims['sub'];
        $user = $this->users->findActiveById($userId);

        if ($user === null) {
            return $this->json($response, ['error' => 'User not found'], 404);
        }

        $displayName = trim((string) $body['display_name']);
        $this->users->updateDisplayName($userId, $displayName);

        // No refresh-token churn needed here (unlike changePassword, this isn't
        // a security-sensitive change) — just a fresh access token so the
        // client's identity claims reflect the new name immediately.
        return $this->json($response, [
            'access_token' => $this->jwtService->issue([
                'sub' => $user['id'],
                'role' => $user['role'],
                'username' => $user['username'],
                'display_name' => $displayName,
            ]),
            'expires_in' => $this->jwtService->ttlSeconds(),
        ]);
    }
}
