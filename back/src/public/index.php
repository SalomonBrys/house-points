<?php

declare(strict_types=1);

use Anthropic\Client as AnthropicClient;
use App\Auth\JwtAuthMiddleware;
use App\Auth\JwtService;
use App\Auth\RefreshTokenService;
use App\Auth\RequireRoleMiddleware;
use App\Controllers\AuthController;
use App\Controllers\EventsController;
use App\Controllers\HousesController;
use App\Controllers\MeController;
use App\Controllers\PointEventsController;
use App\Controllers\UsersController;
use App\Database;
use App\Http\CorsMiddleware;
use App\Repositories\HouseRepository;
use App\Repositories\PointEventRepository;
use App\Repositories\RefreshTokenRepository;
use App\Repositories\UserRepository;
use App\Services\CommentGenerator;
use Slim\Factory\AppFactory;
use Slim\Psr7\Factory\ResponseFactory;

require __DIR__ . '/../vendor/autoload.php';

Dotenv\Dotenv::createImmutable(__DIR__ . '/..')->safeLoad();

$pdo = Database::connect([
    'host' => $_ENV['DB_HOST'] ?? '127.0.0.1',
    'port' => $_ENV['DB_PORT'] ?? '3306',
    'name' => $_ENV['DB_NAME'] ?? '',
    'user' => $_ENV['DB_USER'] ?? '',
    'pass' => $_ENV['DB_PASS'] ?? '',
]);

$jwtService = new JwtService(
    secret: $_ENV['JWT_SECRET'] ?? '',
    ttl: (int) ($_ENV['JWT_TTL'] ?? 900),
);

$userRepository = new UserRepository($pdo);
$houseRepository = new HouseRepository($pdo);
$pointEventRepository = new PointEventRepository($pdo);
$refreshTokenService = new RefreshTokenService(new RefreshTokenRepository($pdo));

$anthropicClient = new AnthropicClient(apiKey: $_ENV['ANTHROPIC_API_KEY'] ?? '');
$commentGenerator = new CommentGenerator($anthropicClient, __DIR__ . '/../AIPrompt.txt');

$responseFactory = new ResponseFactory();

$deps = [
    'auth' => new AuthController($userRepository, $jwtService, $refreshTokenService),
    'me' => new MeController($userRepository, $refreshTokenService),
    'houses' => new HousesController($houseRepository),
    'pointEvents' => new PointEventsController(
        $pointEventRepository,
        $houseRepository,
        $userRepository,
        $commentGenerator,
    ),
    'events' => new EventsController($pointEventRepository),
    'users' => new UsersController($userRepository),
    'jwtAuth' => new JwtAuthMiddleware($jwtService, $responseFactory),
    'requireAdmin' => new RequireRoleMiddleware(['admin'], $responseFactory),
];

$corsAllowedOrigins = array_values(array_filter(array_map(
    'trim',
    explode(',', $_ENV['CORS_ALLOWED_ORIGINS'] ?? '*'),
)));

$app = AppFactory::create();
$app->addBodyParsingMiddleware();
$app->addErrorMiddleware(
    displayErrorDetails: filter_var($_ENV['APP_DEBUG'] ?? false, FILTER_VALIDATE_BOOL),
    logErrors: true,
    logErrorDetails: true,
);
// Added last → outermost: answers preflight before routing and decorates
// error responses (e.g. 401/500) with CORS headers too.
$app->add(new CorsMiddleware($corsAllowedOrigins, $responseFactory));

(require __DIR__ . '/../routes.php')($app, $deps);

$app->run();
