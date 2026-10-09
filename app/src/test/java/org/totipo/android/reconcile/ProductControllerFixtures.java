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
    public volatile java.util.function.Consumer<String> operationHook = name -> {};
    public volatile org.totipo.spi.ObjectWrite tokenWriteFault;
    public volatile boolean persistBeforeTokenFault;
    public volatile SaveResult.Reason saveFailure;
    public volatile int saves, refreshes;
    public volatile VaultSession session;
    public volatile ForegroundVaultCoordinator coordinator;
    private final ForegroundVaultCoordinator.Operations operations = new ForegroundVaultCoordinator.Operations() {
        CoordinatedPrivateStore storage(LocalReplicaOwner.Lease lease) throws IOException {
            var delegate = org.totipo.storage.nio.NioStoreComposition.coordinatedDelegate(lease.root(), new org.totipo.storage.nio.NioDurability());
            var proxy = (org.totipo.spi.TotipoStore) java.lang.reflect.Proxy.newProxyInstance(
                    org.totipo.spi.TotipoStore.class.getClassLoader(), new Class<?>[]{org.totipo.spi.TotipoStore.class},
                    (ignored, method, arguments) -> {
                        if (method.getName().equals("close") && failDomainClose.get()) throw new IllegalStateException("domain close fault");
                        if (method.getName().equals("readVault") && discoveryRead != null) return discoveryRead;
                        operationHook.accept(method.getName());
                        if (method.getName().equals("publishObject") && tokenWriteFault != null) {
                            if (persistBeforeTokenFault) method.invoke(delegate, arguments);
                            return tokenWriteFault;
                        }
                        try {
                            Object result = method.invoke(delegate, arguments);
                            if (method.getName().equals("createVault") && creationInstallFault != null)
                                return new org.totipo.spi.VaultCreate.Uncertain(creationInstallFault);
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
        SaveResult save(CreateToken editor) {
            saves++;
            return saveFailure == null ? super.save(editor) : new SaveResult.Failed(saveFailure);
        }
        void refresh(VaultSession session, CoordinatedPrivateStore store) {
            operationHook.accept("requestRefresh"); refreshes++; super.refresh(session, store);
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
    public ForegroundVaultCoordinator.Opening join(LocalReplicaOwner owner, byte[] exact, char[] credential, java.util.function.BooleanSupplier cancelled) throws IOException {
        var result = ForegroundVaultCoordinator.join(owner, exact, credential, cancelled, operations);
        coordinator = result.vault(); return result;
    }
    public ForegroundVaultCoordinator.Creation create(LocalReplicaOwner owner, char[] credential) throws IOException {
        var result = ForegroundVaultCoordinator.create(owner, credential, operations);
        coordinator = result.vault(); return result;
    }
}
