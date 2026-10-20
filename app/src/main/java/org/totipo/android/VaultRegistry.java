package org.totipo.android;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.*;

/** Local-only metadata, never part of a Java protocol store. One Application-owned writer,
 * single process. Paths are derived, never deserialized. No credentials or protocol identity.
 * A failed write poisons this instance: restart/recovery must inspect disk, never retry blind.
 */
public final class VaultRegistry {
    private static final int MAGIC = 0x544d5631, VERSION = 2, MAX_ENTRIES = 128, MAX_BYTES = 65536;
    public enum State { PENDING, ESTABLISHED, FORGETTING, DETACHED }
    public record Entry(String id, String label, State state, boolean creationAttempted) {
        public Entry {
            validateId(id);
            Objects.requireNonNull(state);
            if (label != null && (label.isEmpty() || label.length() > 128
                    || label.codePoints().anyMatch(c -> Character.isISOControl(c) || (c >= 0xd800 && c <= 0xdfff))))
                throw new IllegalArgumentException("Invalid local vault label");
            if (state == State.ESTABLISHED && !creationAttempted) throw new IllegalArgumentException("Invalid established entry");
        }
    }
    public static final class RecoveryRequired extends IOException {
        private final String diagnostic;
        RecoveryRequired(String diagnostic) { super(diagnostic); this.diagnostic = diagnostic; }
        /** Fixed application-authored diagnostic, never an underlying exception message. */
        public String diagnostic() { return diagnostic; }
    }
    public static final String LEGACY_DIAGNOSTIC = "Incompatible single-vault installation. No data was changed. "
            + "Preserve or synchronize all local vaults using the previous application before manually resetting app data. "
            + "A reset permanently loses unsynchronized local vaults.";
    private final Path base, home, roots, metadata, next;
    private final AtomicWriter writer;
    private List<Entry> entries = List.of();
    private String lastSelected;
    private boolean failed;
    private byte[] committed;

