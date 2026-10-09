package org.totipo.android.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable transport evidence only. Same epoch does not mean an atomic provider snapshot. */
public final class ProviderSnapshot {
    private ProviderSnapshot() {}

    public enum State {
        COMPLETE, INCOMPLETE_LOADING, INCOMPLETE, UNAVAILABLE;
        public State combine(State other) { return ordinal() >= other.ordinal() ? this : other; }
    }
    public enum Issue { NONE, LOADING, PROVIDER_ERROR, NULL_CURSOR, EXCEPTION, MALFORMED_ROW,
        RESOURCE_LIMIT, REPEATED_DIRECTORY, INTERRUPTED }
    /** MISSING means opening reported FileNotFoundException; it is not proof of protocol absence. */
    public enum ByteState { PRESENT, SHORT, OVERSIZED, MISSING, UNAVAILABLE }

    public record Tree(String authority, String locator, String rootId) {
        public Tree { Objects.requireNonNull(authority); Objects.requireNonNull(locator); Objects.requireNonNull(rootId); }
    }
    /** All locators and IDs are opaque; parentId is the queried parent, never an inferred path. */
    public record Document(Tree tree, String locator, String id, String parentId,
                           String displayName, String mimeType, Long reportedSize, Long flags) {
        public Document { Objects.requireNonNull(tree); }
        public boolean isDirectory() { return "vnd.android.document/directory".equals(mimeType); }
    }
    public record Listing(String epoch, String parentId, List<Document> rows, State state, List<Issue> issues) {
        public Listing { rows = frozen(rows); issues = frozen(issues); }
    }
    /** bytes is the observed prefix, including the overflow byte when present; never protocol validity. */
    public record Bytes(String epoch, Document document, int expectedMaximum, ByteState state,
                        byte[] bytes, Issue issue) {
        public Bytes { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    public record Directory(Document document, Listing children, List<Bytes> candidates) {
        public Directory { candidates = frozen(candidates); }
    }
    public record Scan(String epoch, Tree tree, Listing root, List<Directory> directories, State state, List<Issue> issues, List<Bytes> vaultCandidates) {
        public Scan { directories = frozen(directories); issues = frozen(issues); vaultCandidates = frozen(vaultCandidates); }
        public Scan(String epoch, Tree tree, Listing root, List<Directory> directories, State state, List<Issue> issues) {
            this(epoch, tree, root, directories, state, issues, List.of());
        }
    }
    public static String newEpoch() { return UUID.randomUUID().toString(); }
    private static <T> List<T> frozen(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
