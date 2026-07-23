<?php

declare(strict_types=1);

namespace App\Services;

use Anthropic\Client as AnthropicClient;

/**
 * Generates the witty, in-character French comment attached to every point
 * event, via Claude. Synchronous — called inline from
 * PointEventsController::store, so it always returns a usable string (real AI
 * text or a deterministic fallback sentence) rather than ever failing the
 * request or returning null; hp_point_events.comment is NOT NULL.
 */
final class CommentGenerator
{
    private const MODEL = 'claude-sonnet-5';
    private const MAX_OUTPUT_TOKENS = 150;

    // hp_point_events.comment is VARCHAR(255); the prompt asks for <=250
    // chars, this is just a hard safety net against a runaway response.
    private const MAX_COMMENT_CHARS = 255;

    // Used only if AIPrompt.txt is missing/unreadable — generate() must never
    // throw just because a non-programmer's edit broke the file.
    private const FALLBACK_SYSTEM_PROMPT = 'Réponds en français, en une phrase ironique de 250 caractères maximum, sans guillemets.';

    public function __construct(
        private readonly AnthropicClient $client,
        private readonly string $systemPromptPath,
    ) {
    }

    public function generate(string $teacherName, string $houseName, int $points): string
    {
        $userPrompt = sprintf(
            "L'enseignant %s %s %d point%s à l'équipe %s. Rédige le commentaire.",
            $teacherName,
            $points > 0 ? 'a donné' : 'a retiré',
            abs($points),
            abs($points) > 1 ? 's' : '',
            $houseName,
        );

        try {
            $message = $this->client->messages->create(
                model: self::MODEL,
                maxTokens: self::MAX_OUTPUT_TOKENS,
                system: $this->loadSystemPrompt(),
                messages: [['role' => 'user', 'content' => $userPrompt]],
            );

            foreach ($message->content as $block) {
                if ($block->type === 'text' && trim($block->text) !== '') {
                    return mb_substr(trim($block->text), 0, self::MAX_COMMENT_CHARS);
                }
            }
        } catch (\Throwable $e) {
            error_log('CommentGenerator: Anthropic API call failed: ' . $e->getMessage());
        }

        return $this->fallbackComment($teacherName, $houseName, $points);
    }

    /**
     * Read fresh on every call (not cached) so a non-programmer's edit to
     * AIPrompt.txt takes effect on the very next request — no restart needed.
     */
    private function loadSystemPrompt(): string
    {
        $contents = @file_get_contents($this->systemPromptPath);

        if ($contents === false || trim($contents) === '') {
            error_log("CommentGenerator: could not read system prompt at {$this->systemPromptPath}, using fallback");

            return self::FALLBACK_SYSTEM_PROMPT;
        }

        return trim($contents);
    }

    private function fallbackComment(string $teacherName, string $houseName, int $points): string
    {
        return sprintf(
            '%s %s %d point%s à %s.',
            $teacherName,
            $points > 0 ? 'a ajouté' : 'a retiré',
            abs($points),
            abs($points) > 1 ? 's' : '',
            $houseName,
        );
    }
}
