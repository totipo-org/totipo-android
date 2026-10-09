package org.totipo.safqualification;

import android.app.Instrumentation;
import android.os.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Standalone instrumentation: existing user-picked grant, isolated vault, no provider writes. */
public final class SafInboundRegression extends Instrumentation {
    private Object isolated;
    private Path directory;
    private String step = "setup";
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
    private Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void check(boolean condition, String name) {
        step = name; if (!condition) throw new AssertionError("Qualification assertion failed");
        checks++; Bundle result = new Bundle(); result.putString("stream", "M3A " + name + " PASS\n"); sendStatus(0, result);
    }
    private void waitIdle() throws Exception {
        long end = SystemClock.uptimeMillis() + 120000;
        while (SystemClock.uptimeMillis() < end) {
            synchronized (isolated) { if (!(Boolean)field(isolated, "operating") && !(Boolean)field(isolated, "viewQueued") && !(Boolean)field(isolated, "providerActive")) return; }
            SystemClock.sleep(20);
        }
        throw new AssertionError("Worker timed out");
    }
    private void command(String name, Class<?>[] types, Object... args) {
        runOnMainSync(() -> {
            try { if (!(Boolean)call(isolated, name, types, args)) throw new AssertionError("Rejected command"); }
            catch (Exception failure) { throw new AssertionError("Command failed"); }
        });
    }
    private int tokens() throws Exception {
        return ((List<?>) call(call(call(isolated, "snapshot"), "view"), "tokens")).size();
    }
    private void waitTokens(int expected) throws Exception {
        long end = SystemClock.uptimeMillis() + 30000;
        while (tokens() != expected && SystemClock.uptimeMillis() < end) SystemClock.sleep(20);
        check(tokens() == expected, "live_session_observed_" + expected);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int outcome = 0;
        try {
            ClassLoader loader = getTargetContext().getClassLoader();
            Class<?> portType = loader.loadClass("org.totipo.android.sync.SyncFolderBinding$Port");
            Class<?> portClass = loader.loadClass("org.totipo.android.sync.AndroidSyncFolderPort");
            Class<?> bindingClass = loader.loadClass("org.totipo.android.sync.SyncFolderBinding");
            Object port = portClass.getConstructor(android.content.Context.class).newInstance(getTargetContext());
            Object stored = call(port, "load");
            check(stored != null, "stored_binding_present");
            String uri = (String)call(stored, "uri");
            Object grants = call(port, "grants", new Class<?>[]{String.class}, uri);
            check((Boolean)call(grants, "read"), "persisted_read_grant");
            Object binding = bindingClass.getConstructor(portType).newInstance(port);
            check(call(call(binding, "restore"), "status").toString().equals("CHECKING"), "restored_requires_accessibility_check");
            Object original = call(getTargetContext().getApplicationContext(), "vaultController");
            long readyDeadline = SystemClock.uptimeMillis() + 10000;
            while (!call(call(call(original, "syncView"), "binding"), "status").toString().equals("READY")
                    && SystemClock.uptimeMillis() < readyDeadline) SystemClock.sleep(20);
            check(call(call(call(original, "syncView"), "binding"), "status").toString().equals("READY"), "application_startup_ready");
            Class<?> ownerClass = loader.loadClass("org.totipo.android.LocalReplicaOwner");
            directory = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "m3a-isolated-");
            Path root = directory.resolve("totipo-vault"); Files.createDirectories(root);
            byte[] canonicalVault;
            try (var input = getContext().getAssets().open("vault")) { canonicalVault = input.readAllBytes(); }
            Files.write(root.resolve("vault"), canonicalVault);
            var ownerConstructor = ownerClass.getDeclaredConstructor(Path.class); ownerConstructor.setAccessible(true);
            Object owner = ownerConstructor.newInstance(directory);
            Class<?> controllerClass = loader.loadClass("org.totipo.android.AndroidVaultController");
            Class<?> dispatcherClass = loader.loadClass("org.totipo.android.AndroidVaultController$Dispatcher");
            Class<?> backendClass = loader.loadClass("org.totipo.android.AndroidVaultController$Backend");
            Class<?> timeClass = loader.loadClass("org.totipo.android.TotpPresentation$Time");
            Class<?> clipboardClass = loader.loadClass("org.totipo.android.TotpPresentation$Clipboard");
            Handler main = new Handler(Looper.getMainLooper());
            Object dispatcher = Proxy.newProxyInstance(loader, new Class<?>[]{dispatcherClass}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "post": main.post((Runnable)args[0]); return null;
                    case "after": Runnable action = (Runnable)args[1]; main.postDelayed(action, (Long)args[0]); return (Runnable)() -> main.removeCallbacks(action);
                    case "assertDispatchThread": if (Looper.myLooper() != Looper.getMainLooper()) throw new AssertionError("Dispatch thread"); return null;
                    case "assertWorkerThread": if (Looper.myLooper() == Looper.getMainLooper()) throw new AssertionError("Worker thread"); return null;
                    default: return null;
                }
            });
            Object time = Proxy.newProxyInstance(loader, new Class<?>[]{timeClass}, (proxy, method, args) ->
                    method.getName().equals("wall") ? java.time.Instant.now() : SystemClock.elapsedRealtime());
            var backendConstructor = backendClass.getDeclaredConstructor(); backendConstructor.setAccessible(true);
            var constructor = controllerClass.getDeclaredConstructor(ownerClass, dispatcherClass, backendClass, timeClass, clipboardClass, bindingClass);
            constructor.setAccessible(true);
            isolated = constructor.newInstance(owner, dispatcher, backendConstructor.newInstance(), time, null, binding);
            waitIdle(); check(!(Boolean)call(isolated, "canImportProviderChanges"), "locked_import_disabled");
            command("unlock", new Class<?>[]{char[].class}, new char[0]); waitIdle();
            Object coordinator = field(isolated, "vault"), session = field(coordinator, "session"), store = field(coordinator, "store");
            check(tokens() == 0, "isolated_replica_initially_empty");
            command("importProviderChanges", new Class<?>[0]); waitIdle(); waitTokens(3);
            check(field(isolated, "vault") == coordinator && field(coordinator, "session") == session
                    && field(coordinator, "store") == store && field(isolated, "owner") == owner, "same_session_owner_and_store");
            check(Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))), "provider_vault_not_adopted");
            check(((String)call(call(isolated, "syncView"), "message")).contains("Changes imported"), "import_status_honest");
            Object firstToken = ((List<?>)call(call(call(isolated, "snapshot"), "view"), "tokens")).get(0);
            Object id = call(firstToken, "id");
            command("showCode", new Class<?>[]{loader.loadClass("org.totipo.TokenId")}, id); waitIdle();
            check(call(call(isolated, "snapshot"), "revealedCode") != null, "isolated_code_revealed");
            command("importProviderChanges", new Class<?>[0]); waitIdle();
            check(call(call(isolated, "snapshot"), "revealedCode") == null, "import_conceals_code");
            check(call(call(isolated, "syncView"), "message").equals("No new objects."), "exact_retry_no_new_objects");
            command("lock", new Class<?>[0]); waitIdle();
            check(field(isolated, "vault") == null, "isolated_session_closed_for_cleanup");
            result.putString("stream", "M3A PASS checks=" + checks + "\n"); outcome = -1;
        } catch (Throwable failure) {
            result.putString("stream", "M3A FAIL step=" + step + " checks=" + checks + " category=" + failure.getClass().getSimpleName() + "\n");
        } finally {
            try {
                if (isolated != null && field(isolated, "vault") != null) { command("lock", new Class<?>[0]); waitIdle(); }
                if (isolated != null) call(isolated, "shutdown");
                if (directory != null) try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            } catch (Exception ignored) { }
        }
        finish(outcome, result);
    }
}
