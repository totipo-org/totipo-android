package org.totipo.android.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Pure bounded traversal policy; the Source is a package-private deterministic test seam. */
final class ProviderTraversal {
    // Hard caps: 256 root rows, 8 directory listings, 1024 rows per directory,
    // 512 read attempts, 64 KiB + probe per read, 1 MiB reserved per eager scan.
    static final int ROOT_ROWS = 256;
    static final int DIRECTORIES = 8;
    static final int CHILD_ROWS = 1024;
    static final int CANDIDATES = 512;
    static final int MAXIMUM_BYTES = 64 * 1024;
    static final int SCAN_BYTES = 1024 * 1024;
    interface Source {
        Listing children(String epoch, String parentId, int rowLimit);
        Bytes read(String epoch, Document document, int maximum);
    }
    static void checkMaximum(int maximum) {
        if (maximum < 0 || maximum > MAXIMUM_BYTES) throw new IllegalArgumentException("Maximum outside 0..65536");
    }
    static boolean isObjectName(String name) {
        if (name == null || name.length() != 64) return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }
    static Scan scan(Tree tree, int maximum, Source source) {
        checkMaximum(maximum);
        String epoch = ProviderSnapshot.newEpoch();
        Listing root = source.children(epoch, tree.rootId(), ROOT_ROWS);
        State state = root.state();
        List<Directory> directories = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(tree.rootId());
        int candidates = 0;
        int reservedBytes = 0;
        for (Document row : root.rows()) {
            if (!"objects-v1".equals(row.displayName()) || !row.isDirectory()) continue;
            if (directories.size() == DIRECTORIES) {
                state = state.combine(State.INCOMPLETE);
                if (!issues.contains(Issue.RESOURCE_LIMIT)) issues.add(Issue.RESOURCE_LIMIT);
                break;
            }
            Listing children;
            if (row.id() == null || !visited.add(row.id())) {
                children = new Listing(epoch, row.id(), new ArrayList<>(), State.INCOMPLETE,
                        java.util.Collections.singletonList(Issue.REPEATED_DIRECTORY));
            } else {
                children = source.children(epoch, row.id(), CHILD_ROWS);
            }
            state = state.combine(children.state());
            List<Bytes> reads = new ArrayList<>();
            for (Document child : children.rows()) {
                if (child.isDirectory() || !isObjectName(child.displayName())) continue;
                if (candidates == CANDIDATES) {
                    state = state.combine(State.INCOMPLETE);
                    if (!issues.contains(Issue.RESOURCE_LIMIT)) issues.add(Issue.RESOURCE_LIMIT);
                    break;
                }
                candidates++;
                Bytes bytes;
                if (reservedBytes > SCAN_BYTES - (maximum + 1)) {
                    bytes = new Bytes(epoch, child, maximum, ByteState.UNAVAILABLE, new byte[0], Issue.RESOURCE_LIMIT);
                    if (!issues.contains(Issue.RESOURCE_LIMIT)) issues.add(Issue.RESOURCE_LIMIT);
                } else {
                    reservedBytes += maximum + 1;
                    bytes = source.read(epoch, child, maximum);
                }
                reads.add(bytes);
                if (bytes.state() == ByteState.UNAVAILABLE || bytes.state() == ByteState.MISSING) {
                    state = state.combine(State.INCOMPLETE);
                }
            }
            directories.add(new Directory(row, children, reads));
        }
        return new Scan(epoch, tree, root, directories, state, issues);
    }
}
