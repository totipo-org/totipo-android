package org.totipo.android.sync;

import java.io.*;
import java.util.*;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Deterministic namespace/output fake; never a production seam. */
public final class PublicationPort extends SyncFolderBindingTest.MemoryPort implements ProviderObjectWriter.Port, ProviderVaultWriter.Port {
    public static final Tree TREE = new Tree("fixture", "content://fixture/tree/a", "root");
    public final Map<String, byte[]> objects = new LinkedHashMap<>();
    public final Document directory = new Document(TREE, "content://fixture/dir", "dir", "root", "objects-v1",
            "vnd.android.document/directory", null, 8L);
    public byte[] vaultBytes;
    public boolean directoryPresent = true;
    public int vaultCreates, directoryCreates, vaultOutputs;
    public String bootstrapFault = "";
    public Runnable onVaultCreate = () -> {}, onDirectoryCreate = () -> {};
    public Document createVault(Document root) throws Exception {
        assertLane(); vaultCreates++; onVaultCreate.run();
        if (bootstrapFault.equals("create")) throw new IOException();
        if (bootstrapFault.equals("existingId")) return new Document(TREE, directory.locator(), directory.id(), "root", "vault", "application/octet-stream", null, null);
        return new Document(TREE, "content://fixture/vault", "vault", "root", "vault", "application/octet-stream", null, null);
    }
    public Document createObjectsDirectory(Document root) throws Exception {
        assertLane(); directoryCreates++; onDirectoryCreate.run();
        if (bootstrapFault.equals("directory")) throw new IOException();
        directoryPresent = true; return directory;
    }
    public Document vaultMetadata(Document created) {
        assertLane();
        if (bootstrapFault.equals("suffix") || bootstrapFault.equals("directorySuffix") && created.isDirectory())
            return new Document(created.tree(), created.locator(), created.id(), created.parentId(), created.displayName() + " (1)", created.mimeType(), null, null);
        return created;
    }
    public OutputStream vaultOutput(Document created) throws Exception {
        assertLane(); vaultOutputs++;
        if (bootstrapFault.equals("open")) throw new IOException();
        return new ByteArrayOutputStream() {
            public void write(byte[] bytes) throws IOException {
                onWrite.run();
                if (bootstrapFault.equals("partial")) { super.write(bytes, 0, 40); throw new IOException(); }
                super.write(bytes);
            }
            public void close() throws IOException {
                vaultBytes = toByteArray();
                if (bootstrapFault.equals("close")) throw new IOException();
            }
        };
    }
    public Bytes vaultReadBack(Document created) {
        assertLane(); byte[] bytes = vaultBytes.clone();
        if (bootstrapFault.equals("readback")) bytes[0] ^= 1;
        return new Bytes("read", created, 87, ByteState.PRESENT, bytes, Issue.NONE);
    }
    public java.util.function.UnaryOperator<Scan> snapshotTransform = value -> value;
    public int creates, outputs;
    public String fault = "";
    public int failAt = 1;
    public Runnable onCreate = () -> {}, onWrite = () -> {};
    public State coverage = State.COMPLETE;
    public PublicationPort() {
        stored = new SyncFolderBinding.Stored(TREE.locator(), true, true);
        permissions.put(TREE.locator(), new SyncFolderBinding.Grants(true, true));
    }
    public Document doc(String id) { return new Document(TREE, "content://fixture/" + id, id, "dir", id,
            "application/octet-stream", null, 0L); }
    @Override public Scan scan(String uri) {
        scans++; onScan.run();
        List<Bytes> bytes = new ArrayList<>();
        objects.forEach((id, representation) -> bytes.add(new Bytes("e", doc(id), 1024,
                representation.length == 1024 ? ByteState.PRESENT : ByteState.SHORT, representation, Issue.NONE)));
        Scan result = new Scan("e", TREE, new Listing("e", "root", directoryPresent ? List.of(directory) : List.of(), coverage, List.of()),
                directoryPresent ? List.of(new Directory(directory, new Listing("e", "dir", bytes.stream().map(Bytes::document).toList(),
                        coverage, List.of()), bytes)) : List.of(), coverage, List.of());
        return snapshotTransform.apply(vaultBytes == null ? result : org.totipo.android.reconcile.ForegroundVaultCoordinatorTest.withVault(result, vaultBytes));
    }
    private boolean fault(String name) { return creates == failAt && fault.equals(name); }
    public Document create(Document parent, String name) throws Exception {
        assertLane(); creates++; onCreate.run();
        if (fault("throw")) throw new IOException();
        if (fault("null")) return null;
        return doc(name);
    }
    public Document metadata(Document created) {
        assertLane();
        return fault("suffix") ? new Document(TREE, created.locator(), created.id(), "dir", created.displayName() + " (1)",
                "application/octet-stream", null, 0L) : created;
    }
    public OutputStream output(Document created) throws Exception {
        assertLane(); outputs++;
        if (fault("open")) throw new IOException();
        return new ByteArrayOutputStream() {
            @Override public void write(byte[] bytes) throws IOException {
                onWrite.run();
                if (fault("partial")) { super.write(bytes, 0, 100); throw new IOException(); }
                super.write(bytes);
            }
            @Override public void close() throws IOException {
                objects.put(created.displayName(), toByteArray());
                if (fault("close")) throw new IOException();
            }
        };
    }
    public Bytes readBack(Document created) {
        assertLane();
        byte[] bytes = objects.get(created.displayName()).clone();
        if (fault("different")) bytes[0] ^= 1;
        return new Bytes("read", created, 1024, fault("unavailable") ? ByteState.UNAVAILABLE : ByteState.PRESENT, bytes, Issue.NONE);
    }
    // Direct writer tests name their thread explicitly; controller tests use the actual lane.
    private void assertLane() { org.junit.Assert.assertEquals("Totipo-provider-io", Thread.currentThread().getName()); }
}
