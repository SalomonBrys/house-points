<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\HouseRepository;
use App\Repositories\PointEventRepository;
use App\Support\JsonResponder;
use App\Support\ValidatesInput;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class PointEventsController
{
    use JsonResponder;
    use ValidatesInput;

    // Matches hp_point_events.comment VARCHAR(255).
    private const COMMENT_MAX = 255;

    public function __construct(
        private readonly PointEventRepository $pointEvents,
        private readonly HouseRepository $houses,
    ) {
    }

    /**
     * @param array<string, string> $args
     */
    public function store(Request $request, Response $response, array $args): Response
    {
        $houseId = (int) $args['houseId'];

        if ($this->houses->findActive($houseId) === null) {
            return $this->json($response, ['error' => 'House not found'], 404);
        }

        $body = (array) $request->getParsedBody();
        $points = $this->parsePoints($body['points'] ?? null);

        if ($points === null) {
            return $this->json($response, ['error' => 'points must be a non-zero integer'], 422);
        }

        if (($error = $this->validateOptionalString($body['comment'] ?? null, 'comment', self::COMMENT_MAX)) !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $comment = isset($body['comment']) && trim((string) $body['comment']) !== ''
            ? trim((string) $body['comment'])
            : null;

        $claims = $request->getAttribute('jwt');
        $id = $this->pointEvents->create($houseId, (int) $claims['sub'], $points, $comment);

        return $this->json($response, ['id' => $id], 201);
    }

    /**
     * @param array<string, string> $args
     */
    public function destroy(Request $request, Response $response, array $args): Response
    {
        $eventId = (int) $args['id'];
        $event = $this->pointEvents->find($eventId);

        if ($event === null) {
            return $this->json($response, ['error' => 'Event not found'], 404);
        }

        $claims = $request->getAttribute('jwt');
        $isOwner = $event['teacher_id'] === (int) $claims['sub'];
        $isAdmin = $claims['role'] === 'admin';

        if (!$isOwner && !$isAdmin) {
            return $this->json($response, ['error' => 'Forbidden'], 403);
        }

        $this->pointEvents->delete($eventId);

        return $response->withStatus(204);
    }

    private function parsePoints(mixed $points): ?int
    {
        if (is_int($points)) {
            return $points !== 0 ? $points : null;
        }

        if (is_string($points) && $points !== '' && $points !== '-' && ctype_digit(ltrim($points, '-'))) {
            $value = (int) $points;

            return $value !== 0 ? $value : null;
        }

        return null;
    }
}
