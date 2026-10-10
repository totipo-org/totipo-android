package org.totipo.syncthingqualification;

import android.app.Instrumentation;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Observation only. Separate same-signer APK; never copies, authors or repairs protocol files.
 * Keep instrumentation alive across human UI actions to preserve actual session identity.
 * Reflection follows the existing device qualification pattern, outside production source sets.
 */
public final class SyncthingE2eRegression extends Instrumentation {
    private String folder;
    private final BlockingQueue<String> requests = new ArrayBlockingQueue<>(1);
    private final IdentityHashMap<Object, String> sessions = new IdentityHashMap<>();
    private String boundUri;
    private Object controller;
    private int entries;
    private byte[] providerVault;
    private BroadcastReceiver receiver;
    private static final String ACTION = "org.totipo.syncthingqualification.CAPTURE";
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        folder = args == null ? "" : args.getString("folder", "");
        if (!Set.of("Totipo-M3D-Test", "Totipo-M3D-Android-First").contains(folder)) {
            finish(0, new Bundle()); return;
        }
        start();
    }
    private Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return target.getClass().getMethod(name, types).invoke(target, args);
    }
    private Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private JSONArray sorted(JSONArray array) throws Exception {
        List<JSONObject> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) values.add(array.getJSONObject(i));
        values.sort(Comparator.comparing(JSONObject::toString));
        return new JSONArray(values);
    }
    private void emit(JSONObject value) {
        Bundle status = new Bundle(); status.putString("stream", "M3D_JSON " + value + "\n"); sendStatus(0, status);
    }
    // Fixture filenames only; reject path/control ambiguity rather than leaking provider identifiers.
    private String name(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._ -]{1,160}")) throw new IllegalStateException("unsafe_name");
        return value;
    }
    private void children(Uri tree, String parent, String prefix, List<JSONObject> rows, Set<String> seen, int depth) throws Exception {
        if (depth > 8 || !seen.add(parent)) throw new IllegalStateException("inventory_cycle_or_depth");
        try (var cursor = getTargetContext().getContentResolver().query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent),
                new String[]{"document_id", "_display_name", "mime_type"}, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("inventory_unavailable");
            if (cursor.getExtras() != null && (cursor.getExtras().getBoolean(DocumentsContract.EXTRA_LOADING, false)
                    || cursor.getExtras().containsKey(DocumentsContract.EXTRA_ERROR))) throw new IllegalStateException("inventory_loading");
            while (cursor.moveToNext()) {
                if (++entries > 2048) throw new IllegalStateException("inventory_capacity");
                String id = cursor.getString(0), logical = prefix + name(cursor.getString(1));
                boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2));
                JSONObject row = new JSONObject().put("name", logical).put("kind", directory ? "directory" : "file");
                rows.add(row);
                if (directory) {
                    row.put("status", "READABLE"); children(tree, id, logical + "/", rows, seen, depth + 1);
                } else {
                    try (var input = getTargetContext().getContentResolver().openInputStream(
                            DocumentsContract.buildDocumentUriUsingTree(tree, id))) {
                        if (input == null) throw new IllegalStateException();
                        java.io.ByteArrayOutputStream canonical = logical.equals("vault") ? new java.io.ByteArrayOutputStream(88) : null;
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        byte[] buffer = new byte[8192]; long size = 0; int count;
                        while ((count = input.read(buffer)) != -1) {
                            size += count;
                            if (size > 16 * 1024 * 1024) throw new IllegalStateException();
                            digest.update(buffer, 0, count);
                            if (canonical != null && size <= 88) canonical.write(buffer, 0, count);
                        }
                        if (canonical != null && size == 87) providerVault = canonical.toByteArray();
                        row.put("size", size).put("sha256", HexFormat.of().formatHex(digest.digest())).put("status", "READABLE");
                    } catch (Exception unavailable) { row.put("status", "UNVERIFIABLE"); }
                }
            }
        }
    }
    private JSONObject provider() throws Exception {
        ClassLoader loader = getTargetContext().getClassLoader();
        Object port = loader.loadClass("org.totipo.android.sync.AndroidSyncFolderPort")
                .getConstructor(Context.class).newInstance(getTargetContext());
        Object stored = call(port, "load");
        if (stored == null) throw new IllegalStateException("no_binding");
        String uri = (String)call(stored, "uri");
        if (boundUri != null && !boundUri.equals(uri)) throw new IllegalStateException("binding_changed");
        Uri tree = Uri.parse(uri);
        try (var cursor = getTargetContext().getContentResolver().query(
                DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
                new String[]{"_display_name"}, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst() || !folder.equals(cursor.getString(0)))
                throw new IllegalStateException("fixture_identity_unconfirmed");
        }
        boundUri = uri;
        Object grant = call(port, "grants", new Class<?>[]{String.class}, uri);
        JSONObject result = new JSONObject().put("fixture", folder)
                .put("persisted_read", call(grant, "read")).put("persisted_write", call(grant, "write"));
        if (!(Boolean)call(grant, "read")) throw new IllegalStateException("no_read_grant");
        List<JSONObject> rows = new ArrayList<>(); entries = 0; providerVault = null;
        children(tree, DocumentsContract.getTreeDocumentId(tree), "", rows, new HashSet<>(), 0);
        rows.sort(Comparator.comparing(row -> row.optString("name")));
        Set<String> names = new HashSet<>();
        for (JSONObject row : rows) if (!names.add(row.getString("name"))) throw new IllegalStateException("duplicate_name");
        return result.put("inventory", new JSONArray(rows)).put("coverage", "COMPLETE");
    }
    private JSONObject local() throws Exception {
        // Serialize behind the controller worker. Never access a live store by a second NIO handle.
        FutureTask<JSONObject> task = new FutureTask<>(() -> {
            JSONObject result = new JSONObject();
            Object snapshot = call(controller, "snapshot"), sync = call(controller, "syncView");
            result.put("state", call(snapshot, "state").toString()).put("error", call(snapshot, "error").toString());
            result.put("binding", call(call(sync, "binding"), "status").toString());
            result.put("sync_message", call(sync, "message")); // fixed production status, no credential fields
            Object coordinator = field(controller, "vault");
            Object view = call(snapshot, "view");
            if (view != null) {
                JSONArray tokens = new JSONArray();
                for (Object token : (List<?>)call(view, "tokens")) {
                    JSONObject item = new JSONObject().put("conflict", call(token, "conflict"))
                            .put("unresolved", ((List<?>)call(token, "unresolved")).size())
                            .put("heads", ((List<?>)call(token, "heads")).size());
                    JSONArray alternatives = new JSONArray();
                    for (Object descriptor : (List<?>)call(token, "alternatives")) {
                        String issuer = (String)call(descriptor, "issuer"), account = (String)call(descriptor, "account");
                        // Only explicitly public M3D fixture metadata may enter durable evidence.
                        // Explicitly human-identified public fixture typo; keep other metadata refused.
                        if (!issuer.startsWith("Totipo M3D") || !(account.matches("[a-zA-Z0-9._+-]+@example\\.test")
                                || account.equals("android@example.tes")))
                            throw new IllegalStateException("non_fixture_token");
                        alternatives.put(new JSONObject().put("issuer", issuer).put("account", account));
                    }
                    item.put("alternatives", sorted(alternatives)); tokens.put(item);
                }
                result.put("tokens", sorted(tokens)).put("token_count", tokens.length())
                        .put("diagnostic_count", ((List<?>)call(view, "diagnostics")).size())
                        .put("observation", call(view, "observation").getClass().getSimpleName());
            }
            if (coordinator != null && "OPEN".equals(result.getString("state"))) {
                Object session = field(coordinator, "session");
                result.put("session", sessions.computeIfAbsent(session, ignored -> UUID.randomUUID().toString()));
                byte[] vault = (byte[])call(coordinator, "snapshotVault");
                result.put("vault_sha256", hash(vault)).put("vault_size", vault.length)
                        .put("session_local_vault_id_equal", call(coordinator, "matchesVault", new Class<?>[]{byte[].class}, vault));
                result.put("provider_local_vault_bytes_equal", providerVault != null && Arrays.equals(vault, providerVault));
                if (providerVault != null) result.put("session_provider_vault_id_equal", call(coordinator, "matchesVault", new Class<?>[]{byte[].class}, providerVault));
                JSONArray objects = new JSONArray();
                for (Object object : (List<?>)call(coordinator, "outboundSnapshot")) {
                    byte[] bytes = (byte[])call(object, "representation");
                    objects.put(new JSONObject().put("name", call(call(object, "id"), "hex"))
                            .put("size", bytes.length).put("sha256", hash(bytes)));
                }
                result.put("objects", sorted(objects));
            } else {
                // Exclusive owner lease while no session is owned. No writes, no retained root.
                Object owner = field(controller, "owner");
                try (AutoCloseable lease = (AutoCloseable)call(owner, "acquire")) {
                    Path root = (Path)call(lease, "root");
                    Path vault = root.resolve("vault");
                    result.put("vault_present", Files.exists(vault));
                    if (Files.exists(vault)) {
                        try (var input = Files.newInputStream(vault)) {
                            byte[] bytes = input.readNBytes(88);
                            if (bytes.length != 87) throw new IllegalStateException("local_vault_size");
                            result.put("vault_sha256", hash(bytes)).put("vault_size", bytes.length);
                        }
                    }
                    int count = 0;
                    if (Files.isDirectory(root.resolve("objects-v1"))) try (var files = Files.list(root.resolve("objects-v1"))) { count = (int)files.count(); }
                    result.put("local_object_entries", count);
                }
            }
            result.put("refresh_requested", "NOT_EXPOSED_BY_CONTROLLER");
            return result;
        });
        ((Executor)field(controller, "worker")).execute(task);
        return task.get(30, TimeUnit.SECONDS);
    }
    @Override public void onStart() {
        try {
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    String label = intent.getStringExtra("label");
                    if (label != null && label.matches("[A-Za-z0-9_-]{1,80}")) requests.offer(label);
                }
            };
            getTargetContext().registerReceiver(receiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
            startActivitySync(new Intent().setClassName("org.totipo.android", "org.totipo.android.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            controller = call(getTargetContext().getApplicationContext(), "vaultController");
            if (controller == null) throw new IllegalStateException("controller_not_ready");
            emit(new JSONObject().put("ready", true).put("pid", android.os.Process.myPid()));
            while (true) {
                String label = requests.take();
                if (label.equals("STOP")) break;
                JSONObject evidence = new JSONObject().put("checkpoint", label).put("pid", android.os.Process.myPid());
                String stage = "provider_before";
                try {
                    JSONObject before = provider();
                    stage = "local";
                    JSONObject state = local();
                    stage = "provider_after";
                    JSONObject after = provider();
                    evidence.put("provider", after).put("local", state)
                            .put("provider_stable_during_capture", before.toString().equals(after.toString()));
                    emit(evidence);
                } catch (Throwable failure) {
                    // Do not emit exception text, paths, URI, IDs, or partial plaintext projections.
                    JSONArray frames = new JSONArray();
                    for (StackTraceElement frame : failure.getStackTrace()) {
                        if (frames.length() == 5) break;
                        frames.put(frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
                    }
                    emit(new JSONObject().put("checkpoint", label).put("failure", failure.getClass().getSimpleName())
                            .put("stage", stage).put("frames", frames));
                }
            }
            getTargetContext().unregisterReceiver(receiver);
            finish(-1, new Bundle());
        } catch (Throwable failure) {
            Bundle error = new Bundle(); error.putString("stream", "M3D observer setup failed\n"); finish(0, error);
        }
    }
}
