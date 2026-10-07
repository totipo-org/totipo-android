package org.totipo.android.debug;

import android.provider.DocumentsContract.Document;
import org.junit.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class ProbeLogicTest {
    @Test public void decodesPlatformFlagsAndRetainsFalseValues() {
        long flags = Document.FLAG_DIR_SUPPORTS_CREATE | Document.FLAG_SUPPORTS_WRITE
                | Document.FLAG_SUPPORTS_RENAME | Document.FLAG_SUPPORTS_MOVE
                | Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_REMOVE
                | Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_PARTIAL
                | Document.FLAG_SUPPORTS_METADATA | Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE
                | Document.FLAG_SUPPORTS_TRASH | Document.FLAG_SUPPORTS_RESTORE;
        Map<String, Boolean> decoded = ProbeLogic.flags(flags);
        assertEquals(18, decoded.size());
        for (String name : Arrays.asList("supportsCreate", "supportsWrite", "supportsRename", "supportsMove",
                "supportsDelete", "supportsRemove", "virtualDocument", "partialDocument", "supportsMetadata",
                "dirBlocksOpenDocumentTree", "supportsTrash", "supportsRestore")) assertTrue(name, decoded.get(name));
        assertFalse(decoded.get("supportsCopy"));
        assertFalse(decoded.get("supportsThumbnail"));
        assertFalse(ProbeLogic.flags(1L << 40).get("supportsCreate"));
    }
    @Test public void decodesOptionalApi37SyncStateWithoutDurabilityClaims() {
        Map<String, Boolean> state = ProbeLogic.syncFlags(Document.SYNC_STATE_FLAG_AVAILABLE_LOCALLY
                | Document.SYNC_STATE_FLAG_LOCAL_CHANGES | Document.SYNC_STATE_FLAG_UPLOAD_ERROR
                | Document.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS);
        assertEquals(6, state.size());
        assertTrue(state.get("availableLocally"));
        assertTrue(state.get("localChanges"));
        assertTrue(state.get("uploadError"));
        assertTrue(state.get("downloadProgress"));
        assertFalse(state.get("downloadError"));
        assertFalse(state.get("uploadProgress"));
    }
    @Test public void exactNamesAreCaseSensitiveAndCountDuplicates() {
        assertEquals(2, ProbeLogic.exactMatches(Arrays.asList(null, "probe", "Probe", "probe", "probe (1)"), "probe"));
        assertEquals(0, ProbeLogic.exactMatches(Arrays.asList("probe (1)", null), "probe"));
    }
    @Test public void cleanupRequiresExactRecordedIdentityAndTree() {
        Map<String, String> identities = new HashMap<>();
        identities.put("content://provider/owned", "opaque-id");
        assertTrue(ProbeLogic.owns("tree-a", "tree-a", "content://provider/owned", identities, "opaque-id"));
        assertFalse(ProbeLogic.owns("tree-b", "tree-a", "content://provider/owned", identities, "opaque-id"));
        assertFalse(ProbeLogic.owns("tree-a", "tree-a", "content://provider/sibling", identities, "opaque-id"));
        assertFalse(ProbeLogic.owns("tree-a", "tree-a", "content://provider/owned", identities, "changed-id"));
        assertFalse(ProbeLogic.owns("tree-a", "tree-a", "content://provider/owned", identities, null));
        assertFalse(ProbeLogic.isNewIdentity("old", Arrays.asList("old", "other")));
        assertFalse(ProbeLogic.isNewIdentity(null, Arrays.asList("old")));
        assertTrue(ProbeLogic.isNewIdentity("new", Arrays.asList("old")));
    }
    @Test public void identifiersAndReportLinesDoNotExposeOpaqueInput() {
        String secretish = "account@example.test:Documents/personal";
        assertFalse(ProbeLogic.opaque(secretish).contains(secretish));
        assertEquals(ProbeLogic.opaque(secretish), ProbeLogic.opaque(secretish));
        assertNotEquals(ProbeLogic.opaque(secretish), ProbeLogic.opaque(secretish + "2"));
        assertEquals("unknown", ProbeLogic.opaque(null));
        assertEquals("a b ?", ProbeLogic.line("a\nb\r\t"));
    }
    @Test public void knownStageHasStableDigestAndPrefixDiffers() {
        byte[] stage = ProbeLogic.pattern();
        assertEquals(1024, stage.length);
        assertArrayEquals(stage, ProbeLogic.pattern());
        assertNotEquals(ProbeLogic.sha(stage), ProbeLogic.sha(Arrays.copyOf(stage, 256)));
        assertEquals(64, ProbeLogic.sha(stage).length());
    }
}
