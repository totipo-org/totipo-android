package org.totipo.android.debug;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Direct-child queries only. IDs stay opaque; never interpreted as paths. */
final class ProbeDocuments {
    static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS};
    static final class Row {
        final String id, name, mime;
        final Long size, flags;
        Row(Cursor c) {
            id = string(c, Document.COLUMN_DOCUMENT_ID);
            name = string(c, Document.COLUMN_DISPLAY_NAME);
            mime = string(c, Document.COLUMN_MIME_TYPE);
            size = number(c, Document.COLUMN_SIZE);
            flags = number(c, Document.COLUMN_FLAGS);
        }
    }
    static final class Snapshot {
        final List<Row> rows = new ArrayList<>();
        boolean loading;
        boolean complete = true;
        boolean providerError;
        List<String> ids() {
            List<String> ids = new ArrayList<>();
            for (Row row : rows) ids.add(row.id);
            return ids;
        }
        int matches(String name) {
            List<String> names = new ArrayList<>();
            for (Row row : rows) names.add(row.name);
            return ProbeLogic.exactMatches(names, name);
        }
        void requireObservation() throws IOException {
            if (!complete || loading || providerError) throw new IOException("Incomplete/unknown enumeration");
        }
    }
    private final ContentResolver resolver;
    private final Uri tree;
    ProbeDocuments(ContentResolver resolver, Uri tree) { this.resolver = resolver; this.tree = tree; }
    Uri root() { return document(DocumentsContract.getTreeDocumentId(tree)); }
    Uri document(String id) { return DocumentsContract.buildDocumentUriUsingTree(tree, id); }
    Row query(Uri uri) throws IOException {
        try (Cursor cursor = resolver.query(uri, COLUMNS, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("Null/empty metadata cursor");
            return new Row(cursor);
        }
    }
    // Only an inlined column name: no API-37 method is invoked. Unsupported projection
    // failures are caught by the caller and do not prevent the ordinary experiments.
    @android.annotation.SuppressLint("InlinedApi")
    Long syncState(Uri uri) throws IOException {
        // Optional API-37 metadata; older providers may reject this projection.
        try (Cursor cursor = resolver.query(uri, new String[]{Document.COLUMN_CONTENT_SYNC_STATE_FLAGS}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IOException("No optional sync-state metadata");
            return number(cursor, Document.COLUMN_CONTENT_SYNC_STATE_FLAGS);
        }
    }
    Snapshot children() throws IOException {
        Uri uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        Snapshot result = new Snapshot();
        try (Cursor cursor = resolver.query(uri, COLUMNS, null, null, null)) {
            if (cursor == null) throw new IOException("Null children cursor: provider unavailable/unknown");
            while (cursor.moveToNext()) {
                if (result.rows.size() == 10000) { result.complete = false; break; }
                Row row = new Row(cursor);
                if (row.id == null || row.name == null) result.complete = false;
                result.rows.add(row);
            }
            Bundle extras = cursor.getExtras();
            result.loading = extras != null && extras.getBoolean(DocumentsContract.EXTRA_LOADING, false);
            result.providerError = extras != null && extras.containsKey(DocumentsContract.EXTRA_ERROR);
        }
        return result;
    }
    private static String string(Cursor c, String column) {
        int index = c.getColumnIndex(column);
        return index < 0 || c.isNull(index) ? null : c.getString(index);
    }
    private static Long number(Cursor c, String column) {
        int index = c.getColumnIndex(column);
        return index < 0 || c.isNull(index) ? null : c.getLong(index);
    }
}
