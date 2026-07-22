<?php

declare(strict_types=1);

namespace App\Auth;

use Firebase\JWT\JWT;
use Firebase\JWT\Key;

final class JwtService
{
    private const ALGO = 'HS256';

    public function __construct(
        private readonly string $secret,
        private readonly int $ttl = 900,
    ) {
    }

    public function ttlSeconds(): int
    {
        return $this->ttl;
    }

    /**
     * @param array<string, mixed> $claims
     */
    public function issue(array $claims): string
    {
        $now = time();

        $payload = array_merge($claims, [
            'iat' => $now,
            'exp' => $now + $this->ttl,
        ]);

        return JWT::encode($payload, $this->secret, self::ALGO);
    }

    /**
     * @return array<string, mixed>
     */
    public function verify(string $token): array
    {
        $decoded = JWT::decode($token, new Key($this->secret, self::ALGO));

        return (array) $decoded;
    }
}
