<?php

declare(strict_types=1);

namespace App\Support;

/**
 * Small input-validation helpers returning a human-readable error string (422
 * payload) or null when valid. Length limits are expressed in characters to
 * match MySQL's VARCHAR(n) semantics, so over-long input is rejected as 422
 * rather than overflowing a column and surfacing as a 500.
 */
trait ValidatesInput
{
    private function validateRequiredString(mixed $value, string $field, int $maxChars, int $minChars = 1): ?string
    {
        if (!is_string($value) || trim($value) === '') {
            return "$field is required";
        }

        $length = mb_strlen(trim($value));

        if ($length < $minChars) {
            return "$field must be at least $minChars characters";
        }

        if ($length > $maxChars) {
            return "$field must be at most $maxChars characters";
        }

        return null;
    }

    /**
     * Passwords are not trimmed (surrounding whitespace may be intentional) and
     * are capped at 72 bytes, beyond which bcrypt silently truncates. Pass
     * minChars: 0 to skip the minimum-length check (e.g. teacher accounts,
     * where this small project has no password-strength requirement).
     */
    private function validatePassword(mixed $value, string $field = 'password', int $minChars = 8): ?string
    {
        if (!is_string($value) || $value === '') {
            return "$field is required";
        }

        if ($minChars > 0 && mb_strlen($value) < $minChars) {
            return "$field must be at least $minChars characters";
        }

        if (strlen($value) > 72) {
            return "$field must be at most 72 bytes";
        }

        return null;
    }
}
