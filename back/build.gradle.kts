/*
 * Backend (PHP/MySQL API) build script.
 *
 * The API itself is built and run with PHP tooling (Composer, `php -S`) and a
 * Dockerised MySQL; this Gradle script only exposes convenience tasks so the
 * backend can be driven from the same multi-project build as the Compose
 * frontend.
 *
 * Note: Gradle only recognises `build.gradle.kts`, not `gradle.kts`.
 */

// Override with -PphpHost=... / -PphpPort=... if the defaults clash.
val phpHost = (findProperty("phpHost") as String?) ?: "localhost"
val phpPort = (findProperty("phpPort") as String?) ?: "8080"
val apiDir = layout.projectDirectory.dir("src")

val composerInstall = tasks.register<Exec>("composerInstall") {
    group = "build setup"
    description = "Installs PHP dependencies with Composer."

    workingDir = apiDir.asFile
    commandLine("composer", "install", "--no-interaction")

    // Re-run only when the manifests change; otherwise Gradle marks it up-to-date.
    inputs.files(apiDir.file("composer.json"), apiDir.file("composer.lock"))
    outputs.dir(apiDir.dir("vendor"))
}

val dbUp = tasks.register<Exec>("dbUp") {
    group = "database"
    description = "Starts the MySQL container and waits until it is healthy."

    workingDir = apiDir.asFile
    commandLine("docker", "compose", "up", "-d", "--wait")
}

tasks.register<Exec>("dbDown") {
    group = "database"
    description = "Stops the MySQL container (data volume is preserved)."

    workingDir = apiDir.asFile
    commandLine("docker", "compose", "down")
}

tasks.register<Exec>("dbDownDelete") {
    group = "database"
    description = "Stops the MySQL container (data volume is removed)."

    workingDir = apiDir.asFile
    commandLine("docker", "compose", "down")
}

tasks.register<Exec>("phpServe") {
    group = "application"
    description = "Starts the PHP development server for the API (php -S ${phpHost}:${phpPort})."

    // Bootstrap dependencies and the database before serving.
    dependsOn(composerInstall, dbUp)

    workingDir = apiDir.asFile
    commandLine("php", "-S", "$phpHost:$phpPort", "-t", "public")

    doFirst {
        logger.lifecycle("Serving House Points API at http://$phpHost:$phpPort (Ctrl+C to stop)")
    }
}
