package org.totipo.android;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.util.Arrays;

/** Non-secret interrupted-output marker, outside every canonical root. Never stores
 * a URI or resume authority. Accessed on provider worker; startup only checks presence. */
final class BackupExportJournal implements BackupExport.Journal {
    private static final byte[] CONTENT = "totipo-backup-export-v1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private final Path parent, marker;
    BackupExportJournal(Path noBackup) { parent = noBackup; marker = parent.resolve("backup-export-v1.pending"); }
    boolean interrupted() { return Files.exists(marker, LinkOption.NOFOLLOW_LINKS); }
    private void parentSafe() throws IOException {
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Private storage unavailable");
    }
    private void verify() throws IOException {
        parentSafe();
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.size(marker) != CONTENT.length
                || !Arrays.equals(Files.readAllBytes(marker), CONTENT)) throw new IOException("Export marker unavailable");
    }
    private void forceParent() throws IOException {
        try (var directory = FileChannel.open(parent, StandardOpenOption.READ)) { directory.force(true); }
    }
    public boolean begin() {
        try {
            parentSafe();
            if (interrupted()) {
                verify();
                try (var file = FileChannel.open(marker, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) { file.force(true); }
            }
            else try (var file = FileChannel.open(marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                ByteBuffer bytes = ByteBuffer.wrap(CONTENT);
                while (bytes.hasRemaining()) file.write(bytes);
                file.force(true);
            }
            forceParent(); verify(); return true;
        } catch (IOException | RuntimeException failure) { return false; }
    }
    public boolean complete() {
        try { verify(); Files.delete(marker); forceParent(); return true; }
        catch (IOException | RuntimeException failure) { return false; }
    }
}
