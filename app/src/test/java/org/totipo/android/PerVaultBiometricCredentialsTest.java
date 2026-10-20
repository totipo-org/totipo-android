package org.totipo.android;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.crypto.*;

/** Actual production envelope/transaction/storage with only the hardware key port replaced. */
public final class PerVaultBiometricCredentialsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    VaultRegistry registry;
    String a, b;
    final String vault = "a".repeat(64);
    final Keys keys = new Keys();
    static final class Keys implements AndroidBiometricCredentials.Keys {
        final Map<String, SecretKey> values = new HashMap<>();
        boolean failDelete, invalid;
        public SecretKey find(String alias) throws Exception { if (invalid) throw new java.security.InvalidKeyException(); return values.get(alias); }
        public SecretKey create(String alias) throws Exception {
            assertFalse(values.containsKey(alias)); var key = KeyGenerator.getInstance("AES").generateKey(); values.put(alias, key); return key;
        }
        public void delete(String alias) throws Exception { if (failDelete) throw new IOException(); values.remove(alias); }
    }
    @Before public void setup() throws Exception {
        registry = new VaultRegistry(temporary.newFolder().toPath().toRealPath());
        a = registry.reserve("same label").id(); b = registry.reserve("same label").id();
    }
    BiometricCredentialStorage storage(String id) throws Exception { return new BiometricCredentialStorage(registry, id); }
    AndroidBiometricCredentials credentials(String id) throws Exception { return credentials(id, storage(id)); }
    AndroidBiometricCredentials credentials(String id, BiometricCredentialStorage store) {
        return new AndroidBiometricCredentials(id, store, keys, () -> true);
    }
    void enroll(AndroidBiometricCredentials credentials) throws Exception {
        try (var password = new PasswordBuffer(new char[]{'s','a','m','e'})) { credentials.enrollment(vault).encrypt(password); }
        assertTrue(credentials.configured());
    }
    @Test public void equalPasswordsAndEqualProtocolIdsStillHaveIndependentKeysAndRecords() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        assertNotEquals(BiometricRecord.alias(a), BiometricRecord.alias(b));
        assertNotSame(keys.find(BiometricRecord.alias(a)), keys.find(BiometricRecord.alias(b)));
        assertFalse(Arrays.equals(Files.readAllBytes(storage(a).record), Files.readAllBytes(storage(b).record)));
        assertArrayEquals("same".toCharArray(), ca.unlock().decrypt());
        assertArrayEquals("same".toCharArray(), cb.unlock().decrypt());
        ca.delete(); assertFalse(ca.configured()); assertTrue(cb.configured());
        assertArrayEquals("same".toCharArray(), cb.unlock().decrypt());
        enroll(ca); assertArrayEquals("same".toCharArray(), cb.unlock().decrypt());
    }
    @Test public void swappingFilesCannotChooseAnotherEntriesKey() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        byte[] original = Files.readAllBytes(storage(a).record);
        Files.write(storage(b).record, original);
        assertThrows(IOException.class, cb::unlock); assertFalse(cb.configured());
        assertArrayEquals("same".toCharArray(), ca.unlock().decrypt());
    }
    @Test public void rewritingLocalIdentityAndAliasStillCannotAuthenticateWithOtherKey() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        var saved = BiometricRecord.decode(Files.readAllBytes(storage(a).record), a);
        Files.write(storage(b).record, new BiometricRecord(b, saved.vaultId, saved.iv, saved.ciphertext).encode());
        assertThrows(AEADBadTagException.class, () -> cb.unlock().decrypt());
        // Even deliberately substituting A's key for B cannot defeat the AAD entry binding.
        keys.values.put(BiometricRecord.alias(b), keys.find(BiometricRecord.alias(a)));
        assertThrows(AEADBadTagException.class, () -> cb.unlock().decrypt());
    }
    @Test public void changedVaultIdCannotAuthenticateAndArbitraryAliasIsRejected() throws Exception {
        var ca = credentials(a); enroll(ca); var store = storage(a);
        var saved = BiometricRecord.decode(Files.readAllBytes(store.record), a);
        Files.write(store.record, new BiometricRecord(a, "b".repeat(64), saved.iv, saved.ciphertext).encode());
        assertThrows(AEADBadTagException.class, () -> ca.unlock().decrypt());
        byte[] bytes = saved.encode();
        byte[] alias = BiometricRecord.alias(a).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i <= bytes.length - alias.length; i++) if (Arrays.equals(alias, Arrays.copyOfRange(bytes, i, i + alias.length))) { bytes[i] ^= 1; break; }
        Files.write(store.record, bytes); assertThrows(IOException.class, ca::unlock);
    }
    @Test public void cancelledReplacementPreservesPriorKeyAndRecordAndFreshOperationIsSingleUse() throws Exception {
        var ca = credentials(a); enroll(ca); var key = keys.find(BiometricRecord.alias(a));
        byte[] prior = Files.readAllBytes(storage(a).record);
        ca.enrollment(vault); // user cancellation: no authorized encryption/write
        assertSame(key, keys.find(BiometricRecord.alias(a))); assertArrayEquals(prior, Files.readAllBytes(storage(a).record));
        var unlock = ca.unlock(); assertArrayEquals("same".toCharArray(), unlock.decrypt());
        assertThrows(IllegalStateException.class, unlock::decrypt);
    }
    @Test public void missingKeyNeverRecreatedBesideExistingRecord() throws Exception {
        var ca = credentials(a); enroll(ca); keys.values.remove(BiometricRecord.alias(a));
        assertFalse(ca.configured()); assertThrows(IllegalStateException.class, ca::unlock);
        assertThrows(IllegalStateException.class, () -> ca.enrollment(vault)); assertTrue(keys.values.isEmpty());
        ca.delete(); enroll(ca);
    }
    @Test public void failureBeforeReplacementRetainsOldBytesButBlocksUseAcrossRestart() throws Exception {
        var ca = credentials(a); enroll(ca); byte[] prior = Files.readAllBytes(storage(a).record);
        var failed = new BiometricCredentialStorage(registry, a, (record, next, bytes) -> { throw new IOException(); }, Files::deleteIfExists);
        var attempt = credentials(a, failed).enrollment(vault);
        try (var p = new PasswordBuffer("replacement".toCharArray())) { assertThrows(IOException.class, () -> attempt.encrypt(p)); }
        assertArrayEquals(prior, Files.readAllBytes(storage(a).record));
        assertFalse(credentials(a).configured()); assertThrows(IOException.class, () -> credentials(a).unlock());
        ca.delete(); enroll(ca);
    }
    @Test public void lostAcknowledgementAfterRenameRemainsUnusableUntilExplicitCleanup() throws Exception {
        var failed = new BiometricCredentialStorage(registry, a, (record, next, bytes) -> {
            BiometricCredentialStorage.atomicWrite(record, next, bytes); throw new IOException();
        }, Files::deleteIfExists);
        var ca = credentials(a, failed);
        try (var p = new PasswordBuffer("same".toCharArray())) { assertThrows(IOException.class, () -> ca.enrollment(vault).encrypt(p)); }
        assertTrue(Files.exists(storage(a).record)); assertFalse(credentials(a).configured());
        credentials(a).delete(); enroll(credentials(a));
    }
    @Test public void keyDeletionFailureLeavesDurableBlockerAndRetryDoesNotTouchB() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        keys.failDelete = true; assertThrows(IOException.class, ca::delete);
        assertFalse(credentials(a).configured()); assertTrue(storage(a).recovery());
        assertNotNull(keys.find(BiometricRecord.alias(a))); assertTrue(cb.configured());
        keys.failDelete = false; credentials(a).delete(); assertFalse(storage(a).recovery());
        assertNull(keys.find(BiometricRecord.alias(a))); assertTrue(cb.configured());
    }
    @Test public void recordDeletionFailureStillDeletesOwnKeyAndCanBeRetried() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        var failed = new BiometricCredentialStorage(registry, a, BiometricCredentialStorage::atomicWrite, path -> {
            if (path.getFileName().toString().equals("biometric-v2")) throw new IOException(); Files.deleteIfExists(path);
        });
        assertThrows(IOException.class, () -> credentials(a, failed).delete());
        assertTrue(storage(a).recovery()); assertNull(keys.find(BiometricRecord.alias(a))); assertTrue(cb.configured());
        credentials(a).delete(); assertFalse(storage(a).present()); assertFalse(storage(a).recovery());
    }
    @Test public void interruptedWriteAndDeleteMarkersFailClosedAndCleanupIsIdempotent() throws Exception {
        for (String suffix : List.of(".next", ".pending", ".disabled")) {
            var ca = credentials(a); enroll(ca);
            Files.write(storage(a).record.resolveSibling("biometric-v2" + suffix), new byte[0]);
            assertFalse(credentials(a).configured()); assertThrows(IOException.class, () -> credentials(a).unlock());
            ca.delete(); ca.delete(); assertFalse(storage(a).recovery());
        }
    }
    @Test public void unsafePathsCannotEscapeOrDeleteAnotherEntry() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> BiometricRecord.alias("../x"));
        assertThrows(IllegalArgumentException.class, () -> BiometricRecord.alias("A".repeat(32)));
        var store = storage(a); var other = storage(b); Files.write(other.record, new byte[]{1});
        Files.createSymbolicLink(store.record, other.record);
        assertThrows(IOException.class, () -> storage(a)); assertArrayEquals(new byte[]{1}, Files.readAllBytes(other.record));
    }
    @Test public void malformedAndOversizedRecordsRemainExplicitRecoveryAndNeverTouchB() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        for (byte[] bytes : List.of(new byte[]{1, 2, 3}, new byte[BiometricRecord.MAX_BYTES + 1])) {
            Files.write(storage(a).record, bytes); assertFalse(ca.configured()); assertThrows(IOException.class, ca::unlock); assertTrue(cb.configured());
        }
        ca.delete(); enroll(ca);
    }
    @Test public void disableRetiresAlreadyPreparedOperationsAndRestartNeedsFreshOperation() throws Exception {
        var ca = credentials(a); enroll(ca); var old = ca.unlock(); ca.delete();
        assertThrows(IllegalStateException.class, old::decrypt);
        enroll(ca); assertArrayEquals("same".toCharArray(), credentials(a).unlock().decrypt());
        var pending = ca.enrollment(vault); ca.unlock();
        try (var p = new PasswordBuffer("same".toCharArray())) { assertThrows(IllegalStateException.class, () -> pending.encrypt(p)); }
    }
    @Test public void unavailableHardwareAndInvalidatedKeyNeverDeleteOrRecreateCredentials() throws Exception {
        var ca = credentials(a); var cb = credentials(b); enroll(ca); enroll(cb);
        byte[] prior = Files.readAllBytes(storage(a).record); int count = keys.values.size();
        var hardware = new AndroidBiometricCredentials(a, storage(a), keys, () -> false);
        assertFalse(hardware.available()); assertTrue(hardware.configured());
        assertThrows(IllegalStateException.class, () -> hardware.enrollment(vault));
        keys.invalid = true;
        assertFalse(ca.configured()); assertThrows(java.security.InvalidKeyException.class, ca::unlock);
        assertThrows(java.security.InvalidKeyException.class, () -> ca.enrollment(vault));
        assertEquals(count, keys.values.size()); assertArrayEquals(prior, Files.readAllBytes(storage(a).record));
        keys.invalid = false; assertTrue(ca.configured()); assertTrue(cb.configured());
    }
    @Test public void lostPendingRemovalAcknowledgementRestoresPersistentBlocker() throws Exception {
        var ca = credentials(a); enroll(ca);
        var failed = new BiometricCredentialStorage(registry, a, BiometricCredentialStorage::atomicWrite, path -> {
            Files.deleteIfExists(path);
            if (path.getFileName().toString().endsWith(".pending")) throw new IOException();
        });
        try (var p = new PasswordBuffer("same".toCharArray())) {
            assertThrows(IOException.class, () -> credentials(a, failed).enrollment(vault).encrypt(p));
        }
        assertTrue(storage(a).recovery()); assertFalse(credentials(a).configured());
        ca.delete(); enroll(ca);
    }

}
