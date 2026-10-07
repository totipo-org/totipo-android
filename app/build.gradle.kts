import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins { id("com.android.application") }

android {
    namespace = "org.totipo.android"
    enableKotlin = false
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "org.totipo.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = rootProject.version.toString()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
dependencies {
    implementation("org.totipo:totipo-core:0.1.3")
    debugImplementation("org.totipo:totipo-storage-nio:0.1.3")
    testImplementation("junit:junit:4.13.2")
}
dependencyLocking {
    lockAllConfigurations()
    lockMode.set(LockMode.STRICT)
}
buildscript.configurations.configureEach { resolutionStrategy.activateDependencyLocking() }

val productionConfigurations = listOf(
    "debugCompileClasspath", "debugRuntimeClasspath",
    "releaseCompileClasspath", "releaseRuntimeClasspath"
)
val verifyMavenBoundary = tasks.register("verifyMavenBoundary") {
    group = "verification"
    description = "Require released core/BC and debug-only NIO; reject local Totipo artifacts"
    val graphs = productionConfigurations.map { configurations.named(it).get().incoming.resolutionResult.rootComponent }
    inputs.files(productionConfigurations.map { configurations.named(it) })
    doLast {
        graphs.forEachIndexed { index, graph ->
            val root = graph.get()
            val visited = mutableSetOf<org.gradle.api.artifacts.result.ResolvedComponentResult>()
            fun visit(component: org.gradle.api.artifacts.result.ResolvedComponentResult) {
                if (!visited.add(component)) return
                component.dependencies.forEach { dependency ->
                    check(dependency is ResolvedDependencyResult) { "Unresolved dependency: $dependency" }
                    visit(dependency.selected)
                }
            }
            visit(root)
            val components = visited.filter { it != root }
            val modules = components.map { component ->
                check(component.id is ModuleComponentIdentifier) { "Non-Maven dependency: ${component.id}" }
                component.id as ModuleComponentIdentifier
            }
            val totipo = modules.filter { it.group == "org.totipo" || it.group.startsWith("org.totipo.") }
            val debug = productionConfigurations[index].startsWith("debug")
            val expected = setOf("org.totipo:totipo-core:0.1.3") +
                if (debug) setOf("org.totipo:totipo-storage-nio:0.1.3") else emptySet()
            check(totipo.map { "${it.group}:${it.module}:${it.version}" }.toSet() == expected) {
                "Unexpected Totipo modules in ${productionConfigurations[index]}: $totipo"
            }
            check(modules.none { it.group.startsWith("dev.totipo") ||
                (it.module == "totipo-storage-nio" && (!debug || it.group != "org.totipo" || it.version != "0.1.3")) }) {
                "Unexpected storage provider or obsolete Totipo coordinate"
            }
            val direct = root.dependencies.filterIsInstance<ResolvedDependencyResult>().filter { !it.isConstraint }
            check(direct.any { (it.selected.id as? ModuleComponentIdentifier)?.let { id ->
                id.group == "org.totipo" && id.module == "totipo-core" && id.version == "0.1.3"
            } == true }) { "Core must be a direct external module" }
            if (debug) check(direct.any { (it.selected.id as? ModuleComponentIdentifier)?.let { id ->
                id.group == "org.totipo" && id.module == "totipo-storage-nio" && id.version == "0.1.3"
            } == true }) { "Debug qualification NIO must be a direct external module" }
            // File dependencies do not appear in ResolutionResult; inspect artifacts too.
            configurations.getByName(productionConfigurations[index]).incoming.artifacts.artifacts.forEach { artifact ->
                check(artifact.id.componentIdentifier is ModuleComponentIdentifier) {
                    "File/project artifact found: ${artifact.id}"
                }
            }
            if (productionConfigurations[index].endsWith("RuntimeClasspath")) {
                val core = components.single { (it.id as? ModuleComponentIdentifier)?.let { id ->
                    id.group == "org.totipo" && id.module == "totipo-core"
                } == true }
                check(core.dependencies.filterIsInstance<ResolvedDependencyResult>().any { edge ->
                    !edge.isConstraint && (edge.selected.id as? ModuleComponentIdentifier)?.let { id ->
                        id.group == "org.bouncycastle" && id.module == "bcprov-jdk18on" && id.version == "1.86"
                    } == true
                }) { "Expected core -> BC 1.86 Maven runtime relationship" }
            }
        }
        logger.lifecycle("Verified core 0.1.3; NIO 0.1.3 debug only; external Maven boundary")
    }
}
tasks.named("check") { dependsOn(verifyMavenBoundary, "testDebugUnitTest", "lint") }

val verifyReleaseApkBoundary = tasks.register<Exec>("verifyReleaseApkBoundary") {
    group = "verification"
    description = "Require release APK to exclude NIO and all debug probes"
    dependsOn("assembleRelease")
    commandLine("python3", rootProject.file("tools/verify-apk.py"),
        layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk").get().asFile,
        "--unsigned", "--no-debug-probe")
}
tasks.named("check") { dependsOn(verifyReleaseApkBoundary) }
