package org.totipo.android;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Keep the application free of unapproved QR/camera/cloud dependencies. */
public final class OtpAuthSupplyChainGuardTest {
    @Test public void noQrDependencyDeclared() throws Exception {
        for (String name : List.of("build.gradle.kts", "../build.gradle.kts", "../settings.gradle.kts")) {
            String build = Files.readString(Path.of(name)).toLowerCase(java.util.Locale.ROOT);
            for (String forbidden : List.of("zxing", "camerax", "mlkit", "ml-kit", "play-services", "androidx.camera", "qrcode"))
                assertFalse(name + ": " + forbidden, build.contains(forbidden));
        }
    }

}
