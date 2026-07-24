<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\TeamRepository;
use App\Support\JsonResponder;
use App\Support\ValidatesInput;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class TeamsController
{
    use JsonResponder;
    use ValidatesInput;

    // Matches hp_teams.name VARCHAR(100).
    private const NAME_MAX = 100;

    public function __construct(private readonly TeamRepository $teams)
    {
    }

    public function index(Request $request, Response $response): Response
    {
        return $this->json($response, $this->teams->allActiveWithTotals());
    }

    public function store(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();

        if (($error = $this->validateRequiredString($body['name'] ?? null, 'name', self::NAME_MAX)) !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $id = $this->teams->create(trim((string) $body['name']));

        return $this->json($response, ['id' => $id], 201);
    }

    /**
     * @param array<string, string> $args
     */
    public function destroy(Request $request, Response $response, array $args): Response
    {
        $id = (int) $args['id'];

        if (!$this->teams->existsById($id)) {
            return $this->json($response, ['error' => 'Team not found'], 404);
        }

        $this->teams->deactivate($id);

        return $response->withStatus(204);
    }
}
