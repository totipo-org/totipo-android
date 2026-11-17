package org.totipo.android;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.totipo.backup.BackupCapture;
import org.totipo.backup.BackupLimits;

/** One explicitly authorized export. Only ciphertext is retained, in bounded memory.
 * Controller owns admission; capture runs on vault worker, transport on provider lane.
 * No URI/password/session is persisted or retained here. No automatic retry. */
final class BackupExport {
    enum Phase { PREPARING, READY, PICKER, QUEUED, WRITING, FINISHED }
    enum Outcome { VERIFIED, WRITTEN_UNVERIFIED, FAILED, CANCELLED }
    interface Destination {
        OutputStream openOutput() throws IOException;
        InputStream openReadback() throws IOException;
    }
    interface Journal {
        /** Must positively persist an interrupted-output notice before opening output. */
        boolean begin();
        boolean complete();
    }
    final long session;
    final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile Phase phase = Phase.PREPARING;
    private volatile String message = "Preparing backup…";
    private byte[] archive;
    private byte[] digest;
    private int gaps;
    private volatile boolean started;
    BackupExport(long session) { this.session = session; }
    Phase phase() { return phase; }
    String message() { return message; }
    boolean active() { return phase != Phase.FINISHED; }
    boolean writeStarted() { return started; }
    // Called only on the vault worker, outside all Android store/bridge locks.
    void prepare(BackupCapture capture) throws IOException {
        try (capture) {
            var summary = capture.summary();
            // Fixed allocation prevents ByteArrayOutputStream's unbounded growth/copying.
            byte[] buffer = new byte[BackupLimits.DEFAULT.archiveBytes()];
            int[] size = {0};
            try {
                capture.writeTo(new OutputStream() {
                    @Override public void write(int value) throws IOException {
                        byte[] one = {(byte) value}; write(one, 0, 1);
                    }
                    @Override public void write(byte[] bytes, int offset, int count) throws IOException {
                        if (cancelled.get()) throw new InterruptedIOException();
                        if (count > buffer.length - size[0]) throw new IOException("Backup capacity");
                        System.arraycopy(bytes, offset, buffer, size[0], count); size[0] += count;
                    }
                });
                MessageDigest prepared = sha256(); prepared.update(buffer, 0, size[0]);
                byte[] hash = prepared.digest();
                StringBuilder hex = new StringBuilder(64);
                for (byte value : hash) hex.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
                if (!hex.toString().equals(summary.archiveSha256())) throw new IOException("Backup digest");
                synchronized (this) {
                    if (cancelled.get()) { finish(Outcome.CANCELLED); return; }
                    archive = Arrays.copyOf(buffer, size[0]); digest = hash;
                    gaps = summary.unresolvedReferences(); phase = Phase.READY;
                    message = "Choose where to save the backup.";
                }
            } finally { Arrays.fill(buffer, (byte) 0); }
        }
    }
    synchronized boolean picker() {
        if (cancelled.get() || phase != Phase.READY) return false;
        phase = Phase.PICKER; return true;
    }
    synchronized boolean queue() {
        if (cancelled.get() || phase != Phase.PICKER) return false;
        phase = Phase.QUEUED; message = "Saving and checking backup…"; return true;
    }
    synchronized void cancel() {
        cancelled.set(true);
        // Never wipe an archive being consumed by admitted I/O/preparation.
        if (phase == Phase.READY || phase == Phase.PICKER) finish(Outcome.CANCELLED);
    }
    synchronized void failed(String explanation) {
        finish(cancelled.get() ? Outcome.CANCELLED : Outcome.FAILED);
        if (!cancelled.get()) message = explanation;
    }
    void write(Destination destination, Journal journal) {
        synchronized (this) {
            if (cancelled.get() || phase != Phase.QUEUED) { finish(Outcome.CANCELLED); return; }
            phase = Phase.WRITING;
        }
        Outcome result = Outcome.FAILED;
        boolean recorded = false;
        try {
            if (cancelled.get()) { result = Outcome.CANCELLED; return; }
            if (!journal.begin()) return;
            recorded = true;
            synchronized (this) {
                if (cancelled.get()) { result = Outcome.CANCELLED; return; }
                // Linearize output admission against cancellation without holding any
                // monitor across provider I/O. An admitted open can finish after cancel.
                started = true;
            }
            try (OutputStream output = destination.openOutput()) {
                if (output == null) throw new IOException("No output");
                for (int offset = 0; offset < archive.length;) {
                    if (cancelled.get()) { result = Outcome.CANCELLED; return; }
                    int count = Math.min(16384, archive.length - offset);
                    output.write(archive, offset, count); offset += count;
                }
                if (cancelled.get()) { result = Outcome.CANCELLED; return; }
                output.flush();
            } // Close failure is a failed backup, even if all writes returned.
            if (cancelled.get()) { result = Outcome.CANCELLED; return; }
            InputStream readback;
            try { readback = destination.openReadback(); }
            catch (IOException | SecurityException | UnsupportedOperationException unavailable) {
                result = Outcome.WRITTEN_UNVERIFIED; return;
            }
            if (readback == null) { result = Outcome.WRITTEN_UNVERIFIED; return; }
            try (InputStream input = readback) {
                MessageDigest actual = sha256();
                byte[] buffer = new byte[16384]; int total = 0;
                for (;;) {
                    if (cancelled.get()) { result = Outcome.CANCELLED; return; }
                    int count = input.read(buffer, 0, Math.min(buffer.length, archive.length - total + 1));
                    if (count == -1) break;
                    if (count == 0) {
                        int one = input.read(); if (one == -1) break;
                        buffer[0] = (byte) one; count = 1;
                    }
                    total += count;
                    if (total > archive.length) throw new IOException("Excess readback");
                    actual.update(buffer, 0, count);
                }
                if (total != archive.length || !MessageDigest.isEqual(digest, actual.digest()))
                    throw new IOException("Readback mismatch");
            } // Readback close failure also refuses verified success.
            result = Outcome.VERIFIED;
        } catch (IOException | RuntimeException failure) {
            result = Outcome.FAILED; // Never expose provider exception text or URI.
        } finally {
            boolean cleared = !recorded || journal.complete();
            synchronized (this) {
                finish(cancelled.get() ? Outcome.CANCELLED : result);
                if (!cleared) message += " The interruption notice could not be cleared and may appear again after restart.";
            }
        }
    }
    private synchronized void finish(Outcome result) {
        phase = Phase.FINISHED;
        message = switch (result) {
            case VERIFIED -> "Backup saved.";
            case WRITTEN_UNVERIFIED -> "Backup saved, but couldn't be verified. Check the file before relying on it.";
            case FAILED -> "Backup failed. The file may be incomplete.";
            case CANCELLED -> started ? "Backup canceled. A partial file may remain. Check the chosen location; the file may be empty, incomplete or complete."
                    : "Backup canceled.";
        };
        if (gaps > 0) message += " Some earlier history is missing from this backup.";
        if (archive != null) { Arrays.fill(archive, (byte) 0); archive = null; }
        digest = null;
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
