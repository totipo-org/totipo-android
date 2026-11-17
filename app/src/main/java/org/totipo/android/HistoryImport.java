package org.totipo.android;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.totipo.backup.*;

/** One controller/session-bound archive request. Java alone owns authenticated evidence.
 * All handle methods and disposal run on the existing vault worker. */
final class HistoryImport {
    enum Phase { PICKER, PREPARING, CONFIRM, APPLYING, DONE }
    interface Source { InputStream open() throws IOException; }
    final long session;
    final AtomicBoolean cancelled = new AtomicBoolean();
    volatile Phase phase = Phase.PICKER;
    volatile String message = "";
    BackupImport plan; // worker only
    String digest; // assigned by worker before CONFIRM
    HistoryImport(long session) { this.session = session; }
    boolean active() { return phase != Phase.DONE; }
    void revoke() { cancelled.set(true); }
    void dispose() { if (plan != null) { plan.close(); plan = null; } }
    static String outcome(ImportResult result) {
        return switch (result.outcome()) {
            case COMPLETE -> "History added.";
            case NO_OP -> "No new history to add.";
            case PARTIAL -> "Some history was added, but the import couldn't finish.";
            case UNCERTAIN -> "Some history may have been added, but Totipo couldn't confirm completion.";
            case FAILED -> "History couldn't be added.";
        };
    }
    static final String RECOVERY = " Keep the original backup and the app’s data. Unlock this vault and choose Add History from Backup with the original ZIP to retry. Sync publication is blocked until recovery completes.";
    InputStream ingress(Source source) throws IOException {
        if (cancelled.get()) throw new IOException("Cancelled");
        InputStream input = source.open();
        if (input == null) throw new IOException("No stream");
        return new FilterInputStream(input) {
            private void current() throws IOException { if (cancelled.get()) throw new IOException("Cancelled"); }
            @Override public int read() throws IOException { current(); return in.read(); }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                current(); int count = in.read(bytes, offset, length);
                if (count == 0 && length > 0) throw new IOException("No progress");
                return count;
            }
        };
    }
}
