package org.totipo.android.sync;
import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SyncSourceGuardTest {
    @Test public void readOnlyPlatformAndHonestUiBoundary() throws Exception {
        String binding = Files.readString(Path.of("src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java"));
        String reader = Files.readString(Path.of("src/main/java/org/totipo/android/provider/ProviderTreeReader.java"));
        String ui = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        for (String forbidden : List.of("createDocument", "deleteDocument", "renameDocument", "openOutputStream",
                "openFileDescriptor", "_data", "/storage/", "com.google.android.apps.docs", "DocumentFile"))
            assertFalse(forbidden, (binding + reader).contains(forbidden));
        for (String forbidden : List.of("fully synced", "two-way sync", "everything up to date"))
            assertFalse(ui.toLowerCase().contains(forbidden));
        assertTrue(ui.contains("lock.setEnabled(state.state() == State.OPEN)"));
        assertFalse(ui.contains("Executor")); assertFalse(ui.contains("ContentResolver"));
        String lane = Files.readString(Path.of("src/main/java/org/totipo/android/sync/ProviderIoLane.java"));
        for (String forbidden : List.of("VaultSession", "LocalReplicaOwner", "CoordinatedPrivateStore", "NioTotipoStore",
                "AddTokenRequest", "NewSecret", "validateObject", "password", "Clipboard")) assertFalse(lane.contains(forbidden));
        assertFalse(ui.contains("ProviderTreeReader")); assertFalse(ui.contains("ProviderSnapshot"));
        assertTrue(ui.contains("ACTION_OPEN_DOCUMENT_TREE"));
        for (String flag : List.of("READ", "WRITE", "PERSISTABLE", "PREFIX")) assertTrue(ui.contains("FLAG_GRANT_" + flag + "_URI_PERMISSION"));
        assertTrue(binding.contains("sync_binding_v1")); assertTrue(binding.contains("getPersistedUriPermissions"));
    }
}
