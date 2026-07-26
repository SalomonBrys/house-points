<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Repositories\TeamRepository;
use App\Support\JsonResponder;
use App\Support\ValidatesInput;
use Psr\Http\Message\ResponseInterface as Response;
use Psr\Http\Message\ServerRequestInterface as Request;
use Psr\Http\Message\UploadedFileInterface;

final class TeamsController
{
    use JsonResponder;
    use ValidatesInput;

    // Matches teampoints_teams.name VARCHAR(100).
    private const NAME_MAX = 100;

    private const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    // Sniffed via finfo against the file's actual bytes — the client-supplied
    // media type/extension is never trusted.
    private const ALLOWED_IMAGE_TYPES = [
        'image/png' => 'png',
        'image/jpeg' => 'jpg',
        'image/webp' => 'webp',
    ];

    public function __construct(
        private readonly TeamRepository $teams,
        private readonly string $uploadsDir,
    ) {
    }

    public function index(Request $request, Response $response): Response
    {
        return $this->json($response, $this->teams->allActiveWithTotals());
    }

    public function store(Request $request, Response $response): Response
    {
        $body = (array) $request->getParsedBody();

        if (($error = $this->validateRequiredString($body['name'] ?? null, 'name', self::NAME_MAX)) !== null) {
            return $this->json($response, ['error' => $error], 422);
        }

        $id = $this->teams->create(trim((string) $body['name']));

        return $this->json($response, ['id' => $id], 201);
    }

    /**
     * @param array<string, string> $args
     */
    public function destroy(Request $request, Response $response, array $args): Response
    {
        $id = (int) $args['id'];

        if (!$this->teams->existsById($id)) {
            return $this->json($response, ['error' => 'Team not found'], 404);
        }

        $this->teams->deactivate($id);

        return $response->withStatus(204);
    }

    /**
     * @param array<string, string> $args
     */
    public function uploadImage(Request $request, Response $response, array $args): Response
    {
        $id = (int) $args['id'];

        if (!$this->teams->existsById($id)) {
            return $this->json($response, ['error' => 'Team not found'], 404);
        }

        $uploadedFiles = $request->getUploadedFiles();
        $image = $uploadedFiles['image'] ?? null;

        if (!$image instanceof UploadedFileInterface) {
            return $this->json($response, ['error' => 'image is required'], 422);
        }

        // A file exceeding php.ini's upload_max_filesize/post_max_size never
        // reaches PHP as UPLOAD_ERR_OK — it's already too big before our own
        // MAX_IMAGE_BYTES check below ever runs, so surface the same "too
        // big" message for those two error codes specifically.
        if (in_array($image->getError(), [UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE], true)) {
            return $this->json($response, ['error' => 'image must be at most 2 MB'], 422);
        }

        if ($image->getError() !== UPLOAD_ERR_OK) {
            return $this->json($response, ['error' => 'image is required'], 422);
        }

        if ($image->getSize() > self::MAX_IMAGE_BYTES) {
            return $this->json($response, ['error' => 'image must be at most 2 MB'], 422);
        }

        // The client-declared media type/filename (getClientMediaType(),
        // getClientFilename()) is never trusted — sniff the real content
        // instead so a renamed/relabeled file can't slip past validation.
        $tmpPath = $image->getStream()->getMetadata('uri');
        $mimeType = is_string($tmpPath) ? (new \finfo(FILEINFO_MIME_TYPE))->file($tmpPath) : false;
        $extension = self::ALLOWED_IMAGE_TYPES[$mimeType] ?? null;

        if ($extension === null) {
            return $this->json($response, ['error' => 'image must be a PNG, JPG, or WebP file'], 422);
        }

        if (!is_dir($this->uploadsDir) && !mkdir($this->uploadsDir, 0755, true) && !is_dir($this->uploadsDir)) {
            return $this->json($response, ['error' => 'Could not store image'], 500);
        }

        $filename = bin2hex(random_bytes(16)) . '.' . $extension;
        $image->moveTo($this->uploadsDir . '/' . $filename);

        $previousFilename = $this->teams->getImageFilename($id);
        $this->teams->setImage($id, $filename);

        if ($previousFilename !== null) {
            @unlink($this->uploadsDir . '/' . $previousFilename);
        }

        return $this->json($response, ['image' => 'uploads/' . $filename]);
    }
}
