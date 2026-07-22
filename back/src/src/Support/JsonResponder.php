<?php

declare(strict_types=1);

namespace App\Support;

use Psr\Http\Message\ResponseInterface as Response;

trait JsonResponder
{
    private function json(Response $response, mixed $data, int $status = 200): Response
    {
        // THROW_ON_ERROR turns an encoding failure into a caught 500 (logged)
        // instead of a silent empty 200 body; SUBSTITUTE keeps stray invalid
        // UTF-8 in stored data (e.g. a comment) from breaking the response.
        $json = json_encode($data, JSON_THROW_ON_ERROR | JSON_INVALID_UTF8_SUBSTITUTE);
        $response->getBody()->write($json);

        return $response
            ->withHeader('Content-Type', 'application/json')
            ->withStatus($status);
    }
}
