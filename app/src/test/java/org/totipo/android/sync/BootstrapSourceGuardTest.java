package org.totipo.android.sync;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class BootstrapSourceGuardTest {
    @Test public void publicSpiAuthenticationAndFixedCreateOnlyPlatformBoundary() throws Exception {
        Path root=Path.of("src/main/java/org/totipo/android");
        String auth=Files.readString(root.resolve("reconcile/DetachedVaultAuthentication.java"));
        for(String forbidden:List.of("org.totipo.format","Argon2","Cipher","reflect","K_root","Files.","ContentResolver")) assertFalse(forbidden,auth.contains(forbidden));
        assertTrue(auth.contains("Totipo.open(new CandidateStore(exact), credential)"));assertTrue(auth.contains("try (var session = opened.session())"));
        String port=Files.readString(root.resolve("sync/AndroidProviderVaultPort.java"));
        assertTrue(port.contains('"'+"vault"+'"'));assertTrue(port.contains('"'+"objects-v1"+'"'));assertTrue(port.contains("private Document create("));
        assertTrue(port.contains("DocumentsContract.isChildDocument"));assertEquals(1,port.split("DocumentsContract.createDocument",-1).length-1);
        String writer=Files.readString(root.resolve("sync/ProviderVaultWriter.java"));
        assertTrue(writer.contains("port.vaultOutput(created)"));assertFalse(writer.contains("vaultOutput(root)"));
        for(String forbidden:List.of("renameDocument","deleteDocument","moveDocument","copyDocument","VaultSession","credential","Totipo.open","java.nio"))assertFalse(forbidden,writer.contains(forbidden));
        String domain=Files.readString(root.resolve("reconcile/CoordinatedPrivateStore.java"));
        assertEquals(2,domain.split(java.util.regex.Pattern.quote("delegate.createVault("),-1).length-1); // reviewed bridge + ordinary Java creation only
        assertTrue(domain.contains("delegate.readVault(87)"));
        String lane=Files.readString(root.resolve("sync/ProviderIoLane.java"));
        assertTrue(lane.contains("ProviderVaultWriter.initialize"));assertFalse(lane.contains("char[]"));
    }
}
