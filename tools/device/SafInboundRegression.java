package org.totipo.safqualification;

import android.app.Instrumentation;
import android.os.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Standalone instrumentation: existing user-picked grant, isolated vault, no provider writes. */
public final class SafInboundRegression extends Instrumentation {
    private Object fixtureOwner(ClassLoader loader, Path base) throws Exception {
        Class<?> registryType = loader.loadClass("org.totipo.android.VaultRegistry");
        Object registry = registryType.getConstructor(Path.class).newInstance(base.toRealPath());
        Object entry = call(registry, "reserve", new Class<?>[]{String.class}, new Object[]{null});
        Class<?> admissionType = loader.loadClass("org.totipo.android.LocalReplicaOwner$Admission");
        return admissionType.getMethod("select", registryType, String.class).invoke(
                admissionType.getConstructor().newInstance(), registry, call(entry, "id"));
    }
    private Path fixtureRoot(Object owner) throws Exception {
        Object lease = call(owner, "acquire");
        try { return (Path)call(lease, "root"); }
        finally { call(lease, "close"); }
    }

    private Object isolated;
    private Path directory;
    private Object openedCoordinator, openedSession, openedStore;
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
            synchronized (isolated) { if (!(Boolean)field(isolated, "operating") && !(Boolean)field(isolated, "viewQueued") && !(Boolean)field(isolated, "providerActive")
                    && !(Boolean)field(isolated, "syncPending") && !(Boolean)field(isolated, "syncDrainQueued")) return; }
            SystemClock.sleep(20);
        }
        throw new AssertionError("Worker timed out");
    }
    private void waitReveal() throws Exception {
        step = "isolated_reveal_completion";
        long end = SystemClock.uptimeMillis() + 30000;
        while (SystemClock.uptimeMillis() < end) {
            synchronized (isolated) {
                Object snapshot = call(isolated, "snapshot");
                if (field(isolated, "vault") != openedCoordinator
                        || field(openedCoordinator, "session") != openedSession
                        || !call(snapshot, "state").toString().equals("OPEN"))
                    throw new AssertionError("Reveal session retired");
                if (!(Boolean)field(isolated, "revealing")) {
                    if (call(snapshot, "revealedCode") == null)
                        throw new AssertionError("Reveal completed without a code (failed, retired or expired)");
                    return;
                }
            }
            SystemClock.sleep(20);
        }
        throw new AssertionError("Reveal generation timed out");
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
    private Map<String, String> inventory(String uri) throws Exception {
        var resolver = getTargetContext().getContentResolver();
        android.net.Uri tree = android.net.Uri.parse(uri);
        Map<String, String> result = new TreeMap<>();
        inventoryChildren(resolver, tree, android.provider.DocumentsContract.getTreeDocumentId(tree), "", result);
        return result;
    }
    private void inventoryChildren(android.content.ContentResolver resolver, android.net.Uri tree,
            String parent, String prefix, Map<String, String> result) throws Exception {
        try (var cursor = resolver.query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent),
                new String[]{"document_id", "_display_name", "mime_type"}, null, null, null)) {
            if (cursor == null) throw new AssertionError("Inventory unavailable");
            while (cursor.moveToNext()) {
                String id = cursor.getString(0), name = prefix + cursor.getString(1);
                if ("vnd.android.document/directory".equals(cursor.getString(2))) {
                    if (result.put(name + "/", "directory") != null) throw new AssertionError("Duplicate inventory");
                    inventoryChildren(resolver, tree, id, name + "/", result);
                } else {
                    byte[] bytes;
                    try (var input = resolver.openInputStream(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))) {
                        bytes = input.readNBytes(65537);
                    }
                    if (bytes.length > 65536) throw new AssertionError("Inventory bound");
                    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                    if (result.put(name, hash) != null) throw new AssertionError("Duplicate inventory");
                }
            }
        }
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
            check(uri.equals("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FTotipo-M3A-Test"),
                    "designated_disposable_provider_tree");
            check((Boolean)call(grants, "write"), "persisted_write_grant_for_automatic_sync");
            Map<String, String> before = inventory(uri);
            Map<String, String> expectedObjects = new TreeMap<>(before);
            expectedObjects.keySet().removeIf(name -> !name.matches("objects-v1/[0-9a-f]{64}"));
            check(expectedObjects.size() == 3 && before.keySet().stream().filter(n -> n.startsWith("objects-v1/")
                    && !n.equals("objects-v1/")).count() == 3, "exact_three_object_provider_fixture");
            Object binding = bindingClass.getConstructor(portType).newInstance(port);
            check(call(call(binding, "restore"), "status").toString().equals("CHECKING"), "restored_requires_accessibility_check");
            Object original = call(getTargetContext().getApplicationContext(), "vaultController");
            long readyDeadline = SystemClock.uptimeMillis() + 10000;
            while (!call(call(call(original, "syncView"), "binding"), "status").toString().equals("READY")
                    && SystemClock.uptimeMillis() < readyDeadline) SystemClock.sleep(20);
            check(call(call(call(original, "syncView"), "binding"), "status").toString().equals("READY"), "application_startup_ready");
            Class<?> ownerClass = loader.loadClass("org.totipo.android.LocalReplicaOwner");
            directory = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "m3a-isolated-");
            Object owner = fixtureOwner(loader, directory);
            Path root = fixtureRoot(owner);
            byte[] canonicalVault;
            try (var input = getContext().getAssets().open("vault")) { canonicalVault = input.readAllBytes(); }
            Files.write(root.resolve("vault"), canonicalVault);
            try (var entries = Files.list(root)) {
                check(entries.map(path -> path.getFileName().toString()).toList().equals(List.of("vault"))
                        && !Files.exists(root.resolve("objects-v1"))
                        && Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))),
                        "isolated_replica_empty_before_observation");
            }
            check(before.get("vault").equals(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(canonicalVault))), "matching_fixture_vault");

            Class<?> controllerClass = loader.loadClass("org.totipo.android.AndroidVaultController");
            Class<?> dispatcherClass = loader.loadClass("org.totipo.android.AndroidVaultController$Dispatcher");
            Class<?> backendClass = loader.loadClass("org.totipo.android.AndroidVaultController$Backend");
            Class<?> timeClass = loader.loadClass("org.totipo.android.TotpPresentation$Time");
            Class<?> clipboardClass = loader.loadClass("org.totipo.android.TotpPresentation$Clipboard");
            Handler main = new Handler(Looper.getMainLooper());
            Object dispatcher = Proxy.newProxyInstance(loader, new Class<?>[]{dispatcherClass}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "post":
                        // Capture ownership when opened() queues its first automatic drain,
                        // before dispatching it. Observe only; do not suppress or delay Sync.
                        if (isolated != null) synchronized (isolated) {
                            if (openedCoordinator == null && (Boolean)field(isolated, "syncPending")) {
                                openedCoordinator = field(isolated, "vault");
                                if (openedCoordinator != null) {
                                    openedSession = field(openedCoordinator, "session");
                                    openedStore = field(openedCoordinator, "store");
                                }
                            }
                        }
                        main.post((Runnable)args[0]); return null;
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
            check(call(call(isolated, "snapshot"), "state").toString().equals("OPEN"), "isolated_unlock_open");
            // RETIRED M3A requirement: remain empty after unlock until manual Import.
            // Current product first imports through automatic Sync; local emptiness is
            // independently established above, before controller/session observation.
            waitTokens(3); waitIdle();
            Object coordinator = openedCoordinator, session = openedSession, store = openedStore;
            check(coordinator != null && session != null && store != null, "automatic_sync_open_ownership_captured");
            check(field(isolated, "vault") == coordinator && field(coordinator, "session") == session
                    && field(coordinator, "store") == store && field(isolated, "owner") == owner, "same_session_owner_and_store");
            check(Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))), "provider_vault_not_adopted");
            check(call(call(coordinator, "sessionVaultId"), "hex").equals(java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(canonicalVault))), "matching_session_vault_identity");
            check(uri.equals(call(isolated, "initialTreeUri")) && uri.equals(call(call(port, "load"), "uri"))
                    && call(call(call(isolated, "syncView"), "binding"), "status").toString().equals("READY"),
                    "intended_binding_remains_ready");
            check("Verified against bound shared folder".equals(field(isolated, "lastSyncResult")), "automatic_open_sync_verified");
            Map<String, String> imported = new TreeMap<>();
            try (var files = Files.list(root.resolve("objects-v1"))) {
                for (Path path : files.toList()) imported.put("objects-v1/" + path.getFileName(), java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            }
            check(imported.equals(expectedObjects), "exact_matching_fixture_objects_imported");
            Set<String> accounts = new TreeSet<>();
            for (Object token : (List<?>)call(call(call(isolated, "snapshot"), "view"), "tokens")) {
                List<?> alternatives = (List<?>)call(token, "alternatives");
                check(!(Boolean)call(token, "conflict") && alternatives.size() == 1, "matching_fixture_single_alternative");
                Object descriptor = alternatives.get(0);
                check("M3A public fixture".equals(call(descriptor, "issuer")), "matching_fixture_issuer");
                accounts.add((String)call(descriptor, "account"));
            }
            check(tokens() == 3 && accounts.equals(Set.of("test 0", "test 1", "test 2")), "exact_matching_fixture_accounts");
            check(before.equals(inventory(uri)), "automatic_open_sync_zero_provider_mutation");
            Object firstToken = ((List<?>)call(call(call(isolated, "snapshot"), "view"), "tokens")).get(0);
            Object id = call(firstToken, "id");
            command("showCode", new Class<?>[]{loader.loadClass("org.totipo.TokenId")}, id); waitReveal();
            check(call(call(isolated, "snapshot"), "revealedCode") != null, "isolated_code_revealed");
            // Explicit Import now proves concealment and idempotent no-change retry only.
            command("importProviderChanges", new Class<?>[0]); waitIdle();
            check(call(call(isolated, "snapshot"), "revealedCode") == null, "import_conceals_code");
            check(call(call(isolated, "syncView"), "message").equals("No new objects."), "exact_retry_no_new_objects");
            check(field(isolated, "vault") == coordinator && field(coordinator, "session") == session
                    && field(coordinator, "store") == store && field(isolated, "owner") == owner, "retry_same_session_owner_and_store");
            check(before.equals(inventory(uri)), "explicit_import_retry_zero_provider_mutation");
            command("lock", new Class<?>[0]); waitIdle();
            check(field(isolated, "vault") == null, "isolated_session_closed_for_cleanup");
            call(isolated, "shutdown"); isolated = null;
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
            check(!Files.exists(directory), "isolated_temporary_vault_deleted");
            result.putString("stream", "M3A PASS checks=" + checks + "\n"); outcome = -1;
        } catch (Throwable failure) {
            result.putString("stream", "M3A FAIL step=" + step + " checks=" + checks + " category=" + failure.getClass().getSimpleName() + "\n");
        } finally {
            try {
                if (isolated != null && field(isolated, "vault") != null) { command("lock", new Class<?>[0]); waitIdle(); }
                if (isolated != null) call(isolated, "shutdown");
                if (directory != null && Files.exists(directory)) try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            } catch (Exception ignored) { }
        }
        finish(outcome, result);
    }
}
