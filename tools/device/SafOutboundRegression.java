package org.totipo.safqualification;

import android.app.Instrumentation;
import android.os.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Standalone instrumentation: existing user-picked grant, isolated vault, production outbound path; existing fixture tree only. */
public final class SafOutboundRegression extends Instrumentation {
    private Object isolated;
    private Path directory;
    private Path writerDirectory;
    private Object reader;
    private String step = "setup";
    private int checks;
    private boolean inspectOnly;
    private String blockedExpected;
    @Override public void onCreate(Bundle args) { super.onCreate(args); inspectOnly = args != null && "true".equals(args.getString("inspectOnly")); blockedExpected = args == null ? null : args.getString("blockedExpected"); start(); }
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
    private Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void check(boolean condition, String name) {
        step = name; if (!condition) throw new AssertionError("Qualification assertion failed");
        checks++; Bundle result = new Bundle(); result.putString("stream", "M3B " + name + " PASS\n"); sendStatus(0, result);
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
    private void recordInventory(String phase, Map<String, String> values) {
        for (var entry : values.entrySet()) {
            String name = entry.getKey();
            if (!name.equals("vault") && !name.equals("objects-v1/") && !name.matches("objects-v1/[0-9a-f]{64}")) name = "other-fixture-entry";
            Bundle status = new Bundle(); status.putString("stream", "M3B inventory_" + phase + " " + name + " " + entry.getValue() + "\n");
            sendStatus(0, status);
        }
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
            check((Boolean)call(grants, "write"), "persisted_write_grant");
            if (inspectOnly) {
                result.putString("stream", "M3B persisted READ + WRITE inspection PASS\n"); finish(-1, result); return;
            }
            android.net.Uri selectedTree = android.net.Uri.parse(uri);
            try (var cursor = getTargetContext().getContentResolver().query(
                    android.provider.DocumentsContract.buildDocumentUriUsingTree(selectedTree,
                        android.provider.DocumentsContract.getTreeDocumentId(selectedTree)),
                    new String[]{"_display_name"}, null, null, null)) {
                check(cursor != null && cursor.moveToFirst() && "Totipo-M3A-Test".equals(cursor.getString(0)), "designated_disposable_provider_tree");
            }
            Map<String, String> before = inventory(uri);
            check((blockedExpected != null || before.containsKey("vault")) && before.containsKey("objects-v1/")
                    && before.keySet().stream().filter(n -> n.matches("objects-v1/[0-9a-f]{64}")).count() >= 3,
                    "existing_fixture_baseline_required");
            recordInventory("before", before);
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
            LocalStoreQualification.run(directory);
            check(true, "r19_local_coordinated_store_and_orphan_veto");
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
            if (blockedExpected != null) {
                Class<?> requestClass = loader.loadClass("org.totipo.android.AddTokenRequest");
                Class<?> algorithmClass = loader.loadClass("org.totipo.TotpAlgorithm");
                Object request = requestClass.getConstructor(String.class, String.class, algorithmClass, int.class, long.class, char[].class)
                        .newInstance("r19 blocked public fixture", "isolated", algorithmClass.getField("SHA1").get(null), 6, 30L, "AEAQCAI".toCharArray());
                command("addToken", new Class<?>[]{requestClass}, request); waitIdle(); waitTokens(1);
                Map<String, byte[]> localBefore = new TreeMap<>();
                try (var paths = Files.list(root.resolve("objects-v1"))) {
                    for (Path path : paths.toList()) localBefore.put(path.getFileName().toString(), Files.readAllBytes(path));
                }
                for (String operation : List.of("importProviderChanges", "publishLocalChanges")) {
                    command(operation, new Class<?>[0]); waitIdle();
                    check(blockedExpected.equals(call(call(isolated, "syncView"), "message")), "r19_" + operation + "_blocked_truthfully");
                    check(tokens() == 1, "no_local_object_import");
                    try (var paths = Files.list(root.resolve("objects-v1"))) {
                        var all = paths.toList(); check(all.size() == localBefore.size(), "no_extra_local_object");
                        for (Path path : all) check(Arrays.equals(localBefore.get(path.getFileName().toString()), Files.readAllBytes(path)), "local_objects_unchanged");
                    }
                    check(before.equals(inventory(uri)), "zero_provider_mutation");
                    check(Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))), "local_canonical_vault_unchanged");
                    check(field(coordinator, "session") == session, "blocked_same_live_session");
                }
                result.putString("stream", "R19 BLOCKED PASS checks=" + checks + "\n"); finish(-1, result); return;
            }
            check(tokens() == 0, "isolated_replica_initially_empty");
            command("importProviderChanges", new Class<?>[0]); waitIdle(); waitTokens((int)before.keySet().stream().filter(n -> n.matches("objects-v1/[0-9a-f]{64}")).count());
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
            int baselineTokens = tokens();
            reader = isolated;
            Object readerSession = session;
            writerDirectory = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "m3b-writer-");
            Path writerRoot = writerDirectory.resolve("totipo-vault"); Files.createDirectories(writerRoot.resolve("objects-v1"));
            Files.write(writerRoot.resolve("vault"), canonicalVault);
            try (var files = Files.list(root.resolve("objects-v1"))) {
                for (Path path : files.toList()) Files.copy(path, writerRoot.resolve("objects-v1").resolve(path.getFileName()));
            }
            Object writerOwner = ownerConstructor.newInstance(writerDirectory);
            Object writerBinding = bindingClass.getConstructor(portType).newInstance(port);
            isolated = constructor.newInstance(writerOwner, dispatcher, backendConstructor.newInstance(), time, null, writerBinding);
            waitIdle(); command("unlock", new Class<?>[]{char[].class}, new char[0]); waitIdle(); waitTokens(baselineTokens);
            Object writerSession = field(field(isolated, "vault"), "session");
            Class<?> requestClass = loader.loadClass("org.totipo.android.AddTokenRequest");
            Class<?> algorithmClass = loader.loadClass("org.totipo.TotpAlgorithm");
            Object sha1 = algorithmClass.getField("SHA1").get(null);
            Object request = requestClass.getConstructor(String.class, String.class, algorithmClass, int.class, long.class, char[].class)
                .newInstance("M3B public fixture", "isolated writer", sha1, 6, 30L, "AEAQCAI".toCharArray());
            command("addToken", new Class<?>[]{requestClass}, request); waitIdle(); waitTokens(baselineTokens + 1);
            List<Path> expected;
            try (var files = Files.list(writerRoot.resolve("objects-v1"))) {
                expected = files.filter(path -> !before.containsKey("objects-v1/" + path.getFileName())).toList();
            }
            check(!expected.isEmpty(), "isolated_authorship_created_missing_objects");
            command("publishLocalChanges", new Class<?>[0]); waitIdle();
            check(call(call(isolated, "syncView"), "message").equals("Local changes published"), "production_postflight_verified_publication");
            check(field(field(isolated, "vault"), "session") == writerSession, "writer_session_retained");
            Map<String, String> after = inventory(uri);
            recordInventory("after", after);
            for (var entry : before.entrySet()) check(entry.getValue().equals(after.get(entry.getKey())), "existing_provider_entry_unchanged");
            check(after.size() == before.size() + expected.size(), "only_expected_canonical_files_added");
            for (Path path : expected) {
                String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
                check(hash.equals(after.get("objects-v1/" + path.getFileName())), "provider_exact_local_representation");
            }
            check(after.keySet().stream().allMatch(n -> before.containsKey(n) || n.matches("objects-v1/[0-9a-f]{64}")), "no_suffix_temp_or_rename_artifacts");
            command("publishLocalChanges", new Class<?>[0]); waitIdle();
            check(call(call(isolated, "syncView"), "message").equals("No local changes to publish"), "exact_publish_retry_no_changes");
            check(after.equals(inventory(uri)), "retry_inventory_and_hashes_unchanged");
            Object writer = isolated; isolated = reader;
            check(tokens() == baselineTokens, "reader_unchanged_before_import");
            command("importProviderChanges", new Class<?>[0]); waitIdle(); waitTokens(baselineTokens + 1);
            check(field(field(isolated, "vault"), "session") == readerSession, "same_live_reader_observed_writer_token");
            check(Arrays.equals(canonicalVault, Files.readAllBytes(root.resolve("vault"))), "reader_canonicalVault_unchanged");
            command("lock", new Class<?>[0]); waitIdle(); call(isolated, "shutdown"); reader = null;
            isolated = writer; command("lock", new Class<?>[0]); waitIdle();
            check(field(isolated, "vault") == null, "isolated_session_closed_for_cleanup");
            result.putString("stream", "R19 MATCH PASS checks=" + checks + "\n"); outcome = -1;
        } catch (Throwable failure) {
            result.putString("stream", "M3B FAIL step=" + step + " checks=" + checks + " category=" + failure.getClass().getSimpleName() + "\n");
        } finally {
            try {
                if (isolated != null && field(isolated, "vault") != null) { command("lock", new Class<?>[0]); waitIdle(); }
                if (isolated != null) call(isolated, "shutdown");
                if (reader != null) { Object writer = isolated; isolated = reader; command("lock", new Class<?>[0]); waitIdle(); call(isolated, "shutdown"); isolated = writer; }
                if (writerDirectory != null) try (var paths = Files.walk(writerDirectory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
                if (directory != null) try (var paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            } catch (Exception ignored) { }
        }
        finish(outcome, result);
    }
}
