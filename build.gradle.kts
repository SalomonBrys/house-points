// Assembles a publishable website combining the back API and the compiled
// front end into build/website — see front/ARCHITECTURE.md and
// back/ARCHITECTURE.md for the two halves this glues together.

val websiteDir = layout.buildDirectory.dir("website")

val syncBackToWebsite by tasks.registering(Sync::class) {
    // vendor/ is included in the artifact, so make sure it's actually present
    // and current before syncing rather than trusting whatever's on disk.
    dependsOn(":back:composerInstall")
    from(project(":back").file("src")) {
        // .env stays excluded: unlike vendor/ (deterministic from composer.lock), it holds live secrets — the deploy target supplies its own.
        exclude(".env")
        // Exclude dev-only files & directories:
        exclude("composer.json", "composer.lock", "bin", "database", "docker-compose.yml")
    }
    into(websiteDir)
}

val syncFrontDistToWebsite by tasks.registering(Sync::class) {
    dependsOn(":front:composeCompatibilityBrowserDistribution")
    // Sync execution order isn't otherwise guaranteed between two independent
    // dependsOn tasks — without this, the back sync could run after the
    // front sync and stomp the real index.html back to the static/ placeholder.
    mustRunAfter(syncBackToWebsite)
    from(project(":front").layout.buildDirectory.dir("dist/composeWebCompatibility/productionExecutable"))
    into(websiteDir.map { it.dir("static") })
}

tasks.register("buildWebsite") {
    group = "distribution"
    description = "Assembles a publishable website (back API + compiled front end) into build/website."
    dependsOn(syncBackToWebsite, syncFrontDistToWebsite)
}

val syncFrontDistToBack by tasks.registering(Sync::class) {
    dependsOn(":front:wasmJsBrowserDistribution")
    from(project(":front").layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(project(":back").layout.projectDirectory.dir("src/static"))
}

// Deploys build/website to an FTP server via lftp's `mirror --reverse --delete`
// (upload + overwrite + prune orphans in one command). Credentials and the
// target directory come in as Gradle properties, never hardcoded.
val ftpServer = providers.gradleProperty("ftpServer")
val ftpUsername = providers.gradleProperty("ftpUsername")
val ftpPassword = providers.gradleProperty("ftpPassword")
val ftpDirectory = providers.gradleProperty("ftpDirectory")
val ftpDryRun = providers.gradleProperty("ftpDryRun")
val websitePath = layout.buildDirectory.dir("website").map { it.asFile.absolutePath }

tasks.register<Exec>("deployFtp") {
    group = "distribution"
    description = "Deploys build/website to an FTP server (mirror + prune orphans) via lftp. " +
        "Requires -PftpServer -PftpUsername -PftpPassword -PftpDirectory " +
        "(ftpDirectory must already exist on the server); optional -PftpDryRun=true. " +
        "Requires lftp to be installed (macOS: brew install lftp)."
    dependsOn("buildWebsite")

    // The lftp command script (built in doFirst, including the `user` login
    // line) is piped in via standardInput below — never passed as a command
    // line argument — so no credential ever appears in argv/ps or build logs.
    commandLine("lftp")

    doFirst {
        fun require(property: Provider<String>, name: String): String =
            property.orNull?.takeIf { it.isNotBlank() }
                ?: throw GradleException(
                    "Missing required -P$name (need: ftpServer, ftpUsername, ftpPassword, ftpDirectory).",
                )

        val server = require(ftpServer, "ftpServer")
        val username = require(ftpUsername, "ftpUsername")
        val password = require(ftpPassword, "ftpPassword")
        val directory = require(ftpDirectory, "ftpDirectory")
        if (directory.trim() == "/") {
            throw GradleException("Refusing to deploy: ftpDirectory must be a subdirectory, not '/'.")
        }
        val dryRunFlag = if (ftpDryRun.orNull == "true") "--dry-run " else ""
        val dollar = "$" // splice a literal '$' into the regex below, so lftp sees ^\.env$

        val script = buildString {
            appendLine("set cmd:fail-exit yes")
            appendLine("open \"$server\"")
            appendLine("user \"$username\" \"$password\"")
            // --exclude keeps the remote .env, uploads/ and logs/ out of the
            // mirror entirely, so mirror's --delete never removes them.
            appendLine(
                "mirror --reverse --delete --verbose $dryRunFlag" +
                    "--exclude '^\\.env$dollar' --exclude '^uploads/' --exclude '^logs/' " +
                    "\"${websitePath.get()}\" \"$directory\"",
            )
            appendLine("bye")
        }
        standardInput = script.byteInputStream()
    }
}
