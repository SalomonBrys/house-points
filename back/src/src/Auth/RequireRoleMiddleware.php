<?php

declare(strict_types=1);

namespace App\Auth;

use Psr\Http\Message\ResponseFactoryInterface;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface as RequestHandler;

final class RequireRoleMiddleware implements MiddlewareInterface
{
    /**
     * @param array<int, string> $allowedRoles
     */
    public function __construct(
        private readonly array $allowedRoles,
        private readonly ResponseFactoryInterface $responseFactory,
    ) {
    }

    public function process(Request $request, RequestHandler $handler): Response
    {
        $claims = $request->getAttribute('jwt');
        $role = $claims['role'] ?? null;

        if (!in_array($role, $this->allowedRoles, true)) {
            $response = $this->responseFactory->createResponse(403);
            $response->getBody()->write(json_encode(['error' => 'Forbidden'], JSON_THROW_ON_ERROR));

            return $response->withHeader('Content-Type', 'application/json');
        }

        return $handler->handle($request);
    }
}
