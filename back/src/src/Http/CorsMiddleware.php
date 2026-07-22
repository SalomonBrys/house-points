<?php

declare(strict_types=1);

namespace App\Http;

use Psr\Http\Message\ResponseFactoryInterface;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Psr\Http\Server\MiddlewareInterface;
use Psr\Http\Server\RequestHandlerInterface as RequestHandler;

/**
 * Minimal CORS handling for a browser-based client on a separate origin.
 *
 * The mainstream PSR-15 CORS packages (tuupola/cors-middleware,
 * middlewares/cors) both depend on neomerx/cors-psr7, which is pinned to
 * psr/http-message ^1.0 and is therefore incompatible with the
 * psr/http-message 2.0 that Slim 4 pulls in — hence this small hand-rolled
 * middleware.
 *
 * Registered as the outermost middleware so it runs first on the way in
 * (short-circuiting preflight before routing/auth can 404 or 401 it) and
 * last on the way out (decorating error responses too).
 */
final class CorsMiddleware implements MiddlewareInterface
{
    private const ALLOWED_METHODS = 'GET, POST, PATCH, DELETE, OPTIONS';
    private const ALLOWED_HEADERS = 'Authorization, Content-Type';
    private const MAX_AGE = '600';

    /**
     * @param array<int, string> $allowedOrigins exact origins, or ['*'] to allow any
     */
    public function __construct(
        private readonly array $allowedOrigins,
        private readonly ResponseFactoryInterface $responseFactory,
    ) {
    }

    public function process(Request $request, RequestHandler $handler): Response
    {
        $isPreflight = strtoupper($request->getMethod()) === 'OPTIONS'
            && $request->hasHeader('Access-Control-Request-Method');

        // Preflight is answered here directly; it never reaches routing/auth.
        $response = $isPreflight
            ? $this->responseFactory->createResponse(204)
            : $handler->handle($request);

        $origin = $request->getHeaderLine('Origin');

        if ($origin === '' || !$this->isAllowed($origin)) {
            return $response;
        }

        return $response
            ->withHeader('Access-Control-Allow-Origin', $origin)
            ->withHeader('Vary', 'Origin')
            ->withHeader('Access-Control-Allow-Methods', self::ALLOWED_METHODS)
            ->withHeader('Access-Control-Allow-Headers', self::ALLOWED_HEADERS)
            ->withHeader('Access-Control-Max-Age', self::MAX_AGE);
    }

    private function isAllowed(string $origin): bool
    {
        return in_array('*', $this->allowedOrigins, true)
            || in_array($origin, $this->allowedOrigins, true);
    }
}
