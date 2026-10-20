package org.totipo.android;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Arrays;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** One registry entry's private metadata, outside store/. Single application writer.
 * Failed replacement/deletion leaves a durable blocker; only explicit disable recovers it.
 */
final class BiometricCredentialStorage {
    final String localId;
    final Path record, pending, next, disabled;
    private final Path directory;
    interface Writer { void write(Path record, Path next, byte[] bytes) throws IOException; }
    interface Remover { void delete(Path path) throws IOException; }
    private final Writer writer;
    private final Remover remover;
    private boolean uncertain;
    BiometricCredentialStorage(VaultRegistry registry, String id) throws IOException {
        this(registry, id, BiometricCredentialStorage::atomicWrite, Files::deleteIfExists);
    }
    BiometricCredentialStorage(VaultRegistry registry, String id, Writer writer, Remover remover) throws IOException {
        VaultRegistry.validateId(id); registry.entry(id);
        directory = registry.validateRoot(id).getParent();
        localId = id; this.writer = writer; this.remover = remover;
        record = directory.resolve("biometric-v2"); pending = directory.resolve("biometric-v2.pending");
        next = directory.resolve("biometric-v2.next"); disabled = directory.resolve("biometric-v2.disabled");
        validate();
    }
    private void validate() throws IOException {
        VaultRegistry.directory(directory);
        for (Path path : new Path[]{record, pending, next, disabled})
            if (VaultRegistry.exists(path) && !Files.isRegularFile(path, NOFOLLOW_LINKS))
                throw new IOException("Unsafe credential storage");
    }
    synchronized boolean recovery() throws IOException {
        validate(); return uncertain || VaultRegistry.exists(pending) || VaultRegistry.exists(next) || VaultRegistry.exists(disabled);
    }
    synchronized boolean present() throws IOException { validate(); return VaultRegistry.exists(record); }
    synchronized byte[] read() throws IOException {
        if (recovery()) throw new IOException("Credential recovery required");
        return bounded(record);
    }
    synchronized void write(byte[] bytes) throws IOException {
        if (recovery()) throw new IOException("Credential recovery required");
        BiometricRecord.decode(bytes, localId);
        uncertain = true;
        try {
            marker(pending);
            // No in-place overwrite and no fallback to a non-atomic move.
            writer.write(record, next, bytes);
            if (!Arrays.equals(bytes, bounded(record))) throw new IOException("Credential persistence failed");
            remover.delete(pending); force(directory);
            uncertain = false;
        } catch (IOException | RuntimeException failure) {
            // If the final directory acknowledgement failed after marker removal,
            // try to restore the persistent blocker as well as poisoning this instance.
            try { marker(pending); } catch (IOException | RuntimeException unavailable) { /* retain uncertainty */ }
            throw failure;
        }
    }
    synchronized void beginDelete() throws IOException { validate(); uncertain = true; marker(disabled); }
    synchronized void deleteRecord() throws IOException {
        validate();
        for (Path path : new Path[]{record, next, pending}) remover.delete(path);
        force(directory);
        for (Path path : new Path[]{record, next, pending})
            if (VaultRegistry.exists(path)) throw new IOException("Credential deletion incomplete");
    }
    synchronized void finishDelete() throws IOException {
        validate();
        try {
            remover.delete(disabled); force(directory);
            if (VaultRegistry.exists(disabled)) throw new IOException("Credential cleanup incomplete");
            uncertain = false;
        } catch (IOException | RuntimeException failure) {
            try { marker(disabled); } catch (IOException | RuntimeException unavailable) { /* retain uncertainty */ }
            throw failure;
        }
    }
    private void marker(Path path) throws IOException {
        if (!VaultRegistry.exists(path)) {
            try (var channel = FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS)) { channel.force(true); }
        }
        force(directory);
    }
    private static byte[] bounded(Path path) throws IOException {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw new IOException("Credential unavailable");
        try (var in = Files.newInputStream(path, READ, NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[BiometricRecord.MAX_BYTES + 1];
            int size = 0, count;
            while (size < bytes.length && (count = in.read(bytes, size, bytes.length - size)) != -1) {
                if (count == 0) throw new IOException("Credential read made no progress");
                size += count;
            }
            if (size > BiometricRecord.MAX_BYTES) throw new IOException("Oversized credential");
            return Arrays.copyOf(bytes, size);
        }
    }
    static void atomicWrite(Path record, Path next, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(next, CREATE_NEW, WRITE, NOFOLLOW_LINKS)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) if (channel.write(buffer) <= 0) throw new IOException("Credential write failed");
            channel.force(true);
        }
        Files.move(next, record, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        force(record.getParent());
    }
    private static void force(Path path) throws IOException {
        try (var channel = FileChannel.open(path, READ, NOFOLLOW_LINKS)) { channel.force(true); }
    }
}
