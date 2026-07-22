<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\PointEventRepository;
use App\Support\JsonResponder;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class EventsController
{
    use JsonResponder;

    private const DEFAULT_PAGE_SIZE = 20;
    private const MAX_PAGE_SIZE = 100;

    public function __construct(private readonly PointEventRepository $pointEvents)
    {
    }

    public function index(Request $request, Response $response): Response
    {
        $query = $request->getQueryParams();

        $result = $this->pointEvents->listPaginated(
            $this->parsePageSize($query['page_size'] ?? null),
            $this->parseOptionalInt($query['before_id'] ?? null),
            $this->parseOptionalInt($query['teacher_id'] ?? null),
            $this->parseOptionalInt($query['house_id'] ?? null),
        );

        return $this->json($response, $result);
    }

    public function since(Request $request, Response $response): Response
    {
        $query = $request->getQueryParams();
        $sinceId = $this->parseOptionalInt($query['since_id'] ?? null);

        if ($sinceId === null) {
            return $this->json($response, ['error' => 'since_id is required'], 422);
        }

        $events = $this->pointEvents->listSince($sinceId, $this->parsePageSize($query['page_size'] ?? null));

        // Cursor to continue polling from; null when nothing new (client keeps
        // its current since_id). Oldest-first, so the last row is the newest id.
        $lastId = $events === [] ? null : (int) $events[count($events) - 1]['id'];

        return $this->json($response, ['events' => $events, 'last_id' => $lastId]);
    }

    private function parsePageSize(mixed $raw): int
    {
        if (!is_numeric($raw)) {
            return self::DEFAULT_PAGE_SIZE;
        }

        return max(1, min(self::MAX_PAGE_SIZE, (int) $raw));
    }

    private function parseOptionalInt(mixed $raw): ?int
    {
        return is_numeric($raw) ? (int) $raw : null;
    }
}
