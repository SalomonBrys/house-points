<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Auth\JwtService;
use App\Auth\RefreshTokenService;
use App\Repositories\UserRepository;
use App\Support\JsonResponder;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class AuthController
{
    use JsonResponder;

    public function __construct(
        private readonly UserRepository $users,
        private readonly JwtService $jwtService,
        private readonly RefreshTokenService $refreshTokens,
    ) {
    }

    public function login(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();
        $username = trim((string) ($body['username'] ?? ''));
        $password = (string) ($body['password'] ?? '');

        if ($username === '' || $password === '') {
            return $this->json($response, ['error' => 'Username and password are required'], 422);
        }

        $user = $this->users->findActiveByUsername($username);

        if ($user === null || !password_verify($password, $user['password_hash'])) {
            return $this->json($response, ['error' => 'Invalid credentials'], 401);
        }

        return $this->tokenPairResponse($response, $user);
    }

    public function refresh(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();
        $refreshToken = (string) ($body['refresh_token'] ?? '');

        if ($refreshToken === '') {
            return $this->json($response, ['error' => 'refresh_token is required'], 422);
        }

        $rotated = $this->refreshTokens->rotate($refreshToken);

        if ($rotated === null) {
            return $this->json($response, ['error' => 'Invalid or expired refresh token'], 401);
        }

        $user = $this->users->findActiveById($rotated['user_id']);

        if ($user === null) {
            return $this->json($response, ['error' => 'Invalid or expired refresh token'], 401);
        }

        return $this->json($response, [
            'access_token' => $this->issueAccessToken($user),
            'refresh_token' => $rotated['token'],
            'expires_in' => $this->jwtService->ttlSeconds(),
        ]);
    }

    public function logout(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();
        $refreshToken = (string) ($body['refresh_token'] ?? '');

        if ($refreshToken !== '') {
            $this->refreshTokens->revoke($refreshToken);
        }

        return $response->withStatus(204);
    }

    /**
     * @param array{id: int, username: string, role: string, display_name: string} $user
     */
    private function tokenPairResponse(Response $response, array $user): Response
    {
        return $this->json($response, [
            'access_token' => $this->issueAccessToken($user),
            'refresh_token' => $this->refreshTokens->issue($user['id']),
            'expires_in' => $this->jwtService->ttlSeconds(),
        ]);
    }

    /**
     * @param array{id: int, username: string, role: string, display_name: string} $user
     */
    private function issueAccessToken(array $user): string
    {
        return $this->jwtService->issue([
            'sub' => $user['id'],
            'role' => $user['role'],
            'username' => $user['username'],
            'display_name' => $user['display_name'],
        ]);
    }
}
