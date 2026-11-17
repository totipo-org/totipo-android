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
    private volatile String notice = "Choose a Totipo backup ZIP. You’ll need its original vault password.";
    BackupRestoreFlow(String recoveryId, LongSupplier clock) {
        this.recoveryId = recoveryId; this.clock = clock; deadline = clock.getAsLong() + 15 * 60 * 1000;
    }
    boolean cancelled() { return cancelled; }
    boolean currentJourney() { return !cancelled && clock.getAsLong() < deadline; }
    boolean recovery() { return recoveryId != null; }
    boolean needsHistoryReview() { return stage == Stage.CONFIRM && duplicate == null && summary.unresolvedReferences() > 0; }
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
    synchronized boolean reading() { if (stage != Stage.PICKING || cancelled) return false; notice = "Reading backup…"; stage = Stage.READING; return true; }
    synchronized boolean authenticating() { if (stage != Stage.PASSWORD || cancelled) return false; notice = "Checking backup…"; stage = Stage.AUTHENTICATING; return true; }
    synchronized boolean installing() { if (stage != Stage.CONFIRM || cancelled || duplicate != null) return false; notice = "Restoring vault… Keep Totipo open."; stage = Stage.INSTALLING; return true; }
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
            notice = "Restore as a local vault on this device. The vault keeps its password. Sync and biometric unlock can be set up afterward.";
            stage = Stage.PASSWORD;
        } catch (Exception failure) { fail("Could not read this backup ZIP. Choose a complete supported copy. Keep the app’s data and original backup. No new vault was created."); }
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
                        || !intent.vaultId().equals(summary.vaultId().hex())) {
                    fail("This is a different backup. Choose the exact original ZIP to resume. Keep the app’s data and original backup.");
                    return;
                }
            }
            try { duplicate = registry.checkRestoreIdentity(recoveryId, summary.vaultId()); }
            catch (IOException unavailable) {
                fail("Existing vault data could not be checked safely. Keep the app’s data and original backup. Check Recovery before trying again.");
                return;
            }
            current();
            notice = duplicate == null
                    ? (summary.unresolvedReferences() == 0 ? "Backup checked."
                        : "This backup is missing some earlier history. Restoring it won't recover that history.")
                    : duplicate.state() == VaultRegistry.State.ESTABLISHED
                        ? "This vault already exists. Open the existing vault; no history has been added."
                        : "This vault’s data is already kept on this device. Cancel restore and use its Recovery entry.";
            stage = Stage.CONFIRM;
        } catch (Exception failure) { fail("Could not unlock the backup. Check the original password or try another copy. Keep the app’s data and original backup. No new vault was created."); }
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
                notice = "Backup installed. Enter the original vault password to finish restoring.";
                stage = Stage.DONE;
            } else {
                notice = incompleteNotice(result.outcome());
                stage = Stage.FAILED;
            }
        } catch (Exception failure) {
            fail("Restore could not finish safely. Keep the app’s data and original backup. Use Recovery to resume with the exact original ZIP and password. If that fails, keep the data for recovery; do not clear it or uninstall.");
        } finally {
            dispose();
            if (owned != null) {
                // beginMaintenance may itself reject; only release a successfully held reservation.
                try { selection.endMaintenance(owned); } catch (IllegalStateException notOwned) { /* No ownership acquired. */ }
            }
        }
    }
    static String incompleteNotice(RestoreResult.Outcome outcome) {
        String reason = switch (outcome) {
            case PARTIAL -> "Only part of the backup was restored.";
            case UNCERTAIN -> "Totipo could not confirm that the backup was fully saved.";
            case REJECTED -> "The backup could not be installed safely.";
            case COMPLETE -> throw new IllegalArgumentException("Complete restore");
        };
        return reason + " Keep the app’s data and original backup. Use Recovery → Resume restore from backup with the exact original ZIP and password.";
    }
    void cancel() {
        cancelled = true;
        var active = plan; if (active != null) active.cancel(); // Nonblocking; close belongs to the worker.
        notice = "Restore canceled. Storage work already started may finish. Keep the app’s data and original backup. Use Recovery to continue an unfinished restore.";
    }
    void dispose() {
        var active = plan; plan = null; if (active != null) active.close(); clearArchive();
        if (cancelled) stage = Stage.CANCELLED;
    }
    private void clearArchive() { if (archive != null) Arrays.fill(archive, (byte) 0); archive = null; length = 0; }
    private void fail(String message) { notice = message; stage = cancelled ? Stage.CANCELLED : Stage.FAILED; }
}
