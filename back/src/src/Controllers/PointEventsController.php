<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\HouseRepository;
use App\Repositories\PointEventRepository;
use App\Repositories\UserRepository;
use App\Services\CommentGenerator;
use App\Support\JsonResponder;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class PointEventsController
{
    use JsonResponder;

    public function __construct(
        private readonly PointEventRepository $pointEvents,
        private readonly HouseRepository $houses,
        private readonly UserRepository $users,
        private readonly CommentGenerator $commentGenerator,
    ) {
    }

    /**
     * @param array<string, string> $args
     */
    public function store(Request $request, Response $response, array $args): Response
    {
        $houseId = (int) $args['houseId'];
        $house = $this->houses->findActive($houseId);

        if ($house === null) {
            return $this->json($response, ['error' => 'House not found'], 404);
        }

        $body = (array) $request->getParsedBody();
        $points = $this->parsePoints($body['points'] ?? null);

        if ($points === null) {
            return $this->json($response, ['error' => 'points must be a non-zero integer'], 422);
        }

        $claims = $request->getAttribute('jwt');
        $teacherId = (int) $claims['sub'];
        $teacher = $this->users->findActiveById($teacherId);
        $teacherName = $teacher['display_name'] ?? ($claims['username'] ?? 'Un enseignant');

        $comment = $this->commentGenerator->generate($teacherName, $house['name'], $points);

        $id = $this->pointEvents->create($houseId, $teacherId, $points, $comment);

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
