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

/** Platform-only SAF adapter. Queries and streams are read-only, including with a write grant. */
public final class AndroidSyncFolderPort implements SyncFolderBinding.Port {
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
    public Scan scan(String uri) { return new ProviderTreeReader(resolver, Uri.parse(uri)).snapshot(1024); }
}
