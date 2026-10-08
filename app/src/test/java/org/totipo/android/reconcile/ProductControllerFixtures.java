package org.totipo.android.reconcile;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.totipo.*;
import org.totipo.android.LocalReplicaOwner;

/** Test-only counters/faults around real released Java and private NIO. */
public final class ProductControllerFixtures {
    public final AtomicBoolean failClose = new AtomicBoolean();
    public volatile int opens, creates, closes;
    public final AtomicBoolean failDomainClose = new AtomicBoolean();
    public volatile org.totipo.spi.BoundedRead discoveryRead;
    public volatile org.totipo.spi.StoreFailure creationInstallFault;
    public volatile VaultSession session;
    public volatile ForegroundVaultCoordinator coordinator;
    private final ForegroundVaultCoordinator.Operations operations = new ForegroundVaultCoordinator.Operations() {
        CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws IOException {
            var delegate = org.totipo.storage.nio.NioTotipoStore.openPrivate(lease.root());
            var proxy = (org.totipo.spi.TotipoStore) java.lang.reflect.Proxy.newProxyInstance(
                    org.totipo.spi.TotipoStore.class.getClassLoader(), new Class<?>[]{org.totipo.spi.TotipoStore.class},
                    (ignored, method, arguments) -> {
                        if (method.getName().equals("close") && failDomainClose.get()) throw new IllegalStateException("domain close fault");
                        if (method.getName().equals("readVault") && discoveryRead != null) return discoveryRead;
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (method.getName().equals("prepareVault") && creationInstallFault != null
                                    && result instanceof org.totipo.spi.VaultPrepare.Prepared ready) {
                                org.totipo.spi.PreparedVault staged = ready.vault();
                                return new org.totipo.spi.VaultPrepare.Prepared(new org.totipo.spi.PreparedVault() {
                                    public org.totipo.spi.BoundedRead readBack(int size) { return staged.readBack(size); }
                                    public org.totipo.spi.VaultInstall installCanonicalIfAbsent() {
                                        staged.installCanonicalIfAbsent();
                                        return new org.totipo.spi.VaultInstall.Uncertain(creationInstallFault);
                                    }
                                    public org.totipo.spi.VaultReplace replaceCanonical() { return staged.replaceCanonical(); }
                                    public void close() { staged.close(); }
                                });
                            }
                            return result;
                        } catch (java.lang.reflect.InvocationTargetException fault) { throw fault.getCause(); }
                    });
            return new CoordinatedPrivateStore(proxy);
        }
        OpenResult open(CoordinatedPrivateStore store, char[] password) {
            opens++;
            var result = super.open(store, password);
            if (result instanceof OpenResult.Opened opened) session = opened.session();
            return result;
        }
        CreateVaultResult create(CoordinatedPrivateStore store, char[] password) {
            creates++;
            var result = super.create(store, password);
            if (result instanceof CreateVaultResult.Created created) session = created.session();
            return result;
        }
        void close(VaultSession current) {
            if (failClose.get()) throw new IllegalStateException("test close failure");
            closes++; super.close(current);
        }
    };
    public ForegroundVaultCoordinator.Discovery discover(LocalReplicaOwner owner) throws IOException {
        return ForegroundVaultCoordinator.discover(owner, operations);
    }
    public ForegroundVaultCoordinator.Opening open(LocalReplicaOwner owner, char[] credential) throws IOException {
        var result = ForegroundVaultCoordinator.open(owner, credential, operations);
        coordinator = result.vault(); return result;
    }
    public ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        var result = ForegroundVaultCoordinator.create(owner, credential, operations);
        coordinator = result.vault(); return result;
    }
}
