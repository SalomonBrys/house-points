<?php

declare(strict_types=1);

namespace App\Repositories;

use DateTimeImmutable;
use PDO;

final class RefreshTokenRepository
{
    public function __construct(private readonly PDO $pdo)
    {
    }

    public function create(int $userId, string $tokenHash, DateTimeImmutable $expiresAt): void
    {
        $stmt = $this->pdo->prepare(
            'INSERT INTO hp_refresh_tokens (user_id, token_hash, expires_at)
             VALUES (:user_id, :token_hash, :expires_at)'
        );
        $stmt->execute([
            'user_id' => $userId,
            'token_hash' => $tokenHash,
            'expires_at' => $expiresAt->format('Y-m-d H:i:s'),
        ]);
    }

    /**
     * Atomically revokes an active (unrevoked, unexpired) token and returns its
     * owner. The conditional UPDATE plus InnoDB's row lock guarantees that when
     * two requests race with the same token, exactly one gets rowCount() === 1;
     * the loser gets 0 and null. user_id is immutable, so reading it after
     * winning the UPDATE is safe.
     *
     * @return int|null owner user id, or null if the token was invalid, expired, or already consumed
     */
    public function consumeActiveByHash(string $tokenHash): ?int
    {
        $update = $this->pdo->prepare(
            'UPDATE hp_refresh_tokens
             SET revoked_at = NOW()
             WHERE token_hash = :token_hash
               AND revoked_at IS NULL
               AND expires_at > NOW()'
        );
        $update->execute(['token_hash' => $tokenHash]);

        if ($update->rowCount() !== 1) {
            return null;
        }

        $select = $this->pdo->prepare('SELECT user_id FROM hp_refresh_tokens WHERE token_hash = :token_hash LIMIT 1');
        $select->execute(['token_hash' => $tokenHash]);

        $row = $select->fetch();

        return $row === false ? null : (int) $row['user_id'];
    }

    /**
     * Idempotently revokes a token by hash (used on logout). Missing or
     * already-revoked tokens are a no-op.
     */
    public function revokeByHash(string $tokenHash): void
    {
        $stmt = $this->pdo->prepare(
            'UPDATE hp_refresh_tokens
             SET revoked_at = NOW()
             WHERE token_hash = :token_hash AND revoked_at IS NULL'
        );
        $stmt->execute(['token_hash' => $tokenHash]);
    }

    /**
     * Revokes every still-active token for a user (used on password change to
     * force other sessions to re-authenticate).
     */
    public function revokeAllForUser(int $userId): void
    {
        $stmt = $this->pdo->prepare(
            'UPDATE hp_refresh_tokens
             SET revoked_at = NOW()
             WHERE user_id = :user_id AND revoked_at IS NULL'
        );
        $stmt->execute(['user_id' => $userId]);
    }
}
