package org.totipo.android.provider;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.totipo.android.provider.ProviderSnapshot.*;

/**
 * Read-only SAF transport boundary. Call on a worker thread with an already authorized tree.
 * Caller owns selection/persisted grants and scheduling. No protocol validation or local import.
 * Provider calls can block; resource bounds do not promise a wall-clock timeout.
 */
public final class ProviderTreeReader {
    private static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS};
    private final ContentResolver resolver;
    private final Uri treeUri;
    private final Tree tree;
    private final ProviderTraversal.Source source = new ProviderTraversal.Source() {
        @Override public Listing children(String epoch, String parent, int limit) {
            return queryChildren(epoch, parent, limit);
        }
        @Override public Bytes read(String epoch, ProviderSnapshot.Document document, int maximum) {
            return readCandidate(epoch, document, maximum);
        }
    };

    public ProviderTreeReader(ContentResolver resolver, Uri authorizedTree) {
        this.resolver = Objects.requireNonNull(resolver);
        treeUri = Objects.requireNonNull(authorizedTree);
        if (!DocumentsContract.isTreeUri(treeUri) || !"content".equals(treeUri.getScheme())) {
            throw new IllegalArgumentException("Expected content tree URI");
        }
        tree = new Tree(treeUri.getAuthority(), treeUri.toString(), DocumentsContract.getTreeDocumentId(treeUri));
    }

    public Tree tree() { return tree; }

    /** Eager bounded scan. Request 1024 for object transport; sizes do not imply validity. */
    public Scan snapshot(int expectedMaximum) { return ProviderTraversal.scan(tree, expectedMaximum, source); }

    /** Generic bounded read, e.g. a root VAULT row with maximum 87, associated with its scan epoch. */
    public Bytes readCandidate(String epoch, ProviderSnapshot.Document document, int expectedMaximum) {
        Objects.requireNonNull(epoch);
        Objects.requireNonNull(document);
        ProviderTraversal.checkMaximum(expectedMaximum);
        if (!tree.equals(document.tree())) throw new IllegalArgumentException("Different tree");
        if (document.id() == null || document.locator() == null) {
            return new Bytes(epoch, document, expectedMaximum, ByteState.UNAVAILABLE,
                    new byte[0], Issue.MALFORMED_ROW);
        }
        // Rebuild from opaque ID in the selected tree, never follow a caller-supplied locator.
        return BoundedProviderRead.read(epoch, document, expectedMaximum,
                () -> resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, document.id())));
    }

    private Listing queryChildren(String epoch, String parent, int limit) {
        List<ProviderSnapshot.Document> rows = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        State state = State.COMPLETE;
        if (Thread.currentThread().isInterrupted()) {
            issues.add(Issue.INTERRUPTED);
            return new Listing(epoch, parent, rows, State.INCOMPLETE, issues);
        }
        try (Cursor cursor = resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent), COLUMNS, null, null, null)) {
            if (cursor == null) {
                state = State.UNAVAILABLE;
                issues.add(Issue.NULL_CURSOR);
            } else {
                state = state.combine(extras(cursor, issues));
                // Probe one extra row so exactly limit rows with EOF is still complete.
                while (cursor.moveToNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        state = state.combine(State.INCOMPLETE);
                        issues.add(Issue.INTERRUPTED);
                        break;
                    }
                    if (rows.size() == limit) {
                        state = state.combine(State.INCOMPLETE);
                        issues.add(Issue.RESOURCE_LIMIT);
                        break;
                    }
                    String id = string(cursor, Document.COLUMN_DOCUMENT_ID);
                    String name = string(cursor, Document.COLUMN_DISPLAY_NAME);
                    String mime = string(cursor, Document.COLUMN_MIME_TYPE);
                    String locator = id == null ? null : DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString();
                    rows.add(new ProviderSnapshot.Document(tree, locator, id, parent, name, mime,
                            number(cursor, Document.COLUMN_SIZE), number(cursor, Document.COLUMN_FLAGS)));
                    if (id == null || name == null || mime == null) {
                        state = state.combine(State.INCOMPLETE);
                        if (!issues.contains(Issue.MALFORMED_ROW)) issues.add(Issue.MALFORMED_ROW);
                    }
                }
                state = state.combine(extras(cursor, issues));
            }
        } catch (RuntimeException e) {
            state = State.UNAVAILABLE;
            issues.add(Issue.EXCEPTION);
        }
        return new Listing(epoch, parent, rows, state, issues);
    }

    private static State extras(Cursor cursor, List<Issue> issues) {
        Bundle extras = cursor.getExtras();
        State state = State.COMPLETE;
        if (extras != null && extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
            state = State.INCOMPLETE_LOADING;
            if (!issues.contains(Issue.LOADING)) issues.add(Issue.LOADING);
        }
        if (extras != null && extras.containsKey(DocumentsContract.EXTRA_ERROR)) {
            state = State.UNAVAILABLE;
            if (!issues.contains(Issue.PROVIDER_ERROR)) issues.add(Issue.PROVIDER_ERROR);
        }
        return state;
    }
    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getString(index);
    }
    private static Long number(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index < 0 || cursor.isNull(index) ? null : cursor.getLong(index);
    }
}
