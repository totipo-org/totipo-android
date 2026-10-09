package org.totipo.android.reconcile;

import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Architecture regression guards run through normal check/unit tests. */
public final class CoordinationSourceGuardTest {
    @Test public void bridgeHasNoProtocolKeysProviderWritesOrCanonicalFilesystemPublication() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java/org/totipo/android/reconcile"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(path);
                for (String forbidden : List.of("createDocument", "deleteDocument", "renameDocument", "moveDocument",
                        "removeDocument", "openOutputStream", "ContentResolver", "Files.", "FileChannel", "java.io.File",
                        "org.totipo.format", "org.totipo.internal", "getRootKey", "rootKey", "Cipher", "Argon2")) {
                    assertFalse(path + ": " + forbidden, code.contains(forbidden));
                }
            }
        }
        String domain = Files.readString(Path.of("src/main/java/org/totipo/android/reconcile/CoordinatedPrivateStore.java"));
        String bridge = block(domain, "final class Bridge");
        for (String forbidden : List.of("replaceCanonical", "VaultSession", "session.", "Totipo.open")) {
            assertFalse(forbidden, bridge.contains(forbidden));
        }
        assertTrue(domain.contains("org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability())"));
        assertFalse(domain.contains("NioTotipoStore.open(root)"));
    }
    @Test public void exclusiveScopeNeverCallsBackIntoJavaAndSyncNeedsNoAuthentication() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/totipo/android/reconcile/ForegroundVaultCoordinator.java"));
        String exclusive = block(source.substring(source.indexOf("public Report sync(Scan scan")), "try (var bridge = store.bridge())");
        for (String forbidden : List.of("session.", "requestRefresh", "validateObject", "operations.refresh", "operations.close", "operations.open")) {
            assertFalse(forbidden, exclusive.contains(forbidden));
        }
        String sync = block(source, "public Report sync(Scan scan, Cancellation cancellation)");
        for (String forbidden : List.of("char[]", "operations.open", "operations.close", "failClosed", "Totipo.open", "establish(")) {
            assertFalse(forbidden, sync.contains(forbidden));
        }
        assertTrue(source.indexOf("operations.refresh(session, store)", source.indexOf(exclusive)) > source.indexOf(exclusive) + exclusive.length());
        assertTrue(source.contains("if (store.exclusiveHeldByCurrentThread())"));
    }
    private static String block(String source, String marker) {
        int start = source.indexOf('{', source.indexOf(marker)); assertTrue(start >= 0);
        int depth = 1, end = start + 1;
        for (; depth > 0 && end < source.length(); end++) {
            if (source.charAt(end) == '{') depth++;
            if (source.charAt(end) == '}') depth--;
        }
        assertEquals(0, depth); return source.substring(start, end);
    }
}
