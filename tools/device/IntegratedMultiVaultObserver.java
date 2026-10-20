package org.totipo.android;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import org.totipo.android.sync.AndroidSyncFolderPort;

/** Read-only checkpoints in the disposable package. Humans use normal product UI.
 * No provider calls, credential reads, token writes, authentication or extra session.
 * Local protocol reads use the selected worker and coordinated store/owner lease.
 */
public final class IntegratedMultiVaultObserver extends Instrumentation {
    private static final String PACKAGE = "org.totipo.securityqualification";
    private static final String ACTION = PACKAGE + ".AMV6_CAPTURE";
    private final BlockingQueue<String> requests = new ArrayBlockingQueue<>(1);
    private final Set<String> labels = new HashSet<>();
    private final IdentityHashMap<Object, String> sessions = new IdentityHashMap<>();
    private TotipoApplication app;
    private boolean failed;

    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Object field(Object value, String name) throws Exception {
        var field = value.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(value);
    }
    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private void emit(JSONObject value) {
        var status = new Bundle(); status.putString("stream", "AMV6_CAPTURE " + value + "\n"); sendStatus(0, status);
    }
    private JSONObject selected(VaultSelection.Snapshot origin) throws Exception {
        var c = origin.controller();
        FutureTask<JSONObject> task = new FutureTask<>(() -> {
            synchronized (c) {
                for (String name : List.of("operating", "viewQueued", "providerActive", "syncPending", "syncDrainQueued"))
                    if ((Boolean)field(c, name)) throw new IllegalStateException();
            }
            var snapshot = c.snapshot();
            var registry = (VaultRegistry)field(app, "registry");
            var result = new JSONObject().put("local_id", c.localId()).put("activation", c.activationId())
                    .put("state", snapshot.state().toString()).put("error", snapshot.error().toString())
                    .put("retirement", c.retirement().toString())
                    .put("canonical_root", registry.root(c.localId()).toString());
            var port = new AndroidSyncFolderPort(getTargetContext(), c.localId());
            var binding = port.load();
            result.put("binding_configured", binding != null).put("pending_publication_hint", port.pendingPublication());
            if (binding != null) {
                var uri = android.net.Uri.parse(binding.uri());
                String tree = android.provider.DocumentsContract.getTreeDocumentId(uri);
                if (!"com.android.externalstorage.documents".equals(uri.getAuthority())
                        || !Set.of("primary:totipo-test-sync/Totipo-AMV6-20261020-A",
                                   "primary:totipo-test-sync/Totipo-AMV6-20261020-C",
                                   "primary:totipo-test-sync/Totipo-AMV6-20261020-D").contains(tree))
                    throw new IllegalStateException();
                result.put("provider_tree", tree);
            }
            result.put("sync_message", c.syncView().message());
            result.put("folder_configuration_notice", c.folderConfigurationNotice());
            var view = snapshot.view();
            if (view != null) {
                var tokens = new JSONArray();
                for (var token : view.tokens()) {
                    var alternatives = new JSONArray();
                    for (var descriptor : token.alternatives()) {
                        if (!descriptor.issuer().startsWith("Totipo AMV6 ")
                                || !descriptor.account().matches("[A-Za-z0-9._+-]+@example\\.test"))
                            throw new IllegalStateException();
                        alternatives.put(new JSONObject().put("issuer", descriptor.issuer())
                                .put("account", descriptor.account()).put("status", descriptor.status().toString()));
                    }
                    tokens.put(new JSONObject().put("token_id", token.id().toString())
                            .put("conflict", token.conflict()).put("heads", token.heads().size())
                            .put("unresolved", token.unresolved().size()).put("alternatives", alternatives));
                }
                result.put("tokens", tokens).put("diagnostics", view.diagnostics().size())
                        .put("observation", view.observation().getClass().getSimpleName());
            }
            if (snapshot.state() == AndroidVaultController.State.OPEN) {
                var coordinator = (ForegroundVaultCoordinator)field(c, "vault");
                Object session = field(coordinator, "session");
                byte[] vault = coordinator.snapshotVault();
                result.put("session", sessions.computeIfAbsent(session, ignored -> UUID.randomUUID().toString()))
                        .put("vault_sha256", hash(vault)).put("vault_size", vault.length)
                        .put("vault_id", coordinator.sessionVaultId().hex());
                var objects = new JSONArray();
                for (var object : coordinator.outboundSnapshot()) objects.put(new JSONObject()
                        .put("name", object.id().hex()).put("size", object.representation().length)
                        .put("sha256", hash(object.representation())));
                result.put("objects", objects);
            } else if (snapshot.state() == AndroidVaultController.State.LOCKED
                    || snapshot.state() == AndroidVaultController.State.NO_LOCAL_VAULT) {
                var owner = (LocalReplicaOwner)field(c, "owner");
                try (var lease = owner.acquire()) {
                    Path vault = lease.root().resolve("vault");
                    boolean present = Files.exists(vault, LinkOption.NOFOLLOW_LINKS);
                    result.put("vault_present", present);
                    if (present) try (var input = Files.newInputStream(vault, LinkOption.NOFOLLOW_LINKS)) {
                        byte[] bytes = input.readNBytes(88);
                        if (bytes.length != 87) throw new IllegalStateException();
                        result.put("vault_sha256", hash(bytes)).put("vault_size", bytes.length);
                    }
                }
            }
            if (app.selection().snapshot() != origin) throw new IllegalStateException();
            return result;
        });
        ((Executor)field(c, "worker")).execute(task);
        return task.get(30, TimeUnit.SECONDS);
    }
    @Override public void onStart() {
        BroadcastReceiver receiver = null;
        try {
            if (!PACKAGE.equals(getTargetContext().getPackageName())) throw new IllegalStateException();
            runOnMainSync(() -> app = (TotipoApplication)getTargetContext().getApplicationContext());
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    String label = intent.getStringExtra("label");
                    if (label != null && label.matches("[A-Za-z0-9_-]{1,80}")) requests.offer(label);
                }
            };
            getTargetContext().registerReceiver(receiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
            startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            emit(new JSONObject().put("ready", true).put("pid", android.os.Process.myPid()));
            while (true) {
                String label = requests.take();
                if (label.equals("STOP")) break;
                if (failed) { emit(new JSONObject().put("checkpoint", label).put("failure", "failure_latched")); continue; }
                try {
                    if (!labels.add(label)) throw new IllegalStateException();
                    var origin = app.selection().snapshot();
                    var entries = new JSONArray();
                    for (var entry : app.localEntries()) entries.put(new JSONObject().put("local_id", entry.id())
                            .put("state", entry.state().toString()).put("creation_attempted", entry.creationAttempted()));
                    var evidence = new JSONObject().put("checkpoint", label).put("pid", android.os.Process.myPid())
                            .put("registry", entries).put("selected", origin == null ? JSONObject.NULL : selected(origin));
                    if (app.selection().snapshot() != origin) throw new IllegalStateException();
                    emit(evidence); // Capture only; scenario PASS requires independent transport/UI evidence.
                } catch (Throwable failure) {
                    failed = true; // Preserve failure; never continue a partially captured scenario as passing.
                    emit(new JSONObject().put("checkpoint", label).put("failure", "capture_refused"));
                }
            }
            var status = new Bundle(); status.putString("stream", failed ? "AMV6_OBSERVER_FAILED\n" : "AMV6_OBSERVER_STOPPED\n");
            finish(failed ? 0 : -1, status);
        } catch (Throwable failure) {
            var status = new Bundle(); status.putString("stream", "AMV6_OBSERVER_SETUP_FAILED\n"); finish(0, status);
        } finally { if (receiver != null) getTargetContext().unregisterReceiver(receiver); }
    }
}