    interface AtomicWriter { void write(Path destination, Path temporary, byte[] bytes) throws IOException; }
    public VaultRegistry(Path noBackupDirectory) throws IOException { this(noBackupDirectory, VaultRegistry::atomicWrite); }
    VaultRegistry(Path noBackupDirectory, AtomicWriter writer) throws IOException {
        base = noBackupDirectory.toAbsolutePath();
        if (!base.equals(base.normalize())) throw new RecoveryRequired("Private storage path requires recovery.");
        this.writer = Objects.requireNonNull(writer);
        home = base.resolve("totipo-local-v1"); roots = home.resolve("roots");
        metadata = home.resolve("registry"); next = home.resolve("registry.next");
        directory(base);
        rejectLegacy(base);
        if (!exists(home)) {
            Files.createDirectory(home); forceDirectory(base);
        }
        directory(home);
        if (exists(next)) throw new RecoveryRequired("Interrupted registry write. Preserve storage and recover metadata explicitly.");
        if (!exists(metadata)) {
            try (var children = Files.list(home)) {
                if (children.findAny().isPresent()) throw new RecoveryRequired("Registry missing beside managed data. Recovery required.");
            }
            Files.createDirectory(roots); forceDirectory(home);
            persist(List.of(), null);
        } else {
            byte[] bytes = readMetadata(metadata);
            decode(bytes); committed = bytes.clone();
        }
        directory(roots);
        validateInventory();
    }
    private static byte[] readMetadata(Path path) throws IOException {
        regular(path);
        long size = Files.size(path);
        if (size < 32 || size > MAX_BYTES) throw new RecoveryRequired("Corrupt vault registry. Recovery required.");
        try (var input = Files.newInputStream(path, READ, NOFOLLOW_LINKS)) {
            var buffer = new ByteArrayOutputStream(); byte[] block = new byte[4096]; int count;
            while ((count = input.read(block)) != -1) {
                if (buffer.size() + count > MAX_BYTES) throw new RecoveryRequired("Oversized registry.");
                buffer.write(block, 0, count);
            }
            return buffer.toByteArray();
        }
    }
    public static void validateId(String id) {
        if (id == null || !id.matches("[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid local vault ID");
    }
    static boolean exists(Path path) { return Files.exists(path, NOFOLLOW_LINKS); }
    /** Check every ancestor too: final-component checks alone permit symlink escape. */
    static void directory(Path path) throws IOException {
        Path absolute = path.toAbsolutePath();
        if (!absolute.equals(absolute.normalize())) throw new RecoveryRequired("Noncanonical private path.");
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (!Files.isDirectory(current, NOFOLLOW_LINKS)) throw new RecoveryRequired("Missing or unsafe managed directory. Recovery required.");
        }
    }
    private static void regular(Path path) throws IOException {
        directory(path.getParent());
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) throw new RecoveryRequired("Unsafe registry metadata. Recovery required.");
    }
    static void rejectLegacy(Path base) throws IOException {
        if (exists(base.resolve("totipo-vault"))) throw new RecoveryRequired(LEGACY_DIAGNOSTIC);
        for (String name : List.of("biometric-password-v1", "biometric-password-v1.bak", "biometric-password-v1.new"))
            if (exists(base.resolve(name))) throw new RecoveryRequired(LEGACY_DIAGNOSTIC);
        Path preferences = base.getParent().resolve("shared_prefs");
        for (String name : List.of("sync_binding_v1.xml", "sync_binding_v1.xml.bak"))
            if (exists(preferences.resolve(name))) throw new RecoveryRequired(LEGACY_DIAGNOSTIC);
    }
    private void healthy() throws IOException {
        if (failed) throw new RecoveryRequired("Registry write failed or uncertain. Restart and recover before continuing.");
        directory(home); directory(roots);
    }
    public synchronized List<Entry> entries() { return entries; }
    /** Complete, freshly validated metadata inventory for app-wide SAF decisions. */
    public synchronized List<Entry> bindingEntries() throws IOException {
        healthy();
        if (exists(next)) throw new RecoveryRequired("Interrupted registry write. Permission cleanup deferred.");
        if (!Arrays.equals(committed, readMetadata(metadata)))
            throw new RecoveryRequired("Registry changed. Permission cleanup deferred.");
        validateInventory();
        return entries;
    }
    public synchronized String lastSelected() { return lastSelected; }
    synchronized Entry entry(String id) throws IOException {
        healthy(); validateId(id);
        return entries.stream().filter(e -> e.id().equals(id)).findFirst()
                .orElseThrow(() -> new RecoveryRequired("Unknown local vault entry."));
    }
    Path root(String id) { validateId(id); return roots.resolve(id).resolve("store"); }
    /** Reservation is durable BEFORE the first directory or protocol byte is created. */
    public synchronized Entry reserve(String label) throws IOException {
        healthy();
        if (entries.size() == MAX_ENTRIES) throw new RecoveryRequired("Local vault registry is full.");
        String id = UUID.randomUUID().toString().replace("-", "");
        if (entries.stream().anyMatch(e -> e.id().equals(id)) || exists(roots.resolve(id)))
            throw new RecoveryRequired("Local vault allocation collision. Existing data was preserved.");
        Entry entry = new Entry(id, label, State.PENDING, false);
        var updated = new ArrayList<>(entries); updated.add(entry);
        persist(updated, lastSelected);
        allocatePending(id);
        return entry;
    }
    /** Explicit recovery for reservation interrupted before allocation. Never opens or replaces data. */
    public synchronized void allocatePending(String id) throws IOException {
        Entry entry = entry(id);
        if (entry.state() != State.PENDING || entry.creationAttempted()) throw new RecoveryRequired("Creation needs explicit data recovery.");
        Path container = roots.resolve(id), store = root(id);
        if (!exists(container)) { Files.createDirectory(container); forceDirectory(roots); }
        directory(container);
        try (var children = Files.list(container)) {
            if (children.anyMatch(p -> !p.equals(store))) throw new RecoveryRequired("Uncertain allocation. Preserve data for recovery.");
        }
        if (!exists(store)) { Files.createDirectory(store); forceDirectory(container); }
        validateRoot(id);
        try (var children = Files.list(store)) {
            if (children.findAny().isPresent()) throw new RecoveryRequired("Pending root contains data. Open or recover it; never recreate it.");
        }
    }
    synchronized Path validateRoot(String id) throws IOException {
        entry(id); Path root = root(id); directory(root); return root;
    }
    public synchronized void select(String id) throws IOException {
        if (entry(id).state() == State.FORGETTING) throw new RecoveryRequired("Forget cleanup must finish before recovery.");
        validateRoot(id); persist(entries, id);
    }
    synchronized void beginCreation(String id) throws IOException {
        Entry entry = entry(id); validateRoot(id);
        if (entry.state() != State.PENDING || entry.creationAttempted())
            throw new RecoveryRequired("Vault creation already attempted. Open existing data or recover explicitly.");
        try (var children = Files.list(root(id))) {
            if (children.findAny().isPresent()) throw new RecoveryRequired("Vault root is not empty. Creation refused.");
        }
        replace(new Entry(id, entry.label(), State.PENDING, true));
    }
    synchronized void established(String id) throws IOException {
        Entry entry = entry(id); validateRoot(id);
        if (entry.state() != State.ESTABLISHED) replace(new Entry(id, entry.label(), State.ESTABLISHED, true));
    }
    public synchronized void rename(String id, String label) throws IOException {
        Entry old = entry(id);
        replace(new Entry(id, label, old.state(), old.creationAttempted()));
    }
    /** Durable intent BEFORE any scoped cleanup. Retrying never removes canonical data. */
    synchronized void beginForget(String id) throws IOException {
        Entry old = entry(id);
        if (old.state() == State.DETACHED || old.state() == State.FORGETTING) return;
        replace(new Entry(id, old.label(), State.FORGETTING, old.creationAttempted()));
    }
    synchronized void finishForget(String id) throws IOException {
        Entry old = entry(id);
        if (old.state() != State.FORGETTING) throw new RecoveryRequired("Forget intent missing.");
        replace(new Entry(id, old.label(), State.DETACHED, old.creationAttempted()));
    }
    /** Bounded structural recognition is only a conservative refusal, never authentication.
     * All roots, including detached ones, remain accounted for. No session/provider is opened. */
    synchronized void refusePossibleDuplicate(String id, org.totipo.VaultId identity) throws IOException {
        bindingEntries(); entry(id);
        for (Entry other : entries) {
            if (other.id().equals(id)) continue;
            Path file = root(other.id()).resolve("vault");
            if (!exists(file)) continue;
            validateRoot(other.id()); regular(file);
            byte[] bytes;
            bytes = recognitionBytes(file);
            try {
                if (org.totipo.Totipo.vaultId(bytes).equals(identity))
                    throw new RecoveryRequired("Possible duplicate vault identity in retained local data. Preserve both roots; explicit storage recovery is required. No histories were merged.");
            } catch (IllegalArgumentException malformed) { /* Invalid bytes cannot identify another vault. */ }
        }
    }
    synchronized void refusePossibleDuplicate(String id) throws IOException {
        Path file = validateRoot(id).resolve("vault");
        if (!exists(file)) return;
        regular(file);
        byte[] bytes;
        bytes = recognitionBytes(file);
        try { refusePossibleDuplicate(id, org.totipo.Totipo.vaultId(bytes)); }
        catch (IllegalArgumentException malformed) { /* Normal selected discovery supplies the diagnostic. */ }
    }
    private static byte[] recognitionBytes(Path file) throws IOException {
        try (var input = Files.newInputStream(file, READ, NOFOLLOW_LINKS)) {
            byte[] bytes = new byte[88]; int size = 0, count;
            while (size < bytes.length && (count = input.read(bytes, size, bytes.length - size)) != -1) {
                if (count == 0) throw new IOException("No recognition read progress");
                size += count;
            }
            return Arrays.copyOf(bytes, size);
        }
    }
    private void replace(Entry entry) throws IOException {
        var updated = new ArrayList<>(entries);
        for (int i = 0; i < updated.size(); i++) if (updated.get(i).id().equals(entry.id())) updated.set(i, entry);
        persist(updated, lastSelected);
    }
    private void validateInventory() throws IOException {
        Set<String> known = new HashSet<>(); entries.forEach(e -> known.add(e.id()));
        try (var children = Files.list(roots)) {
            for (Path child : children.collect(java.util.stream.Collectors.toList())) {
                if (!known.contains(child.getFileName().toString())) throw new RecoveryRequired("Unregistered managed root. Preserve data for recovery.");
                directory(child);
            }
        }
        // Missing roots are retained in metadata and fail when selected; never recreate them.
    }
    private void persist(List<Entry> updated, String selected) throws IOException {
        byte[] bytes = encode(updated, selected);
        try {
            if (committed != null && !Arrays.equals(committed, readMetadata(metadata)))
                throw new RecoveryRequired("Registry changed unexpectedly. Preserve data and recover metadata explicitly.");
            writer.write(metadata, next, bytes);
        }
        catch (IOException | RuntimeException failure) { failed = true; throw failure; }
        entries = List.copyOf(updated); lastSelected = selected; committed = bytes.clone();
    }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static byte[] encode(List<Entry> entries, String selected) throws IOException {
        validate(entries, selected);
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) {
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeUTF(entry.id()); out.writeBoolean(entry.label() != null);
                if (entry.label() != null) out.writeUTF(entry.label());
                out.writeByte(entry.state().ordinal()); out.writeBoolean(entry.creationAttempted());
            }
            out.writeBoolean(selected != null); if (selected != null) out.writeUTF(selected);
        }
        byte[] payload = buffer.toByteArray(); buffer.write(digest(payload)); return buffer.toByteArray();
    }
    private void decode(byte[] bytes) throws IOException {
        try {
            if (bytes.length < 32 || bytes.length > MAX_BYTES) throw new IOException();
            byte[] payload = Arrays.copyOf(bytes, bytes.length - 32);
            if (!MessageDigest.isEqual(digest(payload), Arrays.copyOfRange(bytes, payload.length, bytes.length))) throw new IOException();
            try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
                if (in.readInt() != MAGIC) throw new IOException();
                int version = in.readInt(); if (version != 1 && version != VERSION) throw new IOException();
                int count = in.readInt(); if (count < 0 || count > MAX_ENTRIES) throw new IOException();
                var decoded = new ArrayList<Entry>();
                for (int i = 0; i < count; i++) {
                    String id = in.readUTF(); String label = bool(in) ? in.readUTF() : null;
                    int state = in.readUnsignedByte(); if (state > (version == 1 ? 1 : 3)) throw new IOException();
                    decoded.add(new Entry(id, label, State.values()[state], bool(in)));
                }
                String selected = bool(in) ? in.readUTF() : null;
                if (in.read() != -1) throw new IOException();
                // Last selection is a navigation hint, never root/session authority.
                // A stale/invalid hint does not invalidate otherwise checked registry entries.
                String hint = selected;
                if (hint != null && decoded.stream().noneMatch(e -> e.id().equals(hint))) selected = null;
                validate(decoded, selected); entries = List.copyOf(decoded); lastSelected = selected;
            }
        } catch (IOException | IllegalArgumentException malformed) {
            throw new RecoveryRequired("Corrupt or unsupported vault registry. Preserve data and recover metadata explicitly.");
        }
    }
    private static boolean bool(DataInputStream in) throws IOException {
        int value = in.readUnsignedByte(); if (value > 1) throw new IOException(); return value == 1;
    }
    private static void validate(List<Entry> entries, String selected) {
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many entries");
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) if (!ids.add(entry.id())) throw new IllegalArgumentException("Duplicate local vault ID");
        if (selected != null && !ids.contains(selected)) throw new IllegalArgumentException("Unknown selection");
        // Exact fixed-depth ID-derived roots cannot overlap, including ancestor/descendant paths.
    }
    static void atomicWrite(Path destination, Path temporary, byte[] bytes) throws IOException {
        directory(destination.getParent());
        if (exists(destination)) regular(destination);
        try (var channel = FileChannel.open(temporary, CREATE_NEW, WRITE, NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) <= 0) throw new IOException("Registry write made no progress");
            }
            channel.force(true);
        }
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        forceDirectory(destination.getParent());
        regular(destination);
        if (!Arrays.equals(bytes, readMetadata(destination))) throw new IOException("Registry readback failed");
    }
    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, READ, NOFOLLOW_LINKS)) { channel.force(true); }
    }
}
