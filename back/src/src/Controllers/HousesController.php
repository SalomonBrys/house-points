<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\HouseRepository;
use App\Support\JsonResponder;
use App\Support\ValidatesInput;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class HousesController
{
    use JsonResponder;
    use ValidatesInput;

    // Matches hp_houses.name VARCHAR(100).
    private const NAME_MAX = 100;

    public function __construct(private readonly HouseRepository $houses)
    {
    }

    public function index(Request $request, Response $response): Response
    {
        return $this->json($response, $this->houses->allActiveWithTotals());
    }

    public function store(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();

        if (($error = $this->validateRequiredString($body['name'] ?? null, 'name', self::NAME_MAX)) !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $id = $this->houses->create(trim((string) $body['name']));

        return $this->json($response, ['id' => $id], 201);
    }

    /**
     * @param array<string, string> $args
     */
    public function destroy(Request $request, Response $response, array $args): Response
    {
        $id = (int) $args['id'];

        if (!$this->houses->existsById($id)) {
            return $this->json($response, ['error' => 'House not found'], 404);
        }

        $this->houses->deactivate($id);

        return $response->withStatus(204);
    }
}
