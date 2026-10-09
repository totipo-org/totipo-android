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

    @Test public void reviewedSupplyChainInputsRemainPinned() throws Exception {
        // Java 0.2.0 is the approved Totipo-only dependency change; unrelated pins remain unchanged.
        var baseline = java.util.Map.ofEntries(
                java.util.Map.entry("app/build.gradle.kts", "69b3f5c737e5f98a1d99be4fe02204f6f2cfadef1b85323c66916a9619db8736"),
                java.util.Map.entry("build.gradle.kts", "08ecc23eea4637d5b5d0bf36b9f9d39f67e23fa3a6be57f34f594d6ad8daa348"),
                java.util.Map.entry("settings.gradle.kts", "948017ca7cb553ecfd91a9f3eab56a1db4870c7eb4c0086cb2f4527503f89141"),
                java.util.Map.entry("gradle.properties", "3db21dde187dd9e9cba8400b07e91374f69131754994c004b2810949be97c27b"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.properties", "e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.jar", "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"),
                java.util.Map.entry("gradle/verification-metadata.xml", "0492327c6a5445cec7c140630598f97853074573a4acf2e2d8565ce481cbfba4"),
                java.util.Map.entry("app/gradle.lockfile", "4d3aee17359005cc60a5f3286c4583a35fffcdce91bb6992ad792eebf5c42226"),
                java.util.Map.entry("buildscript-gradle.lockfile", "22efaa36cdbc4212cb62beb29837199812865f2b303caaef124b07e9915fc1a0"),
                java.util.Map.entry("flake.nix", "1ee5379470643f1699d65e7ba6ea834ffe4cc277d8df49cc59f68c1311b7a5a9"),
                // Rebaselined for the intentional flake update in 5f2fdd8.
                java.util.Map.entry("flake.lock", "44728fcfd8529462cb415b186ee4ce3400343dd911d325b6a965bd386b9f7749"),
                java.util.Map.entry("package-deps.json", "74eb72a9ffb5f9f364f02f9f64014f260f4baf610c1cefdb9b09ed6846eb42b1"));
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
