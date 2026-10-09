package org.totipo.safqualification;

import java.nio.file.*;
import java.util.*;
import org.totipo.*;
import org.totipo.spi.*;
import org.totipo.storage.nio.*;

/** Disposable root exclusively owned by this sequential standalone harness. */
public final class LocalStoreQualification {
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Local coordinated storage regression"); }
    private static TotipoStore store(Path root) throws Exception {
        return NioStoreComposition.coordinatedDelegate(root, new NioDurability());
    }
    private static void observed(VaultSession session, int count) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < end) {
            if (session.state().observation() instanceof ObservationProgress.Finished
                    && session.state().tokens().size() == count) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Local observation did not finish");
    }
    public static void run(Path base) throws Exception {
        Path root = Files.createDirectory(base.resolve("local-coordinated"));
        try (var delegate = store(root)) {
            require(delegate.readVault(87) instanceof BoundedRead.Absent);
            require(delegate.scanObjects().entries().isEmpty());
        }
        VaultId identity;
        try (var session = ((CreateVaultResult.Created)Totipo.create(store(root), new char[0])).session()) {
            identity = session.vaultId(); observed(session, 0);
        }
        byte[] vault = Files.readAllBytes(root.resolve("vault"));
        require(identity.equals(Totipo.vaultId(vault)));
        RevisionId revision;
        try (var session = ((OpenResult.Opened)Totipo.open(store(root), new char[0])).session()) {
            require(identity.equals(session.vaultId())); observed(session, 0);
            try (var secret = NewSecret.copyOf(new byte[]{1,2,3,4}); var token = session.state().createToken()) {
                var result = token.issuer("r19 disposable qualification").secret(secret).save();
                require(result instanceof SaveResult.Saved);
                revision = ((SaveResult.Saved)result).revisions().get(0);
            }
            observed(session, 1);
        }
        byte[] representation;
        try (var delegate = store(root)) {
            var scan = delegate.scanObjects();
            require(scan instanceof ObjectScan.Complete && scan.entries().size() == 1);
            var name = new ObjectName(revision.hex());
            representation = ((BoundedRead.Present)delegate.readObject(name, 1024)).bytes();
            require(representation.length == 1024);
            require(delegate.publishObject(name, representation) instanceof ObjectWrite.AlreadyPresentExact);
            byte[] different = representation.clone(); different[0] ^= 1;
            require(delegate.publishObject(name, different) instanceof ObjectWrite.ExistingDifferent);
            require(Arrays.equals(representation, ((BoundedRead.Present)delegate.readObject(name, 1024)).bytes()));
        }
        try (var session = ((OpenResult.Opened)Totipo.open(store(root), new char[0])).session()) {
            observed(session, 1); require(identity.equals(session.vaultId()));
        }
        try (var paths = Files.list(root)) {
            require(paths.map(p -> p.getFileName().toString()).collect(java.util.stream.Collectors.toSet())
                    .equals(Set.of("vault", "objects-v1")));
        }
        try (var paths = Files.list(root.resolve("objects-v1"))) {
            require(paths.map(p -> p.getFileName().toString()).toList().equals(List.of(revision.hex())));
        }
        Path orphan = Files.createDirectory(base.resolve("orphan-veto"));
        Files.createDirectory(orphan.resolve("objects-v1"));
        Path candidate = Files.write(orphan.resolve("objects-v1").resolve(revision.hex()), representation);
        var veto = Totipo.create(store(orphan), new char[0]);
        require(veto instanceof CreateVaultResult.Failed failed
                && failed.reason() == CreateVaultResult.FailureReason.OBJECT_DATA_OBSERVED);
        require(!Files.exists(orphan.resolve("vault")) && Arrays.equals(representation, Files.readAllBytes(candidate)));
        require(Arrays.equals(vault, Files.readAllBytes(root.resolve("vault"))));
    }
}
