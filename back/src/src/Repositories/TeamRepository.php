<?php

declare(strict_types=1);

namespace App\Repositories;

use PDO;

final class TeamRepository
{
    public function __construct(private readonly PDO $pdo)
    {
    }

    /**
     * @return array<int, array{id: int, name: string, total_points: int}>
     */
    public function allActiveWithTotals(): array
    {
        $stmt = $this->pdo->query(
            'SELECT t.id, t.name, COALESCE(SUM(p.points), 0) AS total_points
             FROM teampoints_teams t
             LEFT JOIN teampoints_point_events p ON p.team_id = t.id
             WHERE t.active = 1
             GROUP BY t.id, t.name
             ORDER BY total_points DESC'
        );

        // SUM()/COALESCE() yields a DECIMAL, which PDO always returns as a
        // string — cast so the JSON stays numeric for the typed client.
        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'name' => $row['name'],
            'total_points' => (int) $row['total_points'],
        ], $stmt->fetchAll());
    }

    /**
     * @return array{id: int, name: string}|null
     */
    public function findActive(int $id): ?array
    {
        $stmt = $this->pdo->prepare('SELECT id, name FROM teampoints_teams WHERE id = :id AND active = 1 LIMIT 1');
        $stmt->execute(['id' => $id]);

        $team = $stmt->fetch();

        return $team === false ? null : $team;
    }

    public function existsById(int $id): bool
    {
        $stmt = $this->pdo->prepare('SELECT 1 FROM teampoints_teams WHERE id = :id LIMIT 1');
        $stmt->execute(['id' => $id]);

        return $stmt->fetch() !== false;
    }

    public function create(string $name): int
    {
        $stmt = $this->pdo->prepare('INSERT INTO teampoints_teams (name, active) VALUES (:name, 1)');
        $stmt->execute(['name' => $name]);

        return (int) $this->pdo->lastInsertId();
    }

    public function deactivate(int $id): void
    {
        $stmt = $this->pdo->prepare('UPDATE teampoints_teams SET active = 0 WHERE id = :id');
        $stmt->execute(['id' => $id]);
    }
}
