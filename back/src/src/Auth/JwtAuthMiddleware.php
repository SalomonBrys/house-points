<?php

declare(strict_types=1);

namespace App\Auth;

use Firebase\JWT\ExpiredException;
use Psr\Http\Message\ResponseFactoryInterface;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface as RequestHandler;
use Throwable;

final class JwtAuthMiddleware implements MiddlewareInterface
{
    public function __construct(
        private readonly JwtService $jwtService,
        private readonly ResponseFactoryInterface $responseFactory,
    ) {
    }

    public function process(Request $request, RequestHandler $handler): Response
    {
        $header = $request->getHeaderLine('Authorization');

        if (!preg_match('/^Bearer\s+(.+)$/i', $header, $matches)) {
            return $this->unauthorized('Missing bearer token');
        }

        try {
            $claims = $this->jwtService->verify($matches[1]);
        } catch (ExpiredException) {
            return $this->unauthorized('Token expired');
        } catch (Throwable) {
            return $this->unauthorized('Invalid token');
        }

        return $handler->handle($request->withAttribute('jwt', $claims));
    }

    private function unauthorized(string $message): Response
    {
        $response = $this->responseFactory->createResponse(401);
        $response->getBody()->write(json_encode(['error' => $message], JSON_THROW_ON_ERROR));

        return $response->withHeader('Content-Type', 'application/json');
    }
}
