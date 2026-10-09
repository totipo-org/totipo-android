package org.totipo.android.debug;

import android.annotation.SuppressLint;
import android.os.Build;
import org.totipo.*;
import org.totipo.spi.*;
import org.totipo.storage.nio.NioDurability;
import org.totipo.storage.nio.NioTotipoStore;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Uses released public APIs with default durability, never injected/replaced operations.
 * Newer Java library calls are deliberately exercised at runtime: a LinkageError is evidence,
 * not a reason to add a compatibility implementation or silently skip released code paths. */
@SuppressLint("NewApi")
final class LocalNioQualification {
    private final StringBuilder report = new StringBuilder();
    private boolean primitivesOk = true;
    private boolean workflowsOk;
    private String stage = "setup";
    @FunctionalInterface private interface Check { void run() throws Exception; }

    static synchronized String run(Path base) {
        LocalNioQualification probe = new LocalNioQualification();
        probe.line("M1C local NIO qualification v2 (private mode)");
        probe.line("API level=" + Build.VERSION.SDK_INT);
        probe.line("Java boundary=core 0.2.0 + storage-nio 0.2.0; default NioDurability");
        probe.line("Storage=noBackupFilesDir/m1c-nio-qualification/<run-id>; disposable only");
        probe.line("Mode=coordinatedDelegate with NioDurability; harness exclusively owns root and serializes runs/handles");
        probe.line("No independent writer, Syncthing, SAF or cloud process writes into this root.");
        probe.line("Private installation evidence comes from released workflow outcomes; no injected operations.");
        probe.line("Accepted force and reopen do not prove physical power-loss survival.");
        try {
            Files.createDirectories(base);
            Path root = Files.createDirectory(base.resolve(UUID.randomUUID().toString()));
            probe.line("Run ID=" + root.getFileName());
            probe.primitives(Files.createDirectory(root.resolve("primitives")));
            probe.workflows(Files.createDirectory(root.resolve("store")));
        } catch (Exception | LinkageError failure) {
            probe.exception("Qualification interrupted at " + probe.stage, failure);
            probe.line("Remaining dependent workflows=NOT EXERCISED");
        }
        probe.line("Harness outcome=" + (probe.primitivesOk && probe.workflowsOk
                ? "ALL REQUIRED CHECKS SUCCEEDED; human Gate-A review required"
                : "NOT ALL REQUIRED CHECKS SUCCEEDED; human Gate-A review required"));
        probe.line("Gate-A verdict=INCONCLUSIVE until physical evidence is reviewed");
        return probe.report.toString();
    }
    private void line(String text) { report.append(text).append('\n'); }
    private void exception(String label, Throwable failure) {
        line(label + ": " + failure.getClass().getName());
        // Exception messages may contain absolute paths; classes and cause chain suffice.
        Throwable cause = failure.getCause();
        for (int i = 0; cause != null && i < 8; i++, cause = cause.getCause())
            line("  cause=" + cause.getClass().getName());
    }
    private boolean primitive(String name, boolean required, Check check) {
        try { check.run(); line("Primitive " + name + "=SUPPORTED + succeeded"); return true; }
        catch (UnsupportedOperationException | AtomicMoveNotSupportedException failure) {
            line("Primitive " + name + "=UNSUPPORTED"); exception("  exception", failure);
        } catch (Exception | LinkageError failure) {
            line("Primitive " + name + "=SUPPORTED + failed (invocation attempted)");
            exception("  exception", failure);
        }
        if (required) primitivesOk = false;
        return false;
    }
    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Qualification expectation failed");
    }
    private void primitives(Path root) {
        stage = "primitives";
        primitive("directory creation and no-follow type observation", true, () -> {
            Path child = Files.createDirectory(root.resolve("child"));
            require(Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isDirectory());
        });
        primitive("temporary file and regular-file observation", true, () -> {
            Path temp = Files.createTempFile(root, ".totipo-object-", ".tmp");
            require(Files.readAttributes(temp, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isRegularFile());
            Files.delete(temp);
        });
        primitive("bounded actual-byte read with NOFOLLOW_LINKS", true, () -> {
            Path file = Files.write(root.resolve("bounded"), new byte[] {1, 2, 3, 4});
            try (InputStream in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                require(Arrays.equals(new byte[] {1, 2, 3}, in.readNBytes(3)));
                require(in.read() == 4 && in.read() == -1);
            }
        });
        primitive("no-follow symlink observation/rejection (optional fixture)", false, () -> {
            Path file = Files.write(root.resolve("symlink-source"), new byte[] {7});
            Path link = Files.createSymbolicLink(root.resolve("symlink"), file);
            require(Files.readAttributes(link, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isSymbolicLink());
            boolean rejected = false;
            try (InputStream in = Files.newInputStream(link, LinkOption.NOFOLLOW_LINKS)) {
                in.read();
            } catch (java.io.IOException expected) {
                rejected = true; line("  rejected open=" + expected.getClass().getName());
            }
            require(rejected);
        });
        primitive("exact direct-child filenames", true, () -> {
            Files.write(root.resolve("vault"), new byte[] {1});
            Files.write(root.resolve("VAULT"), new byte[] {2});
            Set<String> names = new HashSet<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) names.add(entry.getFileName().toString());
            }
            require(names.contains("vault") && names.contains("VAULT"));
            require(Files.readAllBytes(root.resolve("vault"))[0] == 1);
        });
        primitive("FileChannel write loop and force(true)", true, () -> {
            try (FileChannel channel = FileChannel.open(root.resolve("forced"),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(new byte[] {1, 2, 3});
                while (buffer.hasRemaining()) require(channel.write(buffer) > 0);
                channel.force(true);
            }
        });
        primitive("hard link and exclusive collision (shared-mode evidence only)", false, () -> {
            Path source = Files.write(root.resolve("link-source"), new byte[] {9});
            Path target = root.resolve("link-target");
            Files.createLink(target, source);
            require(Files.isSameFile(target, source));
            try { Files.createLink(target, source); throw new IllegalStateException("Overwrote link"); }
            catch (FileAlreadyExistsException expected) {
                line("  collision=" + expected.getClass().getName());
            }
            Files.delete(source);
            require(Files.readAllBytes(target)[0] == 9);
        });
        primitive("directory open READ/NOFOLLOW and force(true)", true, () -> {
            try (FileChannel channel = FileChannel.open(root.resolve("child"),
                    StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
        });
        primitive("released NioDurability root-directory persistence request", true,
                () -> new NioDurability().syncDirectory(root));
        primitive("ATOMIC_MOVE + REPLACE_EXISTING", false, () -> {
            Path source = Files.write(root.resolve("atomic-source"), new byte[] {4});
            Path target = Files.write(root.resolve("atomic-target"), new byte[] {1});
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            require(Files.readAllBytes(target)[0] == 4);
        });
        primitive("ordinary move without replacement options", true, () -> {
            Path source = Files.write(root.resolve("private-move-source"), new byte[] {6});
            Path target = root.resolve("private-move-target");
            Files.move(source, target);
            require(!Files.exists(source) && Files.readAllBytes(target)[0] == 6);
        });
        primitive("replacement move fallback facility (direct exercise)", true, () -> {
            Path source = Files.write(root.resolve("move-source"), new byte[] {5});
            Path target = Files.write(root.resolve("move-target"), new byte[] {1});
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            require(Files.readAllBytes(target)[0] == 5);
        });
        line("POSIX mode/owner mutation=NOT EXERCISED (not used by released implementation)");
    }
    private void outcome(String name, Object result) {
        line("Workflow " + name + "=" + result.getClass().getName());
        // SPI record toString contains only reason/kind/length for these non-Present results.
        if (result instanceof ObjectWrite.Failed || result instanceof ObjectWrite.Uncertain
                || result instanceof BoundedRead.Unavailable || result instanceof ObjectScan.Incomplete)
            line("  classification=" + result);
    }
    private VaultSession open(Path root, char[] credential, VaultId vaultId) throws Exception {
        stage = "authenticate reopen";
        OpenResult result = Totipo.open(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability()), credential);
        outcome("authenticate reopen", result);
        require(result instanceof OpenResult.Opened);
        VaultSession session = ((OpenResult.Opened) result).session();
        if (!session.vaultId().equals(vaultId)) { session.close(); throw new IllegalStateException("Root changed"); }
        line("Workflow same VaultId=confirmed");
        try { awaitObservation(session); }
        catch (Exception | LinkageError failure) { session.close(); throw failure; }
        return session;
    }
    private void awaitObservation(VaultSession session) throws Exception {
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Flow.Subscription> subscription =
                new java.util.concurrent.atomic.AtomicReference<>();
        session.states().subscribe(new java.util.concurrent.Flow.Subscriber<VaultState>() {
            @Override public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
                subscription.set(value); value.request(Long.MAX_VALUE);
            }
            @Override public void onNext(VaultState value) {
                if (value.observation() instanceof ObservationProgress.Finished) finished.countDown();
            }
            @Override public void onError(Throwable failure) { error.set(failure); finished.countDown(); }
            @Override public void onComplete() { finished.countDown(); }
        });
        try {
            require(finished.await(30, java.util.concurrent.TimeUnit.SECONDS));
            if (error.get() != null) {
                exception("Workflow observation failure", error.get());
                throw new IllegalStateException("Observation failed", error.get());
            }
            require(session.state().observation() instanceof ObservationProgress.Finished);
            require(session.state().diagnostics().isEmpty());
            line("Workflow initial core observation=finished without diagnostics");
        } finally {
            if (subscription.get() != null) subscription.get().cancel();
        }
    }
    private static Set<String> children(Path root) throws Exception {
        Set<String> names = new HashSet<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) names.add(entry.getFileName().toString());
        }
        return names;
    }
    private void workflows(Path root) throws Exception {
        char[] initial = "M1C disposable initial credential - NOT SECRET".toCharArray();
        try {
            stage = "store initialization";
            try (TotipoStore store = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability())) {
                BoundedRead read = store.readVault(87); outcome("initial vault observation", read);
                require(read instanceof BoundedRead.Absent);
                ObjectScan scan = store.scanObjects(); outcome("initial namespace observation", scan);
                require(scan instanceof ObjectScan.Complete && scan.entries().isEmpty());
            }
            require(children(root).isEmpty());
            line("Workflow store open/close=completed; canonical VAULT and objects namespace remain absent");
            stage = "create vault (stage/force/read-back/private ordinary move/canonical file force/root force)";
            CreateVaultResult created = Totipo.create(org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability()), initial);
            outcome("create vault", created);
            require(created instanceof CreateVaultResult.Created);
            VaultId vaultId;
            try (VaultSession session = ((CreateVaultResult.Created) created).session()) {
                vaultId = session.vaultId();
            }
            line("Workflow private initial VAULT installation=acknowledged by CreateVaultResult.Created");
            line("  Released path=complete forced stage -> ordinary move without replacement options -> canonical file force -> root force");
            require(Files.isRegularFile(root.resolve("vault"), LinkOption.NOFOLLOW_LINKS));
            require(!Files.exists(root.resolve("objects-v1"), LinkOption.NOFOLLOW_LINKS));
            require(children(root).equals(Set.of("vault")));
            line("Workflow close after create=completed; no abandoned VAULT stages");
            TokenId token;
            RevisionId revision;
            try (VaultSession session = open(root, initial, vaultId)) {
                stage = "core immutable TOKEN save (mkdir/root force/stage force/private ordinary move/canonical file force/directory force)";
                try (NewSecret secret = NewSecret.copyOf(new byte[] {1,2,3,4,5,6,7,8,9,10});
                     CreateToken builder = session.state().createToken()) {
                    SaveResult result = builder.issuer("M1C qualification").account("disposable").secret(secret).save();
                    outcome("first immutable publication via core", result);
                    require(result instanceof SaveResult.Saved);
                    SaveResult.Saved saved = (SaveResult.Saved) result;
                    token = saved.tokenId();
                    require(saved.revisions().size() == 1);
                    revision = saved.revisions().get(0);
                    line("Workflow core revision=" + revision.hex());
                    line("Workflow private immutable-object installation=acknowledged by SaveResult.Saved");
                    line("  Released path=root/namespace persistence -> complete forced stage -> ordinary move without replacement options -> canonical file force -> namespace force");
                }
            }
            stage = "public SPI exact-existing acknowledgement";
            // These are the actual bytes authored above by core, never a fabricated envelope.
            // Session has closed before independent store ownership.
            try (TotipoStore store = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(root, new org.totipo.storage.nio.NioDurability())) {
                ObjectScan scan = store.scanObjects(); outcome("created namespace scan", scan);
                require(scan instanceof ObjectScan.Complete && scan.entries().size() == 1);
                ObjectName name = new ObjectName(revision.hex());
                BoundedRead read = store.readObject(name, 1024); outcome("published object bounded read", read);
                require(read instanceof BoundedRead.Present);
                byte[] bytes = ((BoundedRead.Present) read).bytes();
                require(bytes.length == 1024);
                line("Workflow immutable representation size=" + bytes.length + " bytes (expected 1024)");
                ObjectWrite duplicate = store.publishObject(name, bytes);
                outcome("exact duplicate idempotent publication", duplicate);
                require(duplicate instanceof ObjectWrite.AlreadyPresentExact);
                require(Arrays.equals(bytes, ((BoundedRead.Present) store.readObject(name, 1024)).bytes()));
                // Rejected input only: never install fabricated protocol bytes as a new object.
                byte[] different = bytes.clone();
                different[0] ^= 1;
                ObjectWrite preserved = store.publishObject(name, different);
                outcome("different existing target preservation", preserved);
                require(preserved instanceof ObjectWrite.ExistingDifferent);
                require(Arrays.equals(bytes, ((BoundedRead.Present) store.readObject(name, 1024)).bytes()));
                require(children(root.resolve("objects-v1")).equals(Set.of(revision.hex())));
                line("Workflow immutable stages cleaned; exact canonical object preserved");
            }
            try (VaultSession session = open(root, initial, vaultId)) {
                stage = "core object observation after session reopen";
                require(session.state().token(token).isPresent());
                require(session.state().token(token).orElseThrow().heads().stream()
                        .anyMatch(head -> head.revision().equals(revision)));
                line("Workflow core object persistence across reopen=confirmed");
            }
            stage = "final cleanup observation";
            require(children(root).equals(Set.of("vault", "objects-v1")));
            require(children(root.resolve("objects-v1")).equals(Set.of(revision.hex())));
            require(Files.size(root.resolve("vault")) == 87);
            require(Files.size(root.resolve("objects-v1").resolve(revision.hex())) == 1024);
            line("Workflow final layout=one complete VAULT + one complete immutable object; no abandoned stages/canonical partials");
            workflowsOk = true;
            line("Workflow clean final close=completed");
        } finally { Arrays.fill(initial, '\0'); }
    }
}
