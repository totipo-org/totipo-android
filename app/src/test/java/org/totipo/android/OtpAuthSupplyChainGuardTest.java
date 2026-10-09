package org.totipo.android;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Supply-chain digests inherited unchanged from M2C/M2C2. */
public final class OtpAuthSupplyChainGuardTest {
    @Test public void noQrDependencyDeclared() throws Exception {
        for (String name : List.of("build.gradle.kts", "../build.gradle.kts", "../settings.gradle.kts")) {
            String build = Files.readString(Path.of(name)).toLowerCase(java.util.Locale.ROOT);
            for (String forbidden : List.of("zxing", "camerax", "mlkit", "ml-kit", "play-services", "androidx.camera", "qrcode"))
                assertFalse(name + ": " + forbidden, build.contains(forbidden));
        }
    }

    @Test public void enrollmentKeepsM2bSupplyChainInputsUnchanged() throws Exception {
        // M2C is a zero-dependency gate. Rebaseline deliberately for a later approved build-input change.
        var baseline = java.util.Map.ofEntries(
                java.util.Map.entry("app/build.gradle.kts", "5252eaa05661e7a6ceab7bc3fd9cde13592e64306b5f68465a4b1035d7fc87b3"),
                java.util.Map.entry("build.gradle.kts", "08ecc23eea4637d5b5d0bf36b9f9d39f67e23fa3a6be57f34f594d6ad8daa348"),
                java.util.Map.entry("settings.gradle.kts", "948017ca7cb553ecfd91a9f3eab56a1db4870c7eb4c0086cb2f4527503f89141"),
                java.util.Map.entry("gradle.properties", "3db21dde187dd9e9cba8400b07e91374f69131754994c004b2810949be97c27b"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.properties", "e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.jar", "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"),
                java.util.Map.entry("gradle/verification-metadata.xml", "28a3614f5764447de593aa2f1caf298bce9d756fb75e86d9fd6b1cd27281d6eb"),
                java.util.Map.entry("app/gradle.lockfile", "edd64f4193c53b5356998b60b1b739a32254a1c399bc0e32a3ff2a57063a2ca4"),
                java.util.Map.entry("buildscript-gradle.lockfile", "22efaa36cdbc4212cb62beb29837199812865f2b303caaef124b07e9915fc1a0"),
                java.util.Map.entry("flake.nix", "1ee5379470643f1699d65e7ba6ea834ffe4cc277d8df49cc59f68c1311b7a5a9"),
                java.util.Map.entry("flake.lock", "cb6b924b5f79a0b74108aba9666151cef918dbab26c8d3b7bb17fe00b6a3c8ed"),
                java.util.Map.entry("package-deps.json", "62fb17a174f1664c3ea1a50ffe223bd5cd937b78f4209d67e92cdc8b398727fb"));
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
