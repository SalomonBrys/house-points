<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\UserRepository;
use App\Support\JsonResponder;
use App\Support\ValidatesInput;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;

final class UsersController
{
    use JsonResponder;
    use ValidatesInput;

    private const VALID_ROLES = ['teacher', 'admin'];

    // Match hp_users column limits.
    private const USERNAME_MAX = 190;
    private const DISPLAY_NAME_MAX = 190;

    public function __construct(private readonly UserRepository $users)
    {
    }

    public function store(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();

        $error = $this->validateRequiredString($body['username'] ?? null, 'username', self::USERNAME_MAX)
            ?? $this->validatePassword($body['password'] ?? null)
            ?? $this->validateRequiredString($body['display_name'] ?? null, 'display_name', self::DISPLAY_NAME_MAX);

        if ($error !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $role = (string) ($body['role'] ?? '');

        if (!in_array($role, self::VALID_ROLES, true)) {
            return $this->json(
                $response,
                ['error' => 'role must be one of: ' . implode(', ', self::VALID_ROLES)],
                422,
            );
        }

        $username = trim((string) $body['username']);

        if ($this->users->usernameExists($username)) {
            return $this->json($response, ['error' => 'Username already taken'], 409);
        }

        $id = $this->users->create(
            $username,
            password_hash((string) $body['password'], PASSWORD_DEFAULT),
            $role,
            trim((string) $body['display_name']),
        );

        return $this->json($response, ['id' => $id], 201);
    }

    /**
     * @param array<string, string> $args
     */
    public function destroy(Request $request, Response $response, array $args): Response
    {
        $id = (int) $args['id'];

        if (!$this->users->existsById($id)) {
            return $this->json($response, ['error' => 'User not found'], 404);
        }

        $this->users->deactivate($id);

        return $response->withStatus(204);
    }
}
