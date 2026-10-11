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

    @Test public void reviewedSupplyChainInputsRemainPinned() throws Exception {
        // Stable build scripts remain fully pinned; VERSION and VERSION_CODE are release metadata.
        // App script and flake rebaselined once for the reviewed release-source migration/apps.
        var baseline = java.util.Map.ofEntries(
                java.util.Map.entry("app/build.gradle.kts", "eb6dbefbbdef74771d5d428969248dd936b40955c0b8286adcb07ff8db41e98c"),
                java.util.Map.entry("build.gradle.kts", "08ecc23eea4637d5b5d0bf36b9f9d39f67e23fa3a6be57f34f594d6ad8daa348"),
                java.util.Map.entry("settings.gradle.kts", "948017ca7cb553ecfd91a9f3eab56a1db4870c7eb4c0086cb2f4527503f89141"),
                java.util.Map.entry("gradle.properties", "3db21dde187dd9e9cba8400b07e91374f69131754994c004b2810949be97c27b"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.properties", "e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.jar", "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"),
                java.util.Map.entry("gradle/verification-metadata.xml", "0492327c6a5445cec7c140630598f97853074573a4acf2e2d8565ce481cbfba4"),
                java.util.Map.entry("app/gradle.lockfile", "4d3aee17359005cc60a5f3286c4583a35fffcdce91bb6992ad792eebf5c42226"),
                java.util.Map.entry("buildscript-gradle.lockfile", "22efaa36cdbc4212cb62beb29837199812865f2b303caaef124b07e9915fc1a0"),
                java.util.Map.entry("flake.nix", "dbc85c66c608a2f8866c2cae24a66b96238d08a3591b21ee959f2ca3da8e78b2"),
                // Rebaselined for the intentional flake update in 5f2fdd8.
                java.util.Map.entry("flake.lock", "44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749"),
                // Rebaselined for the verified Google Play SDK Index snapshot refresh.
                java.util.Map.entry("package-deps.json", "8d4ac6dd7fc08cc3793ae46f21cb885cf1300ccef992fe9f399fe6982f89c133"));
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
