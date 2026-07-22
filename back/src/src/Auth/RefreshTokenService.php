<?php

declare(strict_types=1);

namespace App\Auth;

use App\Repositories\RefreshTokenRepository;
use DateTimeImmutable;

final class RefreshTokenService
{
    private const TTL_SECONDS = 72 * 60 * 60;

    public function __construct(private readonly RefreshTokenRepository $refreshTokens)
    {
    }

    public function issue(int $userId): string
    {
        $token = bin2hex(random_bytes(32));

        $this->refreshTokens->create(
            $userId,
            $this->hash($token),
            (new DateTimeImmutable())->modify('+' . self::TTL_SECONDS . ' seconds'),
        );

        return $token;
    }

    /**
     * Atomically consumes the given refresh token and issues a new one for the
     * same user. Concurrent calls with the same token: exactly one succeeds,
     * the rest get null.
     *
     * @return array{user_id: int, token: string}|null null if the token is invalid, expired, or already used
     */
    public function rotate(string $token): ?array
    {
        $userId = $this->refreshTokens->consumeActiveByHash($this->hash($token));

        if ($userId === null) {
            return null;
        }

        return [
            'user_id' => $userId,
            'token' => $this->issue($userId),
        ];
    }

    public function revoke(string $token): void
    {
        $this->refreshTokens->revokeByHash($this->hash($token));
    }

    /**
     * Revokes all of a user's active refresh tokens (e.g. after a password change).
     */
    public function revokeAllForUser(int $userId): void
    {
        $this->refreshTokens->revokeAllForUser($userId);
    }

    private function hash(string $token): string
    {
        return hash('sha256', $token);
    }
}
