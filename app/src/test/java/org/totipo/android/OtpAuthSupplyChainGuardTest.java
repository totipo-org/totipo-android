package org.totipo.android;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Supply-chain digests; Totipo inputs deliberately rebaselined for Java 0.2.0/r19. */
public final class OtpAuthSupplyChainGuardTest {
    @Test public void noQrDependencyDeclared() throws Exception {
        for (String name : List.of("build.gradle.kts", "../build.gradle.kts", "../settings.gradle.kts")) {
            String build = Files.readString(Path.of(name)).toLowerCase(java.util.Locale.ROOT);
            for (String forbidden : List.of("zxing", "camerax", "mlkit", "ml-kit", "play-services", "androidx.camera", "qrcode"))
                assertFalse(name + ": " + forbidden, build.contains(forbidden));
        }
    }

    private static String digest(byte[] contents) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(contents));
    }

    @Test public void releaseMetadataDoesNotChangeProtectedBuildPin() throws Exception {
        byte[] stableBuild = Files.readAllBytes(Path.of("build.gradle.kts"));
        String reviewed = digest(stableBuild);
        // Synthetic source inventories: no repository mutation and no metadata in the pin.
        for (String releaseCode : List.of("2\n", "3\n")) {
            var source = java.util.Map.of("app/build.gradle.kts", stableBuild,
                    "VERSION_CODE", releaseCode.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(reviewed, digest(source.get("app/build.gradle.kts")));
        }
        String build = new String(stableBuild, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(build.contains("versionCode = releaseVersionCode"));
        for (String changed : List.of(
                build.replace("com.android.application", "unreviewed.plugin"),
                build.replace("org.totipo:totipo-core:0.2.0", "org.totipo:totipo-core:9.9.9"),
                build.replace("isMinifyEnabled = false", "isMinifyEnabled = true"))) {
            assertNotEquals(reviewed, digest(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        String settings = Files.readString(Path.of("../settings.gradle.kts"));
        assertNotEquals(digest(settings.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                digest((settings + "\nrepositories { mavenLocal() }\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static final java.util.Set<String> REVIEWED_PATHS = java.util.Set.of(
            "app/build.gradle.kts",
            "app/gradle.lockfile",
            "build.gradle.kts",
            "buildscript-gradle.lockfile",
            "flake.lock",
            "flake.nix",
            "gradle.properties",
            "gradle/verification-metadata.xml",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            "package-deps.json",
            "settings.gradle.kts");

    private static java.util.Map<String, String> reviewedInputs(String data) {
        var entries = new java.util.LinkedHashMap<String, String>();
        for (String line : data.split("(?<=\\n)", -1)) {
            if (line.isEmpty()) continue;
            if (!line.matches("[0-9a-f]{64}  [A-Za-z0-9/._-]+\\n"))
                throw new IllegalArgumentException("Malformed reviewed digest entry");
            String name = line.substring(66, line.length() - 1);
            if (!REVIEWED_PATHS.contains(name) || entries.putIfAbsent(name, line.substring(0, 64)) != null)
                throw new IllegalArgumentException("Unknown/duplicate reviewed path");
        }
        if (!entries.keySet().equals(REVIEWED_PATHS)
                || !new java.util.ArrayList<>(entries.keySet()).equals(entries.keySet().stream().sorted().toList()))
            throw new IllegalArgumentException("Missing/unsorted reviewed input inventory");
        return entries;
    }

    @Test public void reviewedInventoryFailsClosed() throws Exception {
        String inventory = Files.readString(Path.of("../tools/release/reviewed-inputs.sha256"));
        assertEquals(REVIEWED_PATHS, reviewedInputs(inventory).keySet());
        String first = inventory.substring(0, inventory.indexOf('\n') + 1);
        for (String bad : List.of("", inventory.substring(first.length()), inventory + first,
                inventory.replace("app/build.gradle.kts", "unknown"), inventory.toUpperCase(java.util.Locale.ROOT),
                inventory.replaceFirst("  ", " "), inventory.stripTrailing(), inventory.replace("\n", "\r\n"),
                inventory.substring(first.length()) + first)) {
            assertThrows(IllegalArgumentException.class, () -> reviewedInputs(bad));
        }
    }

    @Test public void reviewedSupplyChainInputsRemainPinned() throws Exception {
        // Expected digests live in data, never in either consumer's source syntax.
        var baseline = reviewedInputs(Files.readString(Path.of("../tools/release/reviewed-inputs.sha256")));
        for (var entry : baseline.entrySet()) {
            Path input = Path.of("../" + entry.getKey());
            // package.nix deliberately excludes these three evaluator/cache inputs from Gradle's source tree.
            if (!Files.exists(input) && java.util.Set.of("flake.nix", "flake.lock", "package-deps.json").contains(entry.getKey())) continue;
            byte[] contents = Files.readAllBytes(input);
            String actual = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(contents));
            assertEquals(entry.getKey(), entry.getValue(), actual);
        }
    }
}
