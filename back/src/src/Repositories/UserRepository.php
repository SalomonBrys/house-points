<?php

declare(strict_types=1);

namespace App\Repositories;

use PDO;

final class UserRepository
{
    public function __construct(private readonly PDO $pdo)
    {
    }

    /**
     * @return array{id: int, username: string, password_hash: string, role: string, display_name: string}|null
     */
    public function findActiveByUsername(string $username): ?array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, username, password_hash, role, display_name
             FROM hp_users
             WHERE username = :username AND active = 1
             LIMIT 1'
        );
        $stmt->execute(['username' => $username]);

        $user = $stmt->fetch();

        return $user === false ? null : $user;
    }

    /**
     * @return array{id: int, username: string, password_hash: string, role: string, display_name: string}|null
     */
    public function findActiveById(int $id): ?array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, username, password_hash, role, display_name
             FROM hp_users
             WHERE id = :id AND active = 1
             LIMIT 1'
        );
        $stmt->execute(['id' => $id]);

        $user = $stmt->fetch();

        return $user === false ? null : $user;
    }

    public function usernameExists(string $username): bool
    {
        $stmt = $this->pdo->prepare('SELECT 1 FROM hp_users WHERE username = :username LIMIT 1');
        $stmt->execute(['username' => $username]);

        return $stmt->fetch() !== false;
    }

    public function existsById(int $id): bool
    {
        $stmt = $this->pdo->prepare('SELECT 1 FROM hp_users WHERE id = :id LIMIT 1');
        $stmt->execute(['id' => $id]);

        return $stmt->fetch() !== false;
    }

    public function create(string $username, string $passwordHash, string $role, string $displayName): int
    {
        $stmt = $this->pdo->prepare(
            'INSERT INTO hp_users (username, password_hash, role, display_name, active)
             VALUES (:username, :password_hash, :role, :display_name, 1)'
        );
        $stmt->execute([
            'username' => $username,
            'password_hash' => $passwordHash,
            'role' => $role,
            'display_name' => $displayName,
        ]);

        return (int) $this->pdo->lastInsertId();
    }

    public function deactivate(int $id): void
    {
        $stmt = $this->pdo->prepare('UPDATE hp_users SET active = 0 WHERE id = :id');
        $stmt->execute(['id' => $id]);
    }

    public function updatePassword(int $id, string $passwordHash): void
    {
        $stmt = $this->pdo->prepare('UPDATE hp_users SET password_hash = :password_hash WHERE id = :id');
        $stmt->execute(['password_hash' => $passwordHash, 'id' => $id]);
    }
}
