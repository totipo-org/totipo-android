package org.totipo.android.sync;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.totipo.android.provider.ProviderSnapshot.*;
import org.totipo.android.provider.ProviderTreeReader;

/** Fixed-name bootstrap transport only. Returned handles stay within one provider operation. */
final class AndroidProviderVaultPort implements ProviderVaultWriter.Port {
    private final ContentResolver resolver;
    private final AndroidSyncFolderPort rows;
    AndroidProviderVaultPort(ContentResolver resolver, AndroidSyncFolderPort rows) { this.resolver = resolver; this.rows = rows; }
    public Document createVault(Document root) throws Exception { return create(root, "vault", "application/octet-stream"); }
    public Document createObjectsDirectory(Document root) throws Exception { return create(root, "objects-v1", DocumentsContract.Document.MIME_TYPE_DIR); }
    private Document create(Document root, String name, String mime) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 29) throw new UnsupportedOperationException();
        if (!root.tree().rootId().equals(root.id())) throw new IllegalArgumentException();
        Uri tree = Uri.parse(root.tree().locator());
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, root.id());
        Uri created = DocumentsContract.createDocument(resolver, parent, mime, name);
        if (created == null) return null;
        if (!root.tree().authority().equals(created.getAuthority()) || !DocumentsContract.isTreeUri(created)
                || !root.tree().rootId().equals(DocumentsContract.getTreeDocumentId(created))
                || !DocumentsContract.isChildDocument(resolver, parent, created)) throw new IllegalStateException();
        return new Document(root.tree(), created.toString(), DocumentsContract.getDocumentId(created), root.id(), name, mime, null, null);
    }
    public Document vaultMetadata(Document created) { return rows.metadata(created); }
    public java.io.OutputStream vaultOutput(Document created) throws Exception {
        return resolver.openOutputStream(Uri.parse(created.locator()), "w");
    }
    public Bytes vaultReadBack(Document created) {
        return new ProviderTreeReader(resolver, Uri.parse(created.tree().locator())).readCandidate(
                org.totipo.android.provider.ProviderSnapshot.newEpoch(), created, 87);
    }
}
