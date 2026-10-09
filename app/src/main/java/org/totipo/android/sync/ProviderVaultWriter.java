package org.totipo.android.sync;

import java.io.OutputStream;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Reviewed root-VAULT create-only operation. Never writes a scanned existing URI.
 * Transport evidence only; all calls run on ProviderIoLane. No cleanup or automatic retry. */
public final class ProviderVaultWriter {
    private ProviderVaultWriter() {}
    public interface Port {
        Document createVault(Document root) throws Exception;
        Document createObjectsDirectory(Document root) throws Exception;
        Document vaultMetadata(Document created) throws Exception;
        OutputStream vaultOutput(Document created) throws Exception;
        Bytes vaultReadBack(Document created) throws Exception;
    }
    public enum Status { VERIFIED, ALREADY_INITIALIZED, PARTIAL, UNCERTAIN, BLOCKED, CANCELLED }
    public record Result(Status status, Scan postflight) {}
    private static boolean writable(SyncFolderBinding.Port transport, String tree, BooleanSupplier cancelled) {
        var grant = transport.grants(tree);
        return !cancelled.getAsBoolean() && grant.read() && grant.write();
    }
    private static void metadata(Document parent, Document created, Document row, String name, boolean directory) {
        if (row == null || !name.equals(row.displayName()) || row.isDirectory() != directory
                || (!directory && !"application/octet-stream".equals(row.mimeType()))
                || !parent.tree().equals(row.tree()) || !Objects.equals(parent.id(), row.parentId())
                || !Objects.equals(created.id(), row.id()) || !Objects.equals(created.locator(), row.locator()))
            throw new IllegalStateException("Created name unavailable");
    }
    private static boolean known(Scan scan, Document created) {
        return created == null || scan.root().rows().stream().anyMatch(row ->
                Objects.equals(row.id(), created.id()) || Objects.equals(row.locator(), created.locator()));
    }
    public static Result initialize(SyncFolderBinding.Port transport, String tree, byte[] exact,
                                    BooleanSupplier cancelled) {
        if (exact.length != 87) throw new IllegalArgumentException("Exact VAULT required");
        boolean verified = false, attempted = false;
        Scan scan = null;
        try {
            if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
            // Independent fresh transport preflight closes the gap after worker-side planning.
            scan = transport.scan(tree);
            byte[] existing = VaultBootstrapEvidence.candidate(scan);
            if (existing != null && !Arrays.equals(existing, exact)) return new Result(Status.BLOCKED, scan);
            Document directory = VaultBootstrapEvidence.namespace(scan, existing == null);
            boolean already = existing != null && directory != null;
            Port port = (Port) transport;
            if (existing == null) {
                if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, scan);
                attempted = true;
                Document root = VaultBootstrapEvidence.root(scan);
                Document created = port.createVault(root);
                if (known(scan, created)) throw new IllegalStateException();
                if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
                metadata(root, created, port.vaultMetadata(created), "vault", false);
                if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
                try (OutputStream output = port.vaultOutput(created)) {
                    if (output == null) throw new IllegalStateException();
                    output.write(exact);
                }
                if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
                Bytes read = port.vaultReadBack(created);
                if (read == null || read.expectedMaximum() != 87 || read.state() != ByteState.PRESENT
                        || !created.equals(read.document()) || !Arrays.equals(exact, read.bytes())) throw new IllegalStateException();
            }
            if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
            scan = transport.scan(tree);
            if (!Arrays.equals(exact, VaultBootstrapEvidence.candidate(scan))) throw new IllegalStateException();
            verified = true;
            directory = VaultBootstrapEvidence.namespace(scan, false);
            if (directory == null) {
                if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
                Document root = VaultBootstrapEvidence.root(scan);
                attempted = true;
                Document created = port.createObjectsDirectory(root);
                if (known(scan, created)) throw new IllegalStateException();
                if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
                metadata(root, created, port.vaultMetadata(created), "objects-v1", true);
            }
            if (!writable(transport, tree, cancelled)) return new Result(Status.CANCELLED, null);
            scan = transport.scan(tree);
            if (!Arrays.equals(exact, VaultBootstrapEvidence.candidate(scan))) return new Result(Status.UNCERTAIN, scan);
            if (VaultBootstrapEvidence.namespace(scan, false) == null) throw new IllegalStateException();
            return new Result(already ? Status.ALREADY_INITIALIZED : Status.VERIFIED, scan);
        } catch (Throwable failure) {
            return new Result(cancelled.getAsBoolean() ? Status.CANCELLED : verified ? Status.PARTIAL
                    : attempted ? Status.UNCERTAIN : Status.BLOCKED, null);
        }
    }
}
