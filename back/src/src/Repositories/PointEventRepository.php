<?php

declare(strict_types=1);

namespace App\Repositories;

use PDO;

final class PointEventRepository
{
    public function __construct(private readonly PDO $pdo)
    {
    }

    public function create(int $houseId, int $teacherId, int $points, ?string $comment): int
    {
        $stmt = $this->pdo->prepare(
            'INSERT INTO hp_point_events (house_id, teacher_id, points, comment)
             VALUES (:house_id, :teacher_id, :points, :comment)'
        );
        $stmt->execute([
            'house_id' => $houseId,
            'teacher_id' => $teacherId,
            'points' => $points,
            'comment' => $comment,
        ]);

        return (int) $this->pdo->lastInsertId();
    }

    /**
     * @return array{id: int, house_id: int, teacher_id: int, points: int, comment: ?string, created_at: string}|null
     */
    public function find(int $id): ?array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, house_id, teacher_id, points, comment, created_at
             FROM hp_point_events
             WHERE id = :id
             LIMIT 1'
        );
        $stmt->execute(['id' => $id]);

        $event = $stmt->fetch();

        return $event === false ? null : $event;
    }

    public function delete(int $id): void
    {
        $stmt = $this->pdo->prepare('DELETE FROM hp_point_events WHERE id = :id');
        $stmt->execute(['id' => $id]);
    }

    /**
     * Newest-first, keyset-paginated listing.
     *
     * @return array{events: array<int, array<string, mixed>>, next_id: int|null}
     */
    public function listPaginated(int $pageSize, ?int $beforeId, ?int $teacherId, ?int $houseId): array
    {
        $conditions = [];
        $params = [];

        if ($beforeId !== null) {
            $conditions[] = 'id < :before_id';
            $params['before_id'] = $beforeId;
        }

        if ($teacherId !== null) {
            $conditions[] = 'teacher_id = :teacher_id';
            $params['teacher_id'] = $teacherId;
        }

        if ($houseId !== null) {
            $conditions[] = 'house_id = :house_id';
            $params['house_id'] = $houseId;
        }

        $where = $conditions === [] ? '' : 'WHERE ' . implode(' AND ', $conditions);

        // Fetch one extra row to know whether a next page exists.
        $stmt = $this->pdo->prepare(
            "SELECT id, house_id, teacher_id, points, comment, created_at
             FROM hp_point_events
             $where
             ORDER BY id DESC
             LIMIT " . ($pageSize + 1)
        );
        $stmt->execute($params);
        $rows = $stmt->fetchAll();

        $hasMore = count($rows) > $pageSize;
        $rows = array_slice($rows, 0, $pageSize);
        $nextId = $hasMore && $rows !== [] ? (int) $rows[count($rows) - 1]['id'] : null;

        return ['events' => $rows, 'next_id' => $nextId];
    }

    /**
     * Oldest-first listing of everything strictly after $sinceId, for polling clients.
     *
     * @return array<int, array<string, mixed>>
     */
    public function listSince(int $sinceId, int $pageSize): array
    {
        $stmt = $this->pdo->prepare(
            'SELECT id, house_id, teacher_id, points, comment, created_at
             FROM hp_point_events
             WHERE id > :since_id
             ORDER BY id ASC
             LIMIT ' . $pageSize
        );
        $stmt->execute(['since_id' => $sinceId]);

        return $stmt->fetchAll();
    }
}
