package org.totipo.android.sync;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import org.totipo.android.provider.ProviderSnapshot.Scan;
import org.totipo.android.provider.ProviderTreeReader;
import org.totipo.android.sync.SyncFolderBinding.*;

/** Platform-only SAF adapter. Writes are restricted to newly created immutable documents. */
public final class AndroidSyncFolderPort implements SyncFolderBinding.Port, ProviderObjectWriter.Port {
    private final ContentResolver resolver;
    private final SharedPreferences preferences;
    public AndroidSyncFolderPort(Context context) {
        resolver = context.getContentResolver();
        preferences = context.getSharedPreferences("sync_binding_v1", Context.MODE_PRIVATE);
    }
    public Stored load() {
        try {
            if (!preferences.contains("tree_uri")) return null;
            if (preferences.getInt("schema", 0) != 1) return new Stored("", false, false);
            return new Stored(preferences.getString("tree_uri", ""),
                    preferences.getBoolean("read", false), preferences.getBoolean("write", false));
        } catch (RuntimeException malformed) { return new Stored("", false, false); }
    }
    public boolean save(Stored value) {
        var edit = preferences.edit().clear();
        if (value != null) edit.putInt("schema", 1).putString("tree_uri", value.uri())
                .putBoolean("read", value.readable()).putBoolean("write", value.writable());
        return edit.commit();
    }
    public boolean validTree(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "content".equals(uri.getScheme()) && uri.getAuthority() != null
                    && DocumentsContract.isTreeUri(uri) && !DocumentsContract.getTreeDocumentId(uri).isEmpty();
        } catch (RuntimeException malformed) { return false; }
    }
    public Grants grants(String value) {
        Uri uri = Uri.parse(value);
        boolean read = false, write = false;
        for (var grant : resolver.getPersistedUriPermissions()) if (uri.equals(grant.getUri())) {
            read |= grant.isReadPermission(); write |= grant.isWritePermission();
        }
        return new Grants(read, write);
    }
    private static int flags(boolean read, boolean write) {
        return (read ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0)
                | (write ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
    }
    public void take(String uri, boolean read, boolean write) {
        resolver.takePersistableUriPermission(Uri.parse(uri), flags(read, write));
    }
    public void release(String uri, boolean read, boolean write) {
        if (read || write) resolver.releasePersistableUriPermission(Uri.parse(uri), flags(read, write));
    }
    public void probe(String value) {
        Uri uri = Uri.parse(value);
        try (var cursor = resolver.query(DocumentsContract.buildDocumentUriUsingTree(uri,
                DocumentsContract.getTreeDocumentId(uri)),
                new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()
                    || !DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(0))
                    || (cursor.getExtras() != null && (cursor.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING, false)
                    || cursor.getExtras().containsKey(DocumentsContract.EXTRA_ERROR))))
                throw new IllegalStateException("Provider unavailable");
        }
    }
    public org.totipo.android.provider.ProviderSnapshot.Document create(
            org.totipo.android.provider.ProviderSnapshot.Document parent, String name) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 29) throw new UnsupportedOperationException("Tree relationship verification unavailable");
        new org.totipo.RevisionId(name); // Canonical lowercase filename, never vault/directory.
        Uri tree = Uri.parse(parent.tree().locator());
        Uri uri = DocumentsContract.createDocument(resolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, parent.id()), "application/octet-stream", name);
        if (uri == null) return null;
        if (!parent.tree().authority().equals(uri.getAuthority()) || !DocumentsContract.isTreeUri(uri)
                || !parent.tree().rootId().equals(DocumentsContract.getTreeDocumentId(uri))
                || !DocumentsContract.isChildDocument(resolver,
                    DocumentsContract.buildDocumentUriUsingTree(tree, parent.id()), uri))
            throw new IllegalStateException("Created document outside target");
        return new org.totipo.android.provider.ProviderSnapshot.Document(parent.tree(), uri.toString(),
                DocumentsContract.getDocumentId(uri), parent.id(), name, "application/octet-stream", null, null);
    }
    public org.totipo.android.provider.ProviderSnapshot.Document metadata(
            org.totipo.android.provider.ProviderSnapshot.Document created) {
        try (var cursor = resolver.query(Uri.parse(created.locator()), new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() || (cursor.getExtras() != null
                    && (cursor.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING, false)
                        || cursor.getExtras().containsKey(DocumentsContract.EXTRA_ERROR)))) return null;
            return new org.totipo.android.provider.ProviderSnapshot.Document(created.tree(), created.locator(),
                    cursor.getString(0), created.parentId(), cursor.getString(1), cursor.getString(2), null, null);
        }
    }
    public java.io.OutputStream output(org.totipo.android.provider.ProviderSnapshot.Document created) throws Exception {
        return resolver.openOutputStream(Uri.parse(created.locator()), "w");
    }
    public org.totipo.android.provider.ProviderSnapshot.Bytes readBack(
            org.totipo.android.provider.ProviderSnapshot.Document created) {
        return new ProviderTreeReader(resolver, Uri.parse(created.tree().locator())).readCandidate(
                org.totipo.android.provider.ProviderSnapshot.newEpoch(), created, DetachedImmutableObject.REPRESENTATION_BYTES);
    }
    public Scan scan(String uri) { return new ProviderTreeReader(resolver, Uri.parse(uri)).snapshot(DetachedImmutableObject.REPRESENTATION_BYTES); }
}
