plugins {
    base
    id("com.android.application") version "9.4.0" apply false
}

val versionText = providers.fileContents(layout.projectDirectory.file("VERSION")).asText.get()
require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?\n").matches(versionText)) {
    "VERSION must contain one semantic version and a final newline"
}
version = versionText.removeSuffix("\n")

dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}
buildscript.configurations.configureEach { resolutionStrategy.activateDependencyLocking() }

val buildClasspath = buildscript.configurations.named("classpath")
val verifyBuildDependencies = tasks.register("verifyBuildDependencies") {
    group = "verification"
    description = "Resolve and strictly verify the locked AGP build classpath"
    inputs.files(buildClasspath)
    doLast {
        buildClasspath.get().resolve()
        logger.lifecycle("Verified locked build classpath")
    }
}
tasks.named("check") { dependsOn(":app:check", verifyBuildDependencies) }
tasks.named("assemble") { dependsOn(":app:assembleDebug", ":app:assembleRelease") }
tasks.named("clean") { dependsOn(":app:clean") }
tasks.named<Wrapper>("wrapper") {
    gradleVersion = "9.8.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c"
}
