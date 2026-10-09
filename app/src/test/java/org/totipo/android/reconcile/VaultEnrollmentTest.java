package org.totipo.android.reconcile;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.totipo.*;
import org.totipo.spi.*;
import org.totipo.android.TestReplicaOwners;
import org.totipo.android.LocalReplicaOwner;
import static org.junit.Assert.*;

public final class VaultEnrollmentTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static byte[] exact;
    @BeforeClass public static void fixture() throws Exception { ForegroundVaultCoordinatorTest.realCoreFixture(); exact=ForegroundVaultCoordinatorTest.productFixtureScan().vaultCandidates().get(0).bytes(); }
    @AfterClass public static void cleanup() throws Exception { ForegroundVaultCoordinatorTest.removeFixture(); }
    private char[] password() { return "M1H disposable fixture".toCharArray(); }
    @Test public void realEnrollmentUsesOneDomainGateAndNormalOpenOutsideGate() throws Exception {
        var owner=TestReplicaOwners.create(temporary.newFolder().toPath()); var domains=new AtomicInteger(); var creates=new AtomicInteger();
        var gate=new AtomicReference<CoordinatedPrivateStore>();
        var ops=new ForegroundVaultCoordinator.Operations() {
            CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws java.io.IOException {
                domains.incrementAndGet();
                var delegate=org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(),new org.totipo.storage.nio.NioDurability());
                var proxy=(TotipoStore)java.lang.reflect.Proxy.newProxyInstance(TotipoStore.class.getClassLoader(),new Class<?>[]{TotipoStore.class},(ignored,method,args)->{
                    if(method.getName().equals("createVault")) { assertTrue(gate.get().exclusiveHeldByCurrentThread());creates.incrementAndGet(); }
                    try { return method.invoke(delegate,args); } catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
                var store=new CoordinatedPrivateStore(proxy);gate.set(store);return store;
            }
            OpenResult open(CoordinatedPrivateStore store,char[] password) {
                assertFalse(store.exclusiveHeldByCurrentThread());
                var result=super.open(store,password);
                if(!(result instanceof OpenResult.Opened opened)) return result;
                var checked=(VaultSession)java.lang.reflect.Proxy.newProxyInstance(VaultSession.class.getClassLoader(),new Class<?>[]{VaultSession.class},(ignored,method,args)->{
                    if(method.getName().equals("vaultId")) assertFalse("session identity under bridge gate",store.exclusiveHeldByCurrentThread());
                    try{return method.invoke(opened.session(),args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                });
                return new OpenResult.Opened(checked);
            }
        };
        var result=ForegroundVaultCoordinator.join(owner,exact,password(),()->false,ops);
        try(var vault=result.vault()) {
            assertNull(result.failure());assertNull(result.cause());assertEquals(1,domains.get());assertEquals(1,creates.get());
            byte[] snapshot=vault.snapshotVault();snapshot[0]^=1;assertArrayEquals(exact,vault.snapshotVault());
            assertTrue(vault.view().tokens().isEmpty());
        }
    }
    @Test public void failedAndUncertainNeverRetryOrOpenAndCanonicalMismatchNeverSucceeds() throws Exception {
        for(String kind:List.of("failed","uncertain","mismatch")) {
            var owner=TestReplicaOwners.create(temporary.newFolder().toPath());var creates=new AtomicInteger();var opens=new AtomicInteger();
            var ops=new ForegroundVaultCoordinator.Operations() {
                CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws java.io.IOException {
                    var delegate=org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(),new org.totipo.storage.nio.NioDurability());
                    var proxy=(TotipoStore)java.lang.reflect.Proxy.newProxyInstance(TotipoStore.class.getClassLoader(),new Class<?>[]{TotipoStore.class},(ignored,method,args)->{
                        if(method.getName().equals("createVault")) {
                            creates.incrementAndGet();
                            if(kind.equals("failed")) return new VaultCreate.Failed(StoreFailure.UNAVAILABLE);
                            if(kind.equals("uncertain")) return new VaultCreate.Uncertain(StoreFailure.UNAVAILABLE);
                            byte[] wrong=exact.clone();wrong[86]^=1;return delegate.createVault(wrong);
                        }
                        try{return method.invoke(delegate,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                    });return new CoordinatedPrivateStore(proxy);
                }
                OpenResult open(CoordinatedPrivateStore store,char[] password){opens.incrementAndGet();return super.open(store,password);}
            };
            var result=ForegroundVaultCoordinator.join(owner,exact,password(),()->false,ops);
            try(var vault=result.vault()){assertNotNull(kind,result.cause());assertNotEquals(ForegroundVaultCoordinator.State.OPEN,vault.lifecycle());}
            assertEquals(1,creates.get());assertEquals(0,opens.get());
        }
    }
    @Test public void lateExistingExactCanOpenDifferentNeverOpensAndNeitherIsOverwritten() throws Exception {
        for(boolean same:new boolean[]{true,false}) {
            var owner=TestReplicaOwners.create(temporary.newFolder().toPath());byte[] present=exact.clone();if(!same)present[86]^=1;
            try(var lease=owner.acquire()){Files.write(lease.root().resolve("vault"),present);}
            var result=ForegroundVaultCoordinator.join(owner,exact,password(),()->false);
            try(var vault=result.vault()){assertEquals(same,result.failure()==null&&result.cause()==null);}
            try(var lease=owner.acquire()){assertArrayEquals(present,Files.readAllBytes(lease.root().resolve("vault")));}
        }
    }
    @Test public void cancelledBeforeInstallAndOrphanEvidenceLeaveVaultAbsent() throws Exception {
        for(boolean orphan:new boolean[]{false,true}) {
            var owner=TestReplicaOwners.create(temporary.newFolder().toPath());
            try(var lease=owner.acquire()){if(orphan){Files.createDirectories(lease.root().resolve("objects-v1"));Files.write(lease.root().resolve("objects-v1").resolve("a".repeat(64)),new byte[]{1});}}
            var result=ForegroundVaultCoordinator.join(owner,exact,password(),()->!orphan);
            try(var vault=result.vault()){assertNotNull(result.cause());if(orphan)assertTrue(result.cause() instanceof ForegroundVaultCoordinator.LocalObjectEvidence);}
            try(var lease=owner.acquire()){assertFalse(Files.exists(lease.root().resolve("vault")));}
        }
    }
}
