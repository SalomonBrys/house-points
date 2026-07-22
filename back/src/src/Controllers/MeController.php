<?php

declare(strict_types=1);

namespace App\Controllers;

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

    public function __construct(
        private readonly UserRepository $users,
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

        // Force other sessions to re-authenticate after a password change.
        $this->refreshTokens->revokeAllForUser($userId);

        return $response->withStatus(204);
    }
}
