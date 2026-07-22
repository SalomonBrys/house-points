#!/usr/bin/env php
<?php

declare(strict_types=1);

use App\Database;
use App\Repositories\UserRepository;

require __DIR__ . '/../vendor/autoload.php';

Dotenv\Dotenv::createImmutable(__DIR__ . '/..')->safeLoad();

[, $username, $password, $displayName] = array_pad($argv, 4, null);

if ($username === null || $password === null || $displayName === null) {
    fwrite(STDERR, "Usage: php bin/create-admin.php <username> <password> <display_name>\n");
    exit(1);
}

$pdo = Database::connect([
    'host' => $_ENV['DB_HOST'] ?? '127.0.0.1',
    'port' => $_ENV['DB_PORT'] ?? '3306',
    'name' => $_ENV['DB_NAME'] ?? '',
    'user' => $_ENV['DB_USER'] ?? '',
    'pass' => $_ENV['DB_PASS'] ?? '',
]);

$users = new UserRepository($pdo);

if ($users->usernameExists($username)) {
    fwrite(STDERR, "Username '$username' is already taken.\n");
    exit(1);
}

$id = $users->create($username, password_hash($password, PASSWORD_DEFAULT), 'admin', $displayName);

echo "Created admin #$id ($username)\n";
