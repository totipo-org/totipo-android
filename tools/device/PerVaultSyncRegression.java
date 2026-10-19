package org.totipo.android;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import org.totipo.*;
import org.totipo.android.sync.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;

/** Fresh disposable package only. Actual Application selection, registry, Java and
 * scoped Android SAF ports. The held call is controlled; real SAF is exercised
 * separately and no Syncthing/peer delivery is claimed. */
public final class PerVaultSyncRegression extends Instrumentation {
    private Bundle args;
    private int checks;
    private String step = "startup";
    private TotipoApplication app;
    private VaultRegistry registry;
    private AndroidVaultController controller;
    private MainActivity activity;
    private final CountDownLatch release = new CountDownLatch(1);
    private Object field(Object value, String name) {
        try { var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void check(boolean value, String label) {
        step = label; if (!value) throw new AssertionError(label); checks++;
        Bundle status = new Bundle(); status.putString("stream", "MV3 " + label + " PASS\n"); sendStatus(0, status);
    }
    private void await(BooleanSupplier condition) {
        long end = SystemClock.uptimeMillis() + 120_000;
        while (!condition.getAsBoolean()) {
            if (SystemClock.uptimeMillis() >= end) throw new AssertionError("timeout at " + step);
            SystemClock.sleep(10);
        }
    }
    private boolean idle() {
        synchronized (controller) {
            return !(Boolean)field(controller, "operating") && !(Boolean)field(controller, "viewQueued")
                    && !(Boolean)field(controller, "providerActive") && !(Boolean)field(controller, "syncPending")
                    && !(Boolean)field(controller, "syncDrainQueued");
        }
    }
    private void command(BooleanSupplier action) {
        AtomicBoolean accepted = new AtomicBoolean();
        await(() -> { runOnMainSync(() -> { if (idle()) accepted.set(action.getAsBoolean()); }); return accepted.get(); });
        await(this::idle);
    }
    private void retire() {
        runOnMainSync(() -> app.selection().retire()); await(() -> app.selection().snapshot() == null);
    }
    private void select(String id) {
        runOnMainSync(() -> {
            try { controller = app.selectLocalEntry(id).controller(); }
            catch (Exception failure) { throw new AssertionError(failure); }
        });
        await(this::idle);
    }
    private ForegroundVaultCoordinator vault() { return (ForegroundVaultCoordinator)field(controller, "vault"); }
    private VaultSession session() { return (VaultSession)field(vault(), "session"); }
    private AndroidSyncFolderPort port(String id) { return new AndroidSyncFolderPort(getTargetContext(), id); }
    private boolean granted(String uri) { return port(controller.localId()).grants(uri).read(); }
    private ForegroundVaultCoordinator.ObservedToken row() { return controller.snapshot().view().tokens().get(0); }
    private void add(String account) {
        command(() -> controller.addToken(new AddTokenRequest("Public MV3 fixture", account, TotpAlgorithm.SHA1, 6, 30, "MY".toCharArray())));
        await(() -> controller.snapshot().view() != null && !controller.snapshot().view().tokens().isEmpty());
    }
    private void change(TokenChange.Kind kind, String account) {
        var token = row();
        AtomicReference<TokenChange> change = new AtomicReference<>();
        command(() -> { change.set(controller.beginTokenChange(token.id(), kind)); return change.get() != null; });
        command(() -> controller.confirmTokenChange(change.get(), "Public MV3 fixture", account, 0));
        check(controller.tokenChangeResult() == TokenChange.Result.SAVED, "local_" + kind + "_saved");
    }
    private void open(String password) {
        command(() -> controller.unlock(password.toCharArray()));
        check(controller.snapshot().state() == AndroidVaultController.State.OPEN, "password_open");
        await(() -> controller.snapshot().view() != null);
    }
    private boolean click(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if ((node.getText() != null && text.equalsIgnoreCase(node.getText().toString()))
                || (node.getContentDescription() != null && text.equalsIgnoreCase(node.getContentDescription().toString()))) {
            AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) target = target.getParent();
            return target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        for (int i = 0; i < node.getChildCount(); i++) if (click(node.getChild(i), text)) return true;
        return false;
    }
    private boolean containsText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        if (node.getText() != null && node.getText().toString().contains(text)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (containsText(node.getChild(i), text)) return true;
        return false;
    }
    private boolean folderHeader(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        String id = node.getViewIdResourceName();
        if (id != null && (id.endsWith(":id/toolbar") || id.endsWith(":id/action_bar_title")
                || id.endsWith(":id/breadcrumb")) && containsText(node, text)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (folderHeader(node.getChild(i), text)) return true;
        return false;
    }
    /** The platform picker itself supplies the grant; no shell/app permission bypass. */
    public static final class GrantActivity extends Activity {
        static volatile Uri selected;
        static volatile int flags;
        static volatile boolean returned;
        @Override public void onCreate(Bundle state) {
            super.onCreate(state);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(getIntent().getStringExtra("initial")));
            startActivityForResult(picker, 1);
        }
        @Override public void onActivityResult(int request, int result, Intent data) {
            super.onActivityResult(request, result, data);
            selected = result == RESULT_OK && data != null ? data.getData() : null;
            flags = data == null ? 0 : data.getFlags(); returned = true; finish();
        }
    }
    private void bind(String uri) {
        GrantActivity.selected = null; GrantActivity.returned = false;
        startActivitySync(new Intent(getTargetContext(), GrantActivity.class).putExtra("initial",
                DocumentsContract.buildDocumentUriUsingTree(Uri.parse(uri), DocumentsContract.getTreeDocumentId(Uri.parse(uri))).toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        step = "platform folder picker";
        String document = DocumentsContract.getTreeDocumentId(Uri.parse(uri));
        String uniqueLeaf = document.substring(document.lastIndexOf('/') + 1);
        var chosen = new AtomicBoolean();
        await(() -> {
            if (GrantActivity.returned) return true;
            var root = getUiAutomation().getRootInActiveWindow();
            // Never approve a generic/default picker location. The runner gives
            // every fresh fixture leaf a unique ID, visible in the picker header.
            if (!chosen.get() && folderHeader(root, uniqueLeaf) && click(root, "Use this folder")) chosen.set(true);
            else if (chosen.get() && containsText(root, uniqueLeaf)) click(root, "Allow");
            return false;
        });
        check(Uri.parse(uri).equals(GrantActivity.selected), "picker_exact_disposable_folder");
        command(() -> controller.chooseSyncFolder(false, uri, GrantActivity.flags));
        check(granted(uri), "actual_persisted_read_grant");
        check(controller.syncView().binding().status() == SyncFolderBinding.Status.READY, "actual_saf_ready");
    }
    private void verifyLocalOnly() {
        check("Local only".equals(controller.dailySyncStatus()), "neutral_local_only_status");
        check(!port(controller.localId()).pendingPublication(), "no_local_only_retry_hint");
        var lane = field(controller, "providerIo");
        var executor = (ThreadPoolExecutor)field(lane, "executor");
        check(executor.getTaskCount() == 0, "zero_local_only_provider_operations");
        check(getTargetContext().getContentResolver().getPersistedUriPermissions().isEmpty(), "no_saf_grant_required");
        check("Local only".equals(MainActivity.mainStatus(controller.snapshot(), controller.dailySyncStatus(), controller.tokenChangeResult())), "neutral_product_ui");
    }
    private void persistenceFaults(String id, String candidate) {
        var original = port(id).load();
        for (int failAt : new int[]{1, 2, 3, 4}) {
            AtomicInteger commits = new AtomicInteger();
            Context faultContext = new ContextWrapper(getTargetContext()) {
                @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                    SharedPreferences delegate = super.getSharedPreferences(name, mode);
                    return (SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.class}, (proxy, method, values) -> {
                                if (!method.getName().equals("edit")) return method.invoke(delegate, values);
                                SharedPreferences.Editor edit = delegate.edit();
                                return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                        new Class<?>[]{SharedPreferences.Editor.class}, (editorProxy, editorMethod, arguments) -> {
                                            Object result = editorMethod.invoke(edit, arguments);
                                            // Simulate the harder ambiguous outcome: bytes reached disk,
                                            // yet the platform reports failure. No permission is revoked.
                                            if (editorMethod.getName().equals("commit")) {
                                                int count = commits.incrementAndGet();
                                                if (failAt == 4 && count == 3) throw new IllegalStateException("Fixture commit exception");
                                                if (count == failAt) return false;
                                            }
                                            return result instanceof SharedPreferences.Editor ? editorProxy : result;
                                        });
                            });
                }
            };
            var fault = new AndroidSyncFolderPort(faultContext, id);
            check(!fault.save(new SyncFolderBinding.Stored(candidate, true, true)), "failed_binding_commit_" + failAt);
            check(original.equals(fault.load()) && original.equals(port(id).load()), "old_binding_recoverable_" + failAt);
            if (failAt > 1) check(fault.configurationNotice().contains("recovery"), "uncertain_update_visible_" + failAt);
            check(port(id).save(original), "explicit_binding_recovery_" + failAt);
        }
        var preferences = getTargetContext().getSharedPreferences("sync_binding_mv1_" + id, Context.MODE_PRIVATE);
        var retained = new HashSet<>(preferences.getStringSet("retained_grants", Set.of()));
        check(preferences.edit().putString("retained_grants", "malformed fixture").commit(), "malformed_cleanup_fixture");
        check(port(id).configurationNotice().contains("recovery"), "malformed_cleanup_metadata_honest_notice");
        check(preferences.edit().putStringSet("retained_grants", retained).commit(), "cleanup_metadata_restored");
    }
    private String providerInventory(AndroidSyncFolderPort port, String uri) {
        var scan = port.scan(uri);
        check(scan.state() == org.totipo.android.provider.ProviderSnapshot.State.COMPLETE, "provider_inventory_complete");
        var inventory = new TreeMap<String, String>();
        for (var candidate : scan.vaultCandidates()) inventory.put("vault", Arrays.toString(candidate.bytes()));
        for (var directory : scan.directories()) for (var candidate : directory.candidates())
            inventory.put(candidate.document().id(), Arrays.toString(candidate.bytes()));
        return inventory.toString();
    }
    private void first(String uriA, String uriB) throws Exception {
        check(registry.entries().size() == 1 && app.vaultController() != null, "fresh_single_entry_startup");
        controller = app.vaultController(); await(this::idle);
        String a = controller.localId();
        activity = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        runOnMainSync(() -> activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        command(() -> controller.create("Public MV3 A".toCharArray())); add("local A"); verifyLocalOnly();
        // Two ordinary Java builders from a historical same-session basis create
        // a real conflict without any external provider or independent store writer.
        var state = session().state(); var alternative = state.token(row().id()).orElseThrow().alternatives().get(0);
        change(TokenChange.Kind.EDIT, "edited A");
        try (var branch = state.update(alternative)) { check(branch.account("local branch").save() instanceof SaveResult.Saved, "local_java_branch"); }
        await(() -> row().conflict()); await(this::idle); change(TokenChange.Kind.RESOLVE, null);
        await(() -> !row().conflict()); change(TokenChange.Kind.DELETE, null);
        await(() -> row().alternatives().get(0).status() == TokenStatus.TOMBSTONED); verifyLocalOnly();
        command(() -> controller.lock()); open("Public MV3 A"); verifyLocalOnly();
        var idA = vault().sessionVaultId();
        bind(uriA); check(!port(a).pendingPublication(), "attach_without_retry_hint");
        command(() -> controller.initializeSyncFolder()); command(() -> controller.sync());
        check(controller.dailySyncStatus().isEmpty(), "first_sync_fresh_reconciliation");
        check(vault().sessionVaultId().equals(idA), "attach_keeps_identity");
        persistenceFaults(a, uriB);
        check(!((SyncFolderBinding)field(controller, "syncBinding")).choose(false, uriB, true, true), "permission_acquisition_failure_preserves_binding");
        check(uriA.equals(port(a).load().uri()) && granted(uriA), "old_permission_and_binding_preserved");
        var b = registry.reserve("Public MV3 B").id(); retire(); select(b);
        command(() -> controller.create("Public MV3 B".toCharArray())); add("local B");
        check(!vault().sessionVaultId().equals(idA), "independent_cryptographic_identities");
        var bPort = port(b); var aPort = port(a);
        check(!new SyncFolderBinding(bPort).choose(false, uriA, true, true), "shared_folder_binding_refused");
        check(!new SyncFolderBinding(bPort).choose(false, uriA + "?alias=fixture", true, true), "obvious_tree_alias_refused");
        bind(uriB); command(() -> controller.initializeSyncFolder()); command(() -> controller.sync());
        check(controller.dailySyncStatus().isEmpty(), "B_own_provider_sync");
        // Historical/recovery duplicate reference: manager must preserve B's grant.
        check(bPort.save(aPort.load()), "duplicate_recovery_reference_saved");
        retire(); select(a); open("Public MV3 A"); command(() -> controller.disconnectSyncFolder());
        check(aPort.load() == null && uriA.equals(bPort.load().uri()), "disconnect_changes_only_A");
        check(granted(uriA), "shared_permission_preserved_for_B");
        check(bPort.save(new SyncFolderBinding.Stored(uriB, true, true)), "restore_B_own_binding");
        bind(uriA);
        // Hold an admitted A operation in the real lane with the real scoped port.
        // No claim that Android Binder itself can be paused/cancelled is made.
        var entered = new CountDownLatch(1); var late = new AtomicBoolean();
        var delegate = port(a); var lane = (ProviderIoLane)field(controller, "providerIo");
        var held = new SyncFolderBinding.Port() {
            public SyncFolderBinding.Stored load() { return delegate.load(); }
            public boolean save(SyncFolderBinding.Stored value) { throw new AssertionError(); }
            public boolean validTree(String uri) { return delegate.validTree(uri); }
            public SyncFolderBinding.Grants grants(String uri) { return delegate.grants(uri); }
            public SyncGrantOwnership.Use retain(String uri) { return delegate.retain(uri); }
            public void take(String uri, boolean r, boolean w) { throw new AssertionError(); }
            public void release(String uri, boolean r, boolean w) { throw new AssertionError(); }
            public void probe(String uri) {
                delegate.probe(uri); entered.countDown();
                try { if (!release.await(120, TimeUnit.SECONDS)) throw new AssertionError(); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            public org.totipo.android.provider.ProviderSnapshot.Scan scan(String uri) { throw new AssertionError(); }
        };
        lane.submit(held, ((SyncFolderBinding)field(controller, "syncBinding")).request(), false, value -> late.set(true));
        check(entered.await(10, TimeUnit.SECONDS), "A_provider_admitted");
        command(() -> controller.disconnectSyncFolder());
        check(granted(uriA), "disconnect_during_IO_preserves_authority");
        var old = controller;
        runOnMainSync(() -> app.selection().retire());
        await(() -> old.retirement() == AndroidVaultController.Retirement.WAITING_PROVIDER);
        runOnMainSync(() -> {
            try { app.selectLocalEntry(b); throw new AssertionError("B before A drain"); }
            catch (IllegalStateException expected) { checks++; }
            catch (Exception failure) { throw new AssertionError(failure); }
        });
        release.countDown(); await(() -> app.selection().snapshot() == null);
        check(!late.get(), "late_A_completion_inert"); select(b); open("Public MV3 B");
        check(controller.initialTreeUri().equals(uriB), "B_not_retargeted_by_A");
        getTargetContext().getContentResolver().releasePersistableUriPermission(Uri.parse(uriB), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        command(() -> controller.checkSyncFolder());
        check(controller.syncView().binding().status() == SyncFolderBinding.Status.ACCESS_LOST && bPort.load() != null, "revoked_is_configured_access_needed");
        change(TokenChange.Kind.EDIT, "B offline"); check(bPort.pendingPublication(), "revoked_provider_local_save_complete");
        bind(uriB); command(() -> controller.sync()); check(!bPort.pendingPublication(), "restored_access_retries_safely");
        retire(); select(a); open("Public MV3 A"); bind(uriA); command(() -> controller.sync());
        String beforeMismatch = providerInventory(bPort, uriB);
        var rememberedB = bPort.load(); check(bPort.save(null), "prepare_unreferenced_foreign_folder_fixture");
        bind(uriB); command(() -> controller.sync());
        check(controller.dailySyncStatus().equals("This sync folder belongs to a different Totipo vault."), "foreign_provider_identity_blocks_publication");
        check(beforeMismatch.equals(providerInventory(bPort, uriB)), "foreign_provider_bytes_preserved");
        bind(uriA); check(bPort.save(rememberedB), "restore_B_after_mismatch_fixture");
        command(() -> controller.sync());
        getTargetContext().getContentResolver().releasePersistableUriPermission(Uri.parse(uriA), Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        add("A pending restart"); check(aPort.pendingPublication() && !bPort.pendingPublication(), "independent_retry_hints");
        command(() -> controller.lock()); retire();
        // Fault fixture: stop at the exact durable checkpoint before a binding
        // replacement's final commit. Next phase is a genuinely cold process.
        check(getTargetContext().getSharedPreferences("sync_binding_mv1_" + a, Context.MODE_PRIVATE).edit()
                .putBoolean("binding_recovery", true).putBoolean("recovery_configured", true)
                .putString("recovery_uri", uriA).putBoolean("recovery_read", true)
                .putBoolean("recovery_write", true).putBoolean("recovery_pending", true).commit(), "interrupted_binding_checkpoint_durable");
        check(uriA.equals(aPort.load().uri()) && uriB.equals(bPort.load().uri()), "independent_remembered_uris");
    }
    private void restart(String uriA, String uriB) {
        check(registry.entries().size() == 2 && app.vaultController() == null, "restart_keeps_public_multi_entry_navigation_unavailable");
        String a = registry.entries().get(0).id(), b = registry.entries().get(1).id();
        check(uriA.equals(port(a).load().uri()) && uriB.equals(port(b).load().uri()), "cold_process_scoped_uris_restored");
        check(port(a).pendingPublication() && !port(b).pendingPublication(), "cold_process_retry_hints_restored");
        select(a); open("Public MV3 A");
        check(controller.syncView().binding().status() == SyncFolderBinding.Status.ACCESS_LOST, "restart_missing_grant_not_local_only");
        check(controller.snapshot().view().tokens().size() == 2, "restart_A_local_history_preserved");
        check(port(a).configurationNotice().contains("recovery"), "cold_process_interrupted_binding_visible");
        bind(uriA); command(() -> controller.sync());
        check(!port(a).pendingPublication() && !port(a).configurationNotice().contains("recovery"), "cold_process_explicit_rebind_recovers");
        retire(); select(b); open("Public MV3 B");
        check(controller.snapshot().view().tokens().size() == 1 && row().alternatives().get(0).account().equals("B offline"), "restart_B_local_data_isolated");
        command(() -> controller.sync()); check(controller.dailySyncStatus().isEmpty(), "restart_B_uses_own_provider"); retire();
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = 0;
        try {
            check(getTargetContext().getPackageName().equals("org.totipo.securityqualification"), "disposable_package_only");
            check(!getTargetContext().getSystemService(KeyguardManager.class).isKeyguardLocked(), "device_unlocked");
            var accessibility = getUiAutomation().getServiceInfo();
            accessibility.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            getUiAutomation().setServiceInfo(accessibility);
            runOnMainSync(() -> {});
            app = (TotipoApplication)getTargetContext().getApplicationContext(); registry = (VaultRegistry)field(app, "registry");
            if ("mv3-first".equals(args.getString("phase"))) first(args.getString("folder_a"), args.getString("folder_b"));
            else restart(args.getString("folder_a"), args.getString("folder_b"));
            result.putString("stream", "PER_VAULT_SYNC_PASS phase=" + args.getString("phase") + " checks=" + checks + "; actual SAF, controlled held call; no Syncthing claim\n"); code = -1;
        } catch (Throwable failure) {
            result.putString("stream", "PER_VAULT_SYNC_FAIL step=" + step + " type=" + failure + "\n" + Arrays.toString(failure.getStackTrace()) + "\n");
        } finally {
            release.countDown();
            if (activity != null) runOnMainSync(activity::finish);
            if (app != null && app.selection().snapshot() != null) try { retire(); } catch (Throwable failure) { code = 0; result.putString("cleanup", "retirement failed"); }
        }
        finish(code, result);
    }
}
