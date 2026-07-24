<?php

declare(strict_types=1);

namespace App\Services;

use Anthropic\Client as AnthropicClient;
use App\Repositories\PointEventRepository;

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

    // Ranges 0.0-1.0 (Anthropic API); 1.0 is the max, favoring varied,
    // creative phrasing over safer/more predictable comments.
    private const TEMPERATURE = 1.0;

    // hp_point_events.comment is VARCHAR(255); the prompt asks for <=250
    // chars, this is just a hard safety net against a runaway response.
    private const MAX_COMMENT_CHARS = 255;

    // How many prior comments to show the model so it can vary its style
    // instead of repeating itself (AIPrompt.txt tells it to expect these).
    private const RECENT_COMMENTS_COUNT = 50;

    // Used only if AIPrompt.txt is missing/unreadable — generate() must never
    // throw just because a non-programmer's edit broke the file.
    private const FALLBACK_SYSTEM_PROMPT = 'Réponds en français, en une phrase ironique de 250 caractères maximum, sans guillemets.';

    public function __construct(
        private readonly AnthropicClient $client,
        private readonly string $systemPromptPath,
        private readonly PointEventRepository $pointEvents,
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
                system: $this->loadSystemPrompt() . $this->recentCommentsBlock(),
                messages: [['role' => 'user', 'content' => $userPrompt]],
                temperature: self::TEMPERATURE,
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

    /**
     * The [RECENT_COMMENTS_COUNT] latest comments, formatted as a bulleted
     * addendum to the system prompt so the model can see what it already
     * wrote and vary its style. Best-effort: a DB error or an empty history
     * (e.g. the very first event) just yields no addendum, never a failure —
     * generate() must still return a usable comment either way.
     */
    private function recentCommentsBlock(): string
    {
        try {
            $comments = $this->pointEvents->latestComments(self::RECENT_COMMENTS_COUNT);
        } catch (\Throwable $e) {
            error_log('CommentGenerator: could not load recent comments: ' . $e->getMessage());

            return '';
        }

        if ($comments === []) {
            return '';
        }

        return "\n\nTes précédents commentaires :\n"
            . implode("\n", array_map(static fn (string $c): string => '- ' . $c, $comments))
            . "\n\nRéponds uniquement par une seule phrase, en français, de 250 caractères maximum. N'utilise pas de guillemets, ne réponds rien d'autre que cette phrase.";
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
