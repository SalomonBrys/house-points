// Assembles a publishable website combining the back API and the compiled
// front end into build/website — see front/ARCHITECTURE.md and
// back/ARCHITECTURE.md for the two halves this glues together.

val websiteDir = layout.buildDirectory.dir("website")

val syncBackToWebsite by tasks.registering(Sync::class) {
    // vendor/ is included in the artifact, so make sure it's actually present
    // and current before syncing rather than trusting whatever's on disk.
    dependsOn(":back:composerInstall")
    from(project(":back").file("src")) {
        // .env stays excluded: unlike vendor/ (deterministic from
        // composer.lock), it holds live secrets — the deploy target supplies
        // its own.
        exclude(".env")
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
