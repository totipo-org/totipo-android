package org.totipo.android.reconcile;

import org.junit.*;
import org.totipo.*;
import org.totipo.spi.*;
import static org.junit.Assert.*;

public final class DetachedVaultAuthenticationTest {
    private static byte[] exact;
    @BeforeClass public static void fixture() throws Exception {
        ForegroundVaultCoordinatorTest.realCoreFixture(); exact=ForegroundVaultCoordinatorTest.productFixtureScan().vaultCandidates().get(0).bytes();
    }
    @AfterClass public static void cleanup() throws Exception { ForegroundVaultCoordinatorTest.removeFixture(); }
    @Test public void realCorrectPasswordWrongPasswordAndInvalidInput() {
        assertTrue(DetachedVaultAuthentication.authenticate(exact,"M1H disposable fixture".toCharArray()));
        assertFalse(DetachedVaultAuthentication.authenticate(exact,"wrong".toCharArray()));
        assertThrows(IllegalArgumentException.class,()->DetachedVaultAuthentication.authenticate(exact,new char[]{'\ud800'}));
    }
    @Test public void javaOwnsStoreOnSuccessFailureAndInvalidPassword() {
        for(char[] credential:new char[][]{"M1H disposable fixture".toCharArray(),"wrong".toCharArray(),new char[]{'\ud800'}}) {
            var store=new DetachedVaultAuthentication.CandidateStore(exact);
            var closes=new java.util.concurrent.atomic.AtomicInteger();
            TotipoStore tracked=(TotipoStore)java.lang.reflect.Proxy.newProxyInstance(TotipoStore.class.getClassLoader(),new Class<?>[]{TotipoStore.class},(ignored,method,args)->{
                if(method.getName().equals("close")) closes.incrementAndGet();
                try{return method.invoke(store,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            });
            try {
                var result=Totipo.open(tracked,credential);
                if(result instanceof OpenResult.Opened opened) { opened.session().close(); opened.session().close(); }
            } catch(IllegalArgumentException expected) { }
            assertEquals(1,closes.get());
            assertThrows(IllegalStateException.class,store::scanObjects);
            store.close();
        }
    }
    @Test public void transientStoreCopiesAndIsReadOnlyEmptyComplete() {
        byte[] input=exact.clone(); var store=new DetachedVaultAuthentication.CandidateStore(input); input[0]^=1;
        assertArrayEquals(exact,((BoundedRead.Present)store.readVault(87)).bytes());
        assertTrue(store.scanObjects() instanceof ObjectScan.Complete); assertTrue(store.scanObjects().entries().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->store.createVault(exact));
        assertThrows(UnsupportedOperationException.class,()->store.publishObject(new ObjectName("a".repeat(64)),new byte[0]));
        store.close();
    }
    @Test public void malformedAndWrongSizeBeforeAuthentication() {
        byte[] bad=exact.clone();bad[0]^=1;
        assertThrows(IllegalArgumentException.class,()->DetachedVaultAuthentication.authenticate(bad,null));
        for(int length:new int[]{0,86,88}) assertThrows(IllegalArgumentException.class,()->DetachedVaultAuthentication.authenticate(new byte[length],null));
    }
}
