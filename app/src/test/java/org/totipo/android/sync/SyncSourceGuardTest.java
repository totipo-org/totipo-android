package org.totipo.android.sync;
import java.nio.file.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SyncSourceGuardTest {
    @Test public void productionCreateOnlyAuditAndNoLocalRefreshOrRevealReplacement() throws Exception {
        var root = Path.of("src/main/java/org/totipo/android");
        String port = Files.readString(root.resolve("sync/AndroidSyncFolderPort.java"));
        String writer = Files.readString(root.resolve("sync/ProviderObjectWriter.java"));
        String controller = Files.readString(root.resolve("AndroidVaultController.java"));
        try (var sources = Files.walk(root)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String forbidden : List.of("deleteDocument", "renameDocument", "moveDocument", "removeDocument", "copyDocument"))
                    assertFalse(path + ": " + forbidden, source.contains(forbidden));
                if (!path.equals(root.resolve("sync/AndroidSyncFolderPort.java"))) {
                    assertFalse(path.toString(), source.contains("createDocument("));
                    assertFalse(path.toString(), source.contains("openOutputStream("));
                }
            }
        }
        assertTrue(port.contains("new org.totipo.RevisionId(name)"));
        assertTrue(port.contains("isChildDocument"));
        assertTrue(writer.contains("writer.output(created)"));
        assertFalse(writer.contains("writer.output(parent)"));
        String outbound = controller.substring(controller.indexOf("public synchronized boolean publishLocalChanges()"),
                controller.indexOf("private synchronized boolean startProvider(boolean importing)"));
        for (String forbidden : List.of("clearPresentation", "requestRefresh", "render(", "State.BUSY", "vault.close"))
            assertFalse(forbidden, outbound.contains(forbidden));
        assertTrue(outbound.contains("providerIo.publish"));
    }
    @Test public void platformAndHonestUiBoundary() throws Exception {
        String binding = Files.readString(Path.of("src/main/java/org/totipo/android/sync/AndroidSyncFolderPort.java"));
        String reader = Files.readString(Path.of("src/main/java/org/totipo/android/provider/ProviderTreeReader.java"));
        String ui = Files.readString(Path.of("src/main/java/org/totipo/android/MainActivity.java"));
        for (String forbidden : List.of("deleteDocument", "renameDocument", "openFileDescriptor", "_data", "/storage/", "com.google.android.apps.docs", "DocumentFile"))
            assertFalse(forbidden, (binding + reader).contains(forbidden));
        assertEquals(1, binding.split("DocumentsContract.createDocument", -1).length - 1);
        assertTrue(binding.contains("new org.totipo.RevisionId(name)"));
        assertTrue(binding.contains("openOutputStream(Uri.parse(created.locator()), \"w\")"));
        assertFalse(binding.contains("openOutputStream(Uri.parse(parent.locator())"));
        assertTrue(ui.contains("Publish local changes"));
        for (String forbidden : List.of("fully synced", "two-way sync", "everything up to date", "everything synchronized", "two-way vault sync", "vault synchronized"))
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
