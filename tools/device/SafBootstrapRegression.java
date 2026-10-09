package org.totipo.safqualification;

import android.app.Instrumentation;
import android.os.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Standalone instrumentation: existing user-picked grant, isolated vault, production outbound path; existing fixture tree only. */
public final class SafBootstrapRegression extends Instrumentation {
    private Object isolated;
    private Path directory;
    private Path writerDirectory;
    private Object reader;
    private String step = "setup";
    private int checks;
    private String mode;
    private boolean inspectOnly;
    private String blockedExpected;
    @Override public void onCreate(Bundle args) { super.onCreate(args); inspectOnly = args != null && "true".equals(args.getString("inspectOnly")); blockedExpected = args == null ? null : args.getString("blockedExpected"); mode = args == null ? "join" : args.getString("mode", "join"); start(); }
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
    private Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private Object field(Object target, String name) throws Exception {
        var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private void check(boolean condition, String name) {
        step = name; if (!condition) throw new AssertionError("Qualification assertion failed");
        checks++; Bundle result = new Bundle(); result.putString("stream", "M3C " + name + " PASS\n"); sendStatus(0, result);
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
            Bundle status = new Bundle(); status.putString("stream", "M3C inventory_" + phase + " " + name + " " + entry.getValue() + "\n");
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
        Bundle result = new Bundle(); int outcome = 0;
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
            waitIdle();
            if (mode.equals("join")) {
                check(call(call(isolated,"snapshot"),"state").toString().equals("NO_LOCAL_VAULT"),"empty_local");
                command("prepareJoin",new Class<?>[0]);waitIdle();
                command("joinExistingVault",new Class<?>[]{char[].class},"wrong public password".toCharArray()); waitIdle();
                check(!Files.exists(root.resolve("vault")),"wrong_password_local_vault_absent");
                check(!Files.exists(root.resolve("objects-v1")),"wrong_password_local_objects_unchanged");
                check(before.equals(inventory(uri)),"wrong_password_provider_unchanged");
                command("joinExistingVault",new Class<?>[]{char[].class},new char[0]); waitIdle();
                check(call(call(isolated,"snapshot"),"state").toString().equals("OPEN"),"joined_open");
                check(tokens()==0,"join_no_automatic_import");
                Object coordinator=field(isolated,"vault"),session=field(coordinator,"session");
                byte[] local=Files.readAllBytes(root.resolve("vault"));
                String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(local));
                check(hash.equals(before.get("vault")),"exact_local_provider_vault_hash");
                check(call(session,"vaultId").equals(loader.loadClass("org.totipo.Totipo").getMethod("vaultId",byte[].class).invoke(null,local)),"joined_vault_id");
                recordInventory("local",Map.of("vault",hash));
                command("importProviderChanges",new Class<?>[0]);waitIdle();waitTokens(3);
                check(field(coordinator,"session")==session,"join_import_same_session");
                check(before.equals(inventory(uri)),"join_import_provider_unchanged");
            } else {
                command("create",new Class<?>[]{char[].class},new char[0]);waitIdle();
                check(call(call(isolated,"snapshot"),"state").toString().equals("OPEN"),"local_created_open");
                byte[] local=Files.readAllBytes(root.resolve("vault"));
                String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(local));
                recordInventory("local",Map.of("vault",hash));
                Object session=field(field(isolated,"vault"),"session");
                Class<?> requestClass=loader.loadClass("org.totipo.android.AddTokenRequest");
                Class<?> algorithmClass=loader.loadClass("org.totipo.TotpAlgorithm");
                Object request=requestClass.getConstructor(String.class,String.class,algorithmClass,int.class,long.class,char[].class)
                    .newInstance("M3C public fixture","isolated",algorithmClass.getField("SHA1").get(null),6,30L,"AEAQCAI".toCharArray());
                command("addToken",new Class<?>[]{requestClass},request);waitIdle();waitTokens(1);
                command("initializeSyncFolder",new Class<?>[0]);waitIdle();
                if(mode.equals("initialize")) {
                    Map<String,String> initialized=inventory(uri);
                    check(hash.equals(initialized.get("vault")),"initialized_exact_provider_vault");
                    check(initialized.containsKey("objects-v1/"),"initialized_exact_directory");
                    check(initialized.size()==2,"initialize_no_automatic_publish");
                    command("initializeSyncFolder",new Class<?>[0]);waitIdle();
                    check(initialized.equals(inventory(uri)),"second_initialize_no_mutation");
                    command("publishLocalChanges",new Class<?>[0]);waitIdle();
                    Map<String,String> published=inventory(uri);
                    check(published.size()==3,"manual_publish_object");
                    check(hash.equals(published.get("vault")),"published_vault_unchanged");
                    command("publishLocalChanges",new Class<?>[0]);waitIdle();
                    check(published.equals(inventory(uri)),"second_publish_idempotent");
                    recordInventory("after",published);
                } else {
                    check(before.equals(inventory(uri)),"initialize_veto_zero_provider_mutation");
                    String message=(String)call(call(isolated,"syncView"),"message");
                    check(mode.equals("orphan")?message.contains("will not initialize"):message.equals("Sync folder belongs to a different Totipo vault."),"initialize_veto_truthful");
                    if(mode.equals("different")) for(String operation:List.of("importProviderChanges","publishLocalChanges")) {
                        command(operation,new Class<?>[0]);waitIdle();
                        check(before.equals(inventory(uri)),operation+"_zero_provider_mutation");
                        check(call(call(isolated,"syncView"),"message").equals("Sync folder belongs to a different Totipo vault."),operation+"_different_vault");
                    }
                }
                check(Arrays.equals(local,Files.readAllBytes(root.resolve("vault"))),"local_canonical_vault_unchanged");
                check(field(field(isolated,"vault"),"session")==session,"same_live_session");
            }
            command("lock",new Class<?>[0]);waitIdle();call(isolated,"shutdown");isolated=null;
            result.putString("stream","M3C "+mode+" PASS checks="+checks+"\n");outcome=-1;
        } catch(Throwable failure) {
            result.putString("stream","M3C FAIL step="+step+" checks="+checks+" category="+failure.getClass().getSimpleName()+"\n");
        } finally {
            try { if(isolated!=null) { command("lock",new Class<?>[0]);waitIdle();call(isolated,"shutdown"); } } catch(Throwable ignored) { }
            try { if(directory!=null) try(var paths=Files.walk(directory)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); } } catch(Exception ignored) { }
        }
        finish(outcome,result);
    }
}
