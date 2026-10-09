package org.totipo.android.sync;

import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.totipo.android.provider.ProviderSnapshot.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinatorTest;

public final class VaultBootstrapTest {
    private String thread;
    private byte[] exact;
    @Before public void setup() throws Exception {
        thread = Thread.currentThread().getName(); Thread.currentThread().setName("Totipo-provider-io");
        ForegroundVaultCoordinatorTest.realCoreFixture();
        exact = ForegroundVaultCoordinatorTest.productFixtureScan().vaultCandidates().get(0).bytes();
    }
    @After public void cleanup() throws Exception { Thread.currentThread().setName(thread); ForegroundVaultCoordinatorTest.removeFixture(); }
    private ProviderVaultWriter.Result run(PublicationPort p) { return ProviderVaultWriter.initialize(p, PublicationPort.TREE.locator(), exact, () -> false); }
    @Test public void absentBothCreatesVaultFirstExactReadbackThenDirectoryAndRepeatDoesNothing() {
        var p = new PublicationPort(); p.directoryPresent = false;
        p.onDirectoryCreate = () -> assertArrayEquals(exact, p.vaultBytes);
        assertEquals(ProviderVaultWriter.Status.VERIFIED, run(p).status());
        assertArrayEquals(exact, p.vaultBytes); assertEquals(1,p.vaultCreates); assertEquals(1,p.directoryCreates);
        assertTrue(p.objects.isEmpty()); assertEquals(ProviderVaultWriter.Status.ALREADY_INITIALIZED,run(p).status());
        assertEquals(1,p.vaultCreates); assertEquals(1,p.directoryCreates);
    }
    @Test public void completeEmptyDirectoryRetained() {
        var p = new PublicationPort(); assertEquals(ProviderVaultWriter.Status.VERIFIED,run(p).status());
        assertEquals(1,p.vaultCreates); assertEquals(0,p.directoryCreates);
    }
    @Test public void existingExactNoWritesDifferentAndInvalidNeverWritten() {
        var p = new PublicationPort(); p.vaultBytes=exact.clone();
        assertEquals(ProviderVaultWriter.Status.ALREADY_INITIALIZED,run(p).status()); assertEquals(0,p.vaultCreates);
        p.vaultBytes[86]^=1; assertEquals(ProviderVaultWriter.Status.BLOCKED,run(p).status()); assertEquals(0,p.vaultOutputs);
        p.vaultBytes=exact.clone(); p.vaultBytes[0]^=1; assertEquals(ProviderVaultWriter.Status.BLOCKED,run(p).status()); assertEquals(0,p.vaultOutputs);
    }
    @Test public void orphanAnyPlausibleNameBlocksWithoutReadingAuthentication() {
        var p=new PublicationPort(); p.objects.put("a".repeat(64),new byte[]{1});
        assertEquals(ProviderVaultWriter.Status.BLOCKED,run(p).status()); assertEquals(0,p.vaultCreates);
        assertArrayEquals(new byte[]{1},p.objects.get("a".repeat(64)));
    }
    @Test public void incompleteCoverageBlocks() {
        for(State state:State.values()) if(state!=State.COMPLETE) {
            var p=new PublicationPort(); p.coverage=state;
            assertEquals(ProviderVaultWriter.Status.BLOCKED,run(p).status()); assertEquals(0,p.vaultCreates);
        }
    }
    @Test public void conflictingNamespaceBlocks() {
        for(String kind:List.of("duplicate","wrong-kind","incomplete")) {
            var p=new PublicationPort();
            p.snapshotTransform=s -> {
                var d=p.directory;
                var rows=kind.equals("duplicate")?List.of(d,d):kind.equals("wrong-kind")?
                    List.of(new Document(d.tree(),d.locator(),d.id(),d.parentId(),d.displayName(),"text/plain",null,null)):List.of(d);
                var dirs=kind.equals("incomplete")?List.of(new Directory(d,new Listing("e","dir",List.of(),State.INCOMPLETE,List.of()),List.of())):s.directories();
                return new Scan(s.epoch(),s.tree(),new Listing("e","root",rows,State.COMPLETE,List.of()),dirs,State.COMPLETE,List.of());
            };
            assertEquals(kind,ProviderVaultWriter.Status.BLOCKED,run(p).status()); assertEquals(0,p.vaultCreates);
        }
    }
    @Test public void readonlyNoMutation() {
        var p=new PublicationPort(); p.permissions.put(PublicationPort.TREE.locator(),new SyncFolderBinding.Grants(true,false));
        assertEquals(ProviderVaultWriter.Status.CANCELLED,run(p).status()); assertEquals(0,p.vaultCreates);
    }
    @Test public void uncertainVaultStopsBeforeDirectoryAndNeverRetries() {
        for(String fault:List.of("create","suffix","existingId","open","partial","close","readback")) {
            var p=new PublicationPort(); p.directoryPresent=fault.equals("existingId"); p.bootstrapFault=fault;
            assertEquals(fault,ProviderVaultWriter.Status.UNCERTAIN,run(p).status());
            assertEquals(1,p.vaultCreates); assertEquals(0,p.directoryCreates);
            if(fault.equals("suffix") || fault.equals("existingId")) assertEquals(0,p.vaultOutputs);
        }
    }
    @Test public void partialNamespaceRetainsVaultAndExplicitRetryOnlyCreatesDirectory() {
        var p=new PublicationPort(); p.directoryPresent=false; p.bootstrapFault="directory";
        assertEquals(ProviderVaultWriter.Status.PARTIAL,run(p).status()); assertArrayEquals(exact,p.vaultBytes);
        p.bootstrapFault=""; assertEquals(ProviderVaultWriter.Status.VERIFIED,run(p).status());
        assertEquals(1,p.vaultCreates); assertEquals(2,p.directoryCreates);
    }
    @Test public void cancellationAfterCreatePreventsWriteAndDirectory() {
        var p=new PublicationPort(); var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        p.onVaultCreate=() -> cancelled.set(true);
        assertEquals(ProviderVaultWriter.Status.CANCELLED,ProviderVaultWriter.initialize(p,PublicationPort.TREE.locator(),exact,cancelled::get).status());
        assertEquals(0,p.vaultOutputs); assertEquals(0,p.directoryCreates);
    }
    @Test public void plausibleDirectoryChildAlsoVetoesAndSuffixedNamespaceIsPartial() {
        var p=new PublicationPort(); p.snapshotTransform=s -> {
            var child=p.doc("a".repeat(64));
            child=new Document(child.tree(),child.locator(),child.id(),child.parentId(),child.displayName(),"vnd.android.document/directory",null,null);
            return new Scan(s.epoch(),s.tree(),s.root(),List.of(new Directory(p.directory,new Listing("e","dir",List.of(child),State.COMPLETE,List.of()),List.of())),s.state(),s.issues(),s.vaultCandidates());
        };
        assertEquals(ProviderVaultWriter.Status.BLOCKED,run(p).status()); assertEquals(0,p.vaultCreates);
        var suffix=new PublicationPort();suffix.directoryPresent=false;suffix.bootstrapFault="directorySuffix";
        assertEquals(ProviderVaultWriter.Status.PARTIAL,run(suffix).status());assertArrayEquals(exact,suffix.vaultBytes);
    }
    @Test public void postflightConflictNeverSucceeds() {
        var p=new PublicationPort(); p.snapshotTransform=s -> {
            if(p.vaultOutputs==0) return s;
            var rows=new ArrayList<>(s.root().rows()); rows.add(s.vaultCandidates().get(0).document());
            return new Scan(s.epoch(),s.tree(),new Listing("e","root",rows,State.COMPLETE,List.of()),s.directories(),s.state(),s.issues(),s.vaultCandidates());
        };
        assertEquals(ProviderVaultWriter.Status.UNCERTAIN,run(p).status()); assertEquals(0,p.directoryCreates);
    }
}
