package org.totipo.android.sync;

import java.util.*;
import org.totipo.Totipo;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Detached operation-specific evidence. Structural recognition belongs on the vault worker. */
public final class VaultBootstrapEvidence {
    private VaultBootstrapEvidence() {}
    public static boolean completeRoot(Scan scan) {
        return scan != null && scan.root().state() == State.COMPLETE
                && scan.epoch().equals(scan.root().epoch()) && scan.tree().rootId().equals(scan.root().parentId());
    }
    public static byte[] candidate(Scan scan) {
        if (!completeRoot(scan)) throw new IllegalStateException("Sync folder vault cannot be verified.");
        var rows = scan.root().rows().stream().filter(r -> "vault".equals(r.displayName())).collect(java.util.stream.Collectors.toList());
        if (rows.isEmpty()) return null;
        if (rows.size() != 1 || scan.vaultCandidates().size() != 1) throw new IllegalStateException("Sync folder vault cannot be verified.");
        var row = rows.get(0); var read = scan.vaultCandidates().get(0);
        if (row.isDirectory() || row.mimeType() == null || row.id() == null || row.locator() == null
                || !scan.tree().equals(row.tree()) || !scan.tree().rootId().equals(row.parentId())
                || !row.equals(read.document()) || !scan.epoch().equals(read.epoch())
                || read.expectedMaximum() != 87 || read.state() != ByteState.PRESENT || read.bytes().length != 87)
            throw new IllegalStateException("Sync folder vault cannot be verified.");
        return read.bytes();
    }
    public static byte[] joinCandidate(Scan scan) {
        byte[] exact = candidate(scan);
        if (exact == null) throw new IllegalStateException("Sync folder has no Totipo vault.");
        try { Totipo.vaultId(exact); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Sync folder vault cannot be verified."); }
        return exact;
    }
    /** Positive absence or one exact directory with complete direct-child coverage.
     * Every plausible name, including wrong-kind children, vetoes a new identity anchor. */
    public static Document namespace(Scan scan, boolean orphanVeto) {
        if (!completeRoot(scan)) throw new IllegalStateException("Sync folder cannot be initialized safely.");
        var rows = scan.root().rows().stream().filter(r -> "objects-v1".equals(r.displayName())).collect(java.util.stream.Collectors.toList());
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw new IllegalStateException("Sync folder cannot be initialized safely.");
        var row = rows.get(0);
        if (!row.isDirectory() || row.id() == null || row.locator() == null || !scan.tree().equals(row.tree())
                || !scan.tree().rootId().equals(row.parentId())) throw new IllegalStateException("Sync folder cannot be initialized safely.");
        var dirs = scan.directories().stream().filter(d -> row.equals(d.document())).collect(java.util.stream.Collectors.toList());
        if (dirs.size() != 1 || dirs.get(0).children().state() != State.COMPLETE
                || !scan.epoch().equals(dirs.get(0).children().epoch()) || !row.id().equals(dirs.get(0).children().parentId()))
            throw new IllegalStateException("Sync folder cannot be initialized safely.");
        if (orphanVeto && dirs.get(0).children().rows().stream().anyMatch(r -> r.displayName() != null && r.displayName().matches("[0-9a-f]{64}")))
            throw new IllegalStateException("Sync folder contains Totipo token data but no vault. Totipo will not initialize it automatically.");
        return row;
    }
    public static Document root(Scan scan) {
        return new Document(scan.tree(), scan.tree().locator(), scan.tree().rootId(), null, null,
                "vnd.android.document/directory", null, null);
    }
}
