package org.totipo.android.provider;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.totipo.android.provider.ProviderSnapshot.*;
import static org.junit.Assert.*;

public final class ProviderSnapshotTest {
    private static final Tree TREE = new Tree("provider", "opaque-tree", "root");
    private static final String NAME = String.join("", Collections.nCopies(64, "a"));
    private static Document row(String id, String parent, String name, boolean directory) {
        return new Document(TREE, "opaque:" + id, id, parent, name,
                directory ? "vnd.android.document/directory" : "application/octet-stream", Long.MAX_VALUE, null);
    }
    private static final class Source implements ProviderTraversal.Source {
        final Map<String, List<Document>> rows = new HashMap<>();
        final Map<String, State> states = new HashMap<>();
        final List<String> parents = new ArrayList<>();
        int readCalls;
        boolean missing;
        @Override public Listing children(String epoch, String parent, int limit) {
            parents.add(parent);
            List<Document> all = rows.getOrDefault(parent, Collections.emptyList());
            return new Listing(epoch, parent, all.subList(0, Math.min(limit, all.size())),
                    all.size() > limit ? State.INCOMPLETE : states.getOrDefault(parent, State.COMPLETE),
                    all.size() > limit ? Collections.singletonList(Issue.RESOURCE_LIMIT) : Collections.emptyList());
        }
        @Override public Bytes read(String epoch, Document document, int maximum) {
            readCalls++;
            return new Bytes(epoch, document, maximum, missing ? ByteState.MISSING : ByteState.PRESENT,
                    missing ? new byte[0] : new byte[maximum], Issue.NONE);
        }
    }
    @Test public void namesAreOnlyLowercaseHexAndExactLength() {
        assertTrue(ProviderTraversal.isObjectName(NAME));
        assertTrue(ProviderTraversal.isObjectName(String.join("", Collections.nCopies(64, "0"))));
        for (String invalid : Arrays.asList(null, "", NAME + "a", NAME.substring(1), NAME.toUpperCase(),
                NAME.substring(1) + "g", NAME.substring(1) + "/", NAME.substring(1) + "\n")) {
            assertFalse(ProviderTraversal.isObjectName(invalid));
        }
    }
    @Test public void duplicatesObstructionsAndEpochsArePreservedWithoutRecursion() {
        Source source = new Source();
        source.rows.put("root", Arrays.asList(row("A", "root", "objects-v1", true),
                row("B", "root", "objects-v1", true), row("file", "root", "objects-v1", false),
                row("other", "root", "OBJECTS-V1", true)));
        source.rows.put("A", Arrays.asList(row("X1", "A", NAME, false), row("X2", "A", NAME, false),
                row("nested", "A", NAME, true), row("noise", "A", "noise", false)));
        source.rows.put("B", Collections.singletonList(row("X3", "B", NAME, false)));
        Scan scan = ProviderTraversal.scan(TREE, 1024, source);
        assertEquals(State.COMPLETE, scan.state());
        assertEquals(4, scan.root().rows().size());
        assertEquals(2, scan.directories().size());
        assertEquals(2, scan.directories().get(0).candidates().size());
        assertEquals("X2", scan.directories().get(0).candidates().get(1).document().id());
        assertEquals("X3", scan.directories().get(1).candidates().get(0).document().id());
        assertEquals(Arrays.asList("root", "A", "B"), source.parents);
        assertEquals(scan.epoch(), scan.root().epoch());
        for (Directory directory : scan.directories()) {
            assertEquals(scan.epoch(), directory.children().epoch());
            for (Bytes bytes : directory.candidates()) assertEquals(scan.epoch(), bytes.epoch());
        }
        assertNotEquals(scan.epoch(), ProviderTraversal.scan(TREE, 87, source).epoch());
        assertThrows(UnsupportedOperationException.class, () -> scan.root().rows().clear());
    }
    @Test public void statesDoNotClaimAbsenceAndChildFailureIsLocal() {
        for (State first : State.values()) for (State second : State.values()) {
            assertEquals(first.combine(second), second.combine(first));
            assertEquals(first, first.combine(State.COMPLETE));
        }
        Source source = new Source();
        source.rows.put("root", Arrays.asList(row("A", "root", "objects-v1", true), row("B", "root", "objects-v1", true)));
        source.states.put("root", State.INCOMPLETE_LOADING);
        source.states.put("B", State.UNAVAILABLE);
        source.rows.put("A", Collections.singletonList(row("X", "A", NAME, false)));
        Scan scan = ProviderTraversal.scan(TREE, 87, source);
        assertEquals(State.UNAVAILABLE, scan.state());
        assertEquals(State.INCOMPLETE_LOADING, scan.root().state());
        assertEquals(ByteState.PRESENT, scan.directories().get(0).candidates().get(0).state());
        assertEquals(State.UNAVAILABLE, scan.directories().get(1).children().state());
        source.states.clear(); source.missing = true;
        assertEquals(State.INCOMPLETE, ProviderTraversal.scan(TREE, 87, source).state());
    }
    @Test public void cyclesAreEvidenceAndNeverDescended() {
        Source source = new Source();
        source.rows.put("root", Arrays.asList(row("root", "root", "objects-v1", true),
                row("A", "root", "objects-v1", true), row("A", "root", "objects-v1", true)));
        Scan scan = ProviderTraversal.scan(TREE, 1, source);
        assertEquals(State.INCOMPLETE, scan.state());
        assertEquals(3, scan.directories().size());
        assertEquals(Arrays.asList("root", "A"), source.parents);
        assertEquals(Issue.REPEATED_DIRECTORY, scan.directories().get(2).children().issues().get(0));
    }
    @Test public void allBudgetsBoundWorkAndMarkIncomplete() {
        Source source = new Source();
        List<Document> directories = new ArrayList<>();
        for (int i = 0; i <= ProviderTraversal.ROOT_ROWS; i++) directories.add(row("D" + i, "root", "objects-v1", true));
        source.rows.put("root", directories);
        Scan scan = ProviderTraversal.scan(TREE, 1, source);
        assertEquals(ProviderTraversal.ROOT_ROWS, scan.root().rows().size());
        assertEquals(ProviderTraversal.DIRECTORIES, scan.directories().size());
        assertEquals(State.INCOMPLETE, scan.state());
        assertTrue(scan.issues().contains(Issue.RESOURCE_LIMIT));
        source.rows.put("root", Collections.singletonList(directories.get(0)));
        List<Document> children = new ArrayList<>();
        for (int i = 0; i <= ProviderTraversal.CHILD_ROWS; i++) children.add(row("C" + i, "D0", NAME, false));
        source.rows.put("D0", children);
        source.readCalls = 0;
        scan = ProviderTraversal.scan(TREE, 1024, source);
        assertEquals(ProviderTraversal.CHILD_ROWS, scan.directories().get(0).children().rows().size());
        assertEquals(ProviderTraversal.CANDIDATES, source.readCalls);
        assertEquals(State.INCOMPLETE, scan.state());
        source.readCalls = 0;
        scan = ProviderTraversal.scan(TREE, ProviderTraversal.MAXIMUM_BYTES, source);
        assertEquals(ProviderTraversal.SCAN_BYTES / (ProviderTraversal.MAXIMUM_BYTES + 1), source.readCalls);
        assertEquals(Issue.RESOURCE_LIMIT, scan.directories().get(0).candidates().get(source.readCalls).issue());
        assertThrows(IllegalArgumentException.class, () -> ProviderTraversal.scan(TREE, -1, source));
        assertThrows(IllegalArgumentException.class, () -> ProviderTraversal.scan(TREE, Integer.MAX_VALUE, source));
    }
    @Test public void actualBytesOverflowEofAndClosure() {
        Document document = row("X", "A", NAME, false);
        for (int maximum : new int[] {0, 87, 1024}) {
            for (int length : new int[] {0, maximum, maximum + 1, maximum + 20}) {
                final boolean[] closed = {false};
                InputStream input = new ByteArrayInputStream(new byte[length]) {
                    @Override public void close() { closed[0] = true; }
                };
                Bytes bytes = BoundedProviderRead.read("epoch", document, maximum, () -> input);
                assertEquals(length < maximum ? ByteState.SHORT : length == maximum ? ByteState.PRESENT : ByteState.OVERSIZED, bytes.state());
                assertEquals(Math.min(length, maximum + 1), bytes.bytes().length);
                assertTrue(closed[0]);
                if (bytes.bytes().length > 0) { byte[] copy = bytes.bytes(); copy[0] = 99; assertEquals(0, bytes.bytes()[0]); }
            }
        }
    }
    @Test public void unavailablePreservesPrefixAndClosesOnFailure() {
        Document document = row("X", "A", NAME, false);
        final boolean[] closed = {false};
        InputStream failing = new InputStream() {
            int reads;
            @Override public int read() throws IOException { if (reads++ == 0) return 7; throw new IOException(); }
            @Override public void close() { closed[0] = true; }
        };
        Bytes bytes = BoundedProviderRead.read("epoch", document, 87, () -> failing);
        assertEquals(ByteState.UNAVAILABLE, bytes.state());
        assertArrayEquals(new byte[]{7}, bytes.bytes());
        assertTrue(closed[0]);
        assertEquals(ByteState.MISSING, BoundedProviderRead.read("e", document, 87, () -> { throw new FileNotFoundException(); }).state());
        assertEquals(ByteState.UNAVAILABLE, BoundedProviderRead.read("e", document, 87, () -> null).state());
        Thread.currentThread().interrupt();
        try {
            bytes = BoundedProviderRead.read("e", document, 87, () -> new ByteArrayInputStream(new byte[87]));
            assertEquals(ByteState.UNAVAILABLE, bytes.state());
            assertEquals(Issue.INTERRUPTED, bytes.issue());
        } finally { Thread.interrupted(); }
    }
    @Test public void zeroReturningStreamsAndCloseFailureAreHonest() {
        Document document = row("X", "A", NAME, false);
        InputStream zeroReturning = new InputStream() {
            int remaining = 87;
            @Override public int read(byte[] buffer, int offset, int length) { return 0; }
            @Override public int read() { return remaining-- > 0 ? 3 : -1; }
        };
        assertEquals(ByteState.PRESENT, BoundedProviderRead.read("e", document, 87, () -> zeroReturning).state());
        InputStream closeFailure = new ByteArrayInputStream(new byte[87]) {
            @Override public void close() throws IOException { throw new IOException(); }
        };
        Bytes bytes = BoundedProviderRead.read("e", document, 87, () -> closeFailure);
        assertEquals(ByteState.UNAVAILABLE, bytes.state());
        assertEquals(87, bytes.bytes().length);
    }
    @Test public void exactBudgetsAndLoadingAloneAreDistinctFromExhaustion() {
        Source source = new Source();
        List<Document> root = new ArrayList<>();
        for (int i = 0; i < ProviderTraversal.DIRECTORIES; i++) root.add(row("D" + i, "root", "objects-v1", true));
        source.rows.put("root", root);
        List<Document> children = new ArrayList<>();
        for (int i = 0; i < ProviderTraversal.CANDIDATES; i++) children.add(row("C" + i, "D0", NAME, false));
        source.rows.put("D0", children);
        assertEquals(State.COMPLETE, ProviderTraversal.scan(TREE, 1024, source).state());
        source.states.put("root", State.INCOMPLETE_LOADING);
        assertEquals(State.INCOMPLETE_LOADING, ProviderTraversal.scan(TREE, 1024, source).state());
        source.states.put("root", State.UNAVAILABLE);
        Scan scan = ProviderTraversal.scan(TREE, 1024, source);
        assertEquals(State.UNAVAILABLE, scan.state());
        assertEquals(ProviderTraversal.CANDIDATES, scan.directories().get(0).candidates().size());
    }
    @Test public void productionBoundaryContainsNoMutationOrLocalImport() throws Exception {
        Path directory = Path.of("src/main/java/org/totipo/android/provider");
        try (var files = Files.walk(directory)) {
            for (Path path : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String code = Files.readString(path);
                for (String forbidden : Arrays.asList("createDocument", "renameDocument", "moveDocument", "deleteDocument",
                        "removeDocument", "openOutputStream", "openFileDescriptor", "LocalReplicaOwner", "org.totipo.storage", "org.totipo.format")) {
                    assertFalse(path + " contains " + forbidden, code.contains(forbidden));
                }
            }
        }
    }
}
