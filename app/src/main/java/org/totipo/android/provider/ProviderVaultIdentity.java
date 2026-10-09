package org.totipo.android.provider;

import java.util.List;
import org.totipo.Totipo;
import org.totipo.VaultId;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Structural identity only: no authentication, freshness, password, KDF or store access.
 * Worker-side preflight shared by Import and Publish. Multiple root VAULT rows are
 * conservatively ambiguous, including byte-identical duplicates; no row is selected.
 */
public final class ProviderVaultIdentity {
    private ProviderVaultIdentity() {}
    public enum Status {
        MATCH(null), MISSING("Sync folder has no Totipo vault."),
        UNVERIFIABLE("Sync folder vault cannot be verified."),
        DIFFERENT("Sync folder belongs to a different Totipo vault.");
        private final String message;
        Status(String message) { this.message = message; }
        public String message() { return message; }
    }
    public static Status classify(Scan scan, VaultId local) {
        if (!scan.epoch().equals(scan.root().epoch()) || !scan.tree().rootId().equals(scan.root().parentId())
                || scan.root().state() != State.COMPLETE) return Status.UNVERIFIABLE;
        List<Document> rows = scan.root().rows().stream()
                .filter(row -> "vault".equals(row.displayName())).collect(java.util.stream.Collectors.toList());
        if (rows.isEmpty()) return Status.MISSING;
        if (rows.size() != 1 || scan.vaultCandidates().size() != 1) return Status.UNVERIFIABLE;
        Document row = rows.get(0);
        Bytes candidate = scan.vaultCandidates().get(0);
        if (row.isDirectory() || row.mimeType() == null || row.id() == null || row.locator() == null
                || !scan.tree().equals(row.tree()) || !scan.tree().rootId().equals(row.parentId())
                || !row.equals(candidate.document()) || !scan.epoch().equals(candidate.epoch())
                || candidate.expectedMaximum() != 87 || candidate.state() != ByteState.PRESENT
                || candidate.bytes().length != 87) return Status.UNVERIFIABLE;
        try { return Totipo.vaultId(candidate.bytes()).equals(local) ? Status.MATCH : Status.DIFFERENT; }
        catch (IllegalArgumentException invalid) { return Status.UNVERIFIABLE; }
    }
    public static final class Blocked extends IllegalStateException {
        private final Status status;
        public Blocked(Status status) { super(status.message()); this.status = status; }
        public Status status() { return status; }
    }
}
