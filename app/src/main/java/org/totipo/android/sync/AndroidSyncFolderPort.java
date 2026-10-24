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
public final class AndroidSyncFolderPort implements SyncFolderBinding.Port, ProviderObjectWriter.Port, ProviderVaultWriter.Port, SyncGrantOwnership.Metadata {
    private final ContentResolver resolver;
    private final SharedPreferences preferences;
    private final String localId;
    private final SyncGrantOwnership ownership;
    private boolean uncertain;
    public static SyncGrantOwnership ownership(Context context, org.totipo.android.VaultRegistry registry) {
        ContentResolver resolver = context.getContentResolver();
        return new SyncGrantOwnership(() -> {
            try {
                var result = new java.util.LinkedHashMap<String, SyncGrantOwnership.Metadata>();
                for (var entry : registry.bindingEntries())
                    result.put(entry.id(), new AndroidSyncFolderPort(context, entry.id(), null));
                return result;
            } catch (java.io.IOException failure) { throw new IllegalStateException("Registry needs recovery"); }
        }, new SyncGrantOwnership.Platform() {
            public boolean sameFolder(String first, String second) {
                try {
                    Uri a = Uri.parse(first), b = Uri.parse(second);
                    return java.util.Objects.equals(a.getAuthority(), b.getAuthority())
                            && DocumentsContract.getTreeDocumentId(a).equals(DocumentsContract.getTreeDocumentId(b));
                } catch (RuntimeException malformed) { return first.equals(second); }
            }
            public boolean independent(String uri) {
                // Android offers no general proof of independence for SAF aliases or
                // overlapping trees. Retain while any different platform grant exists.
                return resolver.getPersistedUriPermissions().stream().allMatch(g -> g.getUri().toString().equals(uri));
            }
            public void release(String uri) {
                for (var grant : resolver.getPersistedUriPermissions()) if (grant.getUri().toString().equals(uri))
                    resolver.releasePersistableUriPermission(grant.getUri(), flags(grant.isReadPermission(), grant.isWritePermission()));
                if (resolver.getPersistedUriPermissions().stream().anyMatch(g -> g.getUri().toString().equals(uri)))
                    throw new IllegalStateException("Permission release remains unconfirmed");
            }
        });
    }
    public AndroidSyncFolderPort(Context context, String localId) {
        this(context, localId, ((org.totipo.android.TotipoApplication) context.getApplicationContext()).syncGrantOwnership());
    }
    private AndroidSyncFolderPort(Context context, String localId, SyncGrantOwnership ownership) {
        org.totipo.android.VaultRegistry.validateId(localId);
        this.localId = localId; this.ownership = ownership;
        resolver = context.getContentResolver();
        preferences = context.getSharedPreferences("sync_binding_mv1_" + localId, Context.MODE_PRIVATE);
    }
    private AndroidProviderVaultPort bootstrap() { return new AndroidProviderVaultPort(resolver, this); }
    public org.totipo.android.provider.ProviderSnapshot.Document createVault(org.totipo.android.provider.ProviderSnapshot.Document root) throws Exception { return bootstrap().createVault(root); }
    public org.totipo.android.provider.ProviderSnapshot.Document createObjectsDirectory(org.totipo.android.provider.ProviderSnapshot.Document root) throws Exception { return bootstrap().createObjectsDirectory(root); }
    public org.totipo.android.provider.ProviderSnapshot.Document vaultMetadata(org.totipo.android.provider.ProviderSnapshot.Document created) { return bootstrap().vaultMetadata(created); }
    public java.io.OutputStream vaultOutput(org.totipo.android.provider.ProviderSnapshot.Document created) throws Exception { return bootstrap().vaultOutput(created); }
    public org.totipo.android.provider.ProviderSnapshot.Bytes vaultReadBack(org.totipo.android.provider.ProviderSnapshot.Document created) { return bootstrap().vaultReadBack(created); }
    public boolean pendingPublication() {
        return preferences.getBoolean("binding_recovery", false)
                ? preferences.getBoolean("recovery_pending", false) : preferences.getBoolean("pending_publication", false);
    }
    public void pendingPublication(boolean pending) {
        if (!preferences.edit().putBoolean("pending_publication", pending).commit())
            throw new IllegalStateException("Retry hint could not be saved");
    }
    public Object configurationLock() { return ownership == null ? this : ownership; }
    public SyncGrantOwnership.Use retain(String uri) {
        if (ownership == null) throw new IllegalStateException("Permission owner unavailable");
        return ownership.admit(localId, uri);
    }
    public void selectionAllowed(String uri) {
        if (ownership == null) throw new IllegalStateException("Permission owner unavailable");
        ownership.checkSelection(localId, uri);
    }
    public boolean uncertain() {
        try { return uncertain || preferences.getBoolean("binding_recovery", false); }
        catch (RuntimeException malformed) { return true; }
    }
    public java.util.Set<String> retained() {
        return new java.util.HashSet<>(preferences.getStringSet("retained_grants", java.util.Set.of()));
    }
    public boolean retained(java.util.Set<String> uris) {
        return preferences.edit().putStringSet("retained_grants", new java.util.HashSet<>(uris)).commit();
    }
    public String configurationNotice() {
        try {
            if (uncertain()) return "Folder configuration needs recovery. Choose the intended folder again or retry Disconnect. Permissions retained.";
            var unused = retained();
            Stored current = load(); if (current != null) unused.remove(current.uri());
            return unused.isEmpty() ? "" : "Unused folder permissions may be retained for safety. No external data was removed.";
        } catch (RuntimeException malformed) {
            return "Folder configuration needs recovery. Permissions retained.";
        }
    }
    private static boolean committed(SharedPreferences.Editor edit) {
        try { return edit.commit(); }
        catch (RuntimeException unavailable) { return false; }
    }
    public Stored load() {
        try {
            if (preferences.getBoolean("binding_recovery", false)) {
                if (!preferences.getBoolean("recovery_configured", false)) return new Stored("", false, false);
                return new Stored(preferences.getString("recovery_uri", ""),
                        preferences.getBoolean("recovery_read", false), preferences.getBoolean("recovery_write", false));
            }
            if (!preferences.contains("tree_uri")) return null;
            if (preferences.getInt("schema", 0) != 1) return new Stored("", false, false);
            return new Stored(preferences.getString("tree_uri", ""),
                    preferences.getBoolean("read", false), preferences.getBoolean("write", false));
        } catch (RuntimeException malformed) { return new Stored("", false, false); }
    }
    public boolean save(Stored value) {
        synchronized (configurationLock()) {
            Stored old = load();
            // Secure the cleanup record BEFORE retiring a historical/recovered
            // binding too, even when this process did not originally take its grant.
            if (ownership != null && old != null && validTree(old.uri())) {
                try { ownership.remember(this, old.uri()); }
                catch (RuntimeException unavailable) { return false; }
            }
            var journal = preferences.edit().putBoolean("binding_recovery", true)
                    .putBoolean("recovery_configured", old != null)
                    .putBoolean("recovery_pending", pendingPublication())
                    .putString("recovery_uri", old == null ? "" : old.uri())
                    .putBoolean("recovery_read", old != null && old.readable())
                    .putBoolean("recovery_write", old != null && old.writable());
            if (!committed(journal)) { uncertain = true; return false; }
            var edit = preferences.edit().remove("schema").remove("tree_uri").remove("read").remove("write");
            if (value != null) edit.putInt("schema", 1).putString("tree_uri", value.uri())
                    .putBoolean("read", value.readable()).putBoolean("write", value.writable());
            else edit.putBoolean("pending_publication", false);
            edit.putBoolean("binding_recovery", false);
            if (!committed(edit)) {
                // SharedPreferences changes memory even when disk persistence fails.
                // Restore the recovery marker in memory/disk; keep the original URI
                // and all acquired grants until an explicit successful configuration.
                uncertain = true;
                committed(preferences.edit().putBoolean("binding_recovery", true));
                return false;
            }
            uncertain = false;
            return true;
        }
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
        synchronized (configurationLock()) {
            if (ownership == null) throw new IllegalStateException("Permission owner unavailable");
            ownership.remember(this, uri);
            resolver.takePersistableUriPermission(Uri.parse(uri), flags(read, write));
        }
    }
    public void release(String uri, boolean read, boolean write) {
        if (!read && !write) return;
        synchronized (configurationLock()) {
            if (ownership == null) return; // Metadata-only inspection never revokes.
            ownership.remember(this, uri);
            ownership.cleanup();
        }
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
