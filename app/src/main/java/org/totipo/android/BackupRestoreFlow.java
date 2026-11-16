package org.totipo.android;

import java.io.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.totipo.backup.*;

/** One foreground restore journey. No Activity, password, URI or session is retained.
 * Worker methods are serialized by the Application worker; cancellation never releases
 * its admission while entered ingress/install/close work is still running. */
final class BackupRestoreFlow {
    enum Stage { PICKING, READING, PASSWORD, AUTHENTICATING, CONFIRM, INSTALLING, DONE, FAILED, CANCELLED }
    interface Source { InputStream open() throws IOException; }
    private static final int MAX_ZIP = 6 * 1024 * 1024;
    private final LongSupplier clock;
    private final long deadline;
    private final String recoveryId;
    private volatile Stage stage = Stage.PICKING;
    private volatile boolean cancelled;
    private byte[] archive;
    private int length;
    private volatile BackupRestore plan;
    private volatile BackupSummary summary;
    private volatile RestoreResult result;
    private volatile VaultRegistry.Entry duplicate;
    private volatile String localId;
    private volatile String notice = "Select a portable backup ZIP. Restore keeps its original identity, password and encrypted history.";
    BackupRestoreFlow(String recoveryId, LongSupplier clock) {
        this.recoveryId = recoveryId; this.clock = clock; deadline = clock.getAsLong() + 15 * 60 * 1000;
    }
    boolean cancelled() { return cancelled; }
    Stage stage() { return stage; }
    String notice() { return notice; }
    String localId() { return localId; }
    VaultRegistry.Entry duplicate() { return duplicate; }
    RestoreResult result() { return result; }
    BackupSummary summary() { return summary; }
    boolean active() { return stage != Stage.DONE && stage != Stage.FAILED && stage != Stage.CANCELLED; }
    private void current() throws IOException {
        if (cancelled || clock.getAsLong() >= deadline) { cancel(); throw new IOException("Restore retired"); }
    }
    // UI admission methods do no blocking work and consume each step once.
    synchronized boolean reading() { if (stage != Stage.PICKING || cancelled) return false; stage = Stage.READING; return true; }
    synchronized boolean authenticating() { if (stage != Stage.PASSWORD || cancelled) return false; stage = Stage.AUTHENTICATING; return true; }
    synchronized boolean installing() { if (stage != Stage.CONFIRM || cancelled || duplicate != null) return false; stage = Stage.INSTALLING; return true; }
    void freeze(Source source) {
        byte[] bytes = new byte[MAX_ZIP + 1]; int size = 0;
        try {
            current();
            try (var input = Objects.requireNonNull(source.open())) {
                while (size < bytes.length) {
                    current(); int count = input.read(bytes, size, bytes.length - size);
                    if (count < 0) break;
                    if (count == 0) throw new IOException("No ingress progress");
                    size += count;
                }
            }
            if (size > MAX_ZIP) throw new IOException("Archive capacity");
            BackupArchives.inspect(new ByteArrayInputStream(bytes, 0, size), BackupLimits.DEFAULT);
            current(); archive = bytes; length = size; bytes = null;
            notice = "Enter the backup's original vault password. All encrypted objects will be authenticated.";
            stage = Stage.PASSWORD;
        } catch (Exception failure) { fail("Could not read a supported complete backup ZIP. Try another copy; no vault was allocated."); }
        finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); if (cancelled) dispose(); }
    }
    void authenticate(char[] password, VaultRegistry registry) {
        try {
            current();
            plan = BackupArchives.prepareRestore(new ByteArrayInputStream(archive, 0, length), BackupLimits.DEFAULT, password);
            summary = plan.summary(); current();
            if (recoveryId != null) {
                var intent = registry.entry(recoveryId).restore();
                if (intent == null || !intent.archiveSha256().equals(summary.archiveSha256())
                        || !intent.vaultId().equals(summary.vaultId().hex())) throw new IOException("Exact archive required");
            }
            duplicate = registry.checkRestoreIdentity(recoveryId, summary.vaultId()); current();
            notice = duplicate == null ? "Authenticated backup: " + summary.objects() + " encrypted history objects. "
                    + (summary.unresolvedReferences() == 0 ? "" : summary.unresolvedReferences() + " unresolved ancestry references. This is limited history. ")
                    + "Restore preserves the original password and starts local only."
                    : duplicate.state() == VaultRegistry.State.ESTABLISHED
                        ? "This vault already exists. Open the existing vault; no history has been added."
                        : "This identity belongs to retained local data. Use its Recovery entry; a second allocation is refused.";
            stage = Stage.CONFIRM;
        } catch (Exception failure) { fail("Could not unlock this backup or establish safe identity evidence. Check the password, exact ZIP and retained-vault Recovery entries. No new vault was allocated."); }
        finally { Arrays.fill(password, '\0'); clearArchive(); if (cancelled || stage == Stage.FAILED) dispose(); }
    }
    void install(String label, VaultRegistry registry, VaultSelection selection) {
        String owned = null;
        try {
            current();
            String maintenance = recoveryId == null ? "00000000000000000000000000000000" : recoveryId;
            selection.beginRestoreMaintenance(maintenance); owned = maintenance;
            current();
            if (recoveryId == null) localId = registry.reserveRestore(label, summary).id();
            else localId = recoveryId;
            current();
            boolean firstAllocation = recoveryId == null || registry.entry(localId).restore().phase() == VaultRegistry.RestorePhase.RESERVED;
            var root = registry.beginRestore(localId, summary);
            current();
            // Existing entries are never accepted as fresh destinations, including empty ones.
            boolean recover = !firstAllocation;
            result = org.totipo.android.reconcile.CoordinatedPrivateStore.restore(root, plan, recover);
            plan = null;
            if (result.outcome() == RestoreResult.Outcome.COMPLETE) {
                registry.installedRestore(localId, result);
                notice = (summary.unresolvedReferences() == 0 ? "" : "Limited history: " + summary.unresolvedReferences() + " unresolved ancestry references. ")
                        + "Installation complete. Open with the original password to finish authenticated observation and registration. Local only; Sync and biometrics were not imported.";
                stage = Stage.DONE;
            } else {
                notice = "Restore " + result.outcome().name() + ". The owned allocation and any encrypted data were retained. Use Restore interrupted backup with the exact original ZIP and password.";
                stage = Stage.FAILED;
            }
        } catch (Exception failure) {
            fail("Restore interrupted or unavailable. Preserve app data. Any reserved allocation remains in Recovery; retry explicitly with the exact original ZIP. Missing or torn provider intent requires storage investigation.");
        } finally {
            dispose();
            if (owned != null) {
                // beginMaintenance may itself reject; only release a successfully held reservation.
                try { selection.endMaintenance(owned); } catch (IllegalStateException notOwned) { /* No ownership acquired. */ }
            }
        }
    }
    void cancel() {
        cancelled = true;
        var active = plan; if (active != null) active.cancel(); // Nonblocking; close belongs to the worker.
        notice = "Restore cancelled. Entered storage work may finish; retained allocations require explicit recovery.";
    }
    void dispose() {
        var active = plan; plan = null; if (active != null) active.close(); clearArchive();
        if (cancelled) stage = Stage.CANCELLED;
    }
    private void clearArchive() { if (archive != null) Arrays.fill(archive, (byte) 0); archive = null; length = 0; }
    private void fail(String message) { notice = message; stage = cancelled ? Stage.CANCELLED : Stage.FAILED; }
}
