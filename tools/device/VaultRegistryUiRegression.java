package org.totipo.android;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.totipo.android.reconcile.ForegroundVaultCoordinator;
import org.totipo.android.sync.*;

/** Sequential cold-process UI/recovery qualification in a fresh disposable package only.
 * Faults affect only this harness's own synthetic vault/metadata. No biometric key,
 * real SAF grant, user package or simultaneous session is used. Each phase requires
 * the preceding phase's exact evidence; none resets data to make a test pass.
 */
public final class VaultRegistryUiRegression extends Instrumentation {
    private String phase, step = "setup";
    private int checks;
    private TotipoApplication application;
    private AndroidVaultController controller;
    private MainActivity activity;
    private Path base, marker;
    private final Application.ActivityLifecycleCallbacks awake = new Application.ActivityLifecycleCallbacks() {
        public void onActivityCreated(Activity a, Bundle b) { a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
        public void onActivityStarted(Activity a) {} public void onActivityResumed(Activity a) {}
        public void onActivityPaused(Activity a) {} public void onActivityStopped(Activity a) {}
        public void onActivitySaveInstanceState(Activity a, Bundle b) {} public void onActivityDestroyed(Activity a) {}
    };
    private void check(boolean value, String label) {
        step = label; if (!value) throw new AssertionError(label); checks++;
    }
    private Object field(Object value, String name) throws Exception {
        var f = value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }
    private boolean idle() {
        try { synchronized (controller) {
            for (String flag : List.of("operating", "viewQueued", "providerActive", "revealing", "syncPending", "syncDrainQueued"))
                if ((boolean)field(controller, flag)) return false;
            return true;
        }} catch (Exception failure) { throw new AssertionError(failure); }
    }
    private void await(BooleanSupplier ready) {
        long end = SystemClock.elapsedRealtime() + 120000;
        while (!ready.getAsBoolean()) {
            if (SystemClock.elapsedRealtime() >= end) throw new AssertionError("timeout");
            SystemClock.sleep(20);
        }
    }
    private void state(AndroidVaultController.State expected) {
        await(() -> controller.snapshot().state() == expected && idle()); waitForIdleSync();
        check(controller.snapshot().state() == expected, "state_" + expected);
    }
    private void launch() throws Exception {
        step = "launch_unlocked_activity";
        runOnMainSync(() -> application.registerActivityLifecycleCallbacks(awake));
        activity = (MainActivity)startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        await(activity::hasWindowFocus); waitForIdleSync();
        check(activity.hasWindowFocus(), "activity_focused");
    }
    private void authenticate(boolean create, boolean wrong) throws Exception {
        check(controller != null, "controller_present");
        runOnMainSync(() -> {
            try {
                ((EditText)field(activity, "password")).setText(wrong ? "WrongPublicFixturePassword" : "PublicMV1DevicePassword");
                if (create) ((EditText)field(activity, "confirmation")).setText("PublicMV1DevicePassword");
                ((Button)field(activity, "action")).performClick();
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        if (wrong) {
            await(() -> idle() && controller.snapshot().error() == AndroidVaultController.Error.AUTHENTICATION_FAILED);
            check(controller.snapshot().state() == AndroidVaultController.State.LOCKED, "wrong_password_stays_locked");
        } else state(AndroidVaultController.State.OPEN);
    }
    private void lock() {
        runOnMainSync(() -> check(controller.lock(), "lock_admitted")); state(AndroidVaultController.State.LOCKED);
        check(controller.snapshot().view() == null && controller.snapshot().revealedCode() == null, "locked_snapshot_empty");
    }
    private void passwordOnly() throws Exception {
        check(field(activity, "enableBiometric") == null, "no_biometric_enrollment_control");
        check(!((BiometricUnlock)field(activity, "biometric")).available(), "production_biometrics_disabled");
    }
    private Path metadata() { return base.resolve("totipo-local-v1/registry"); }
    private Path savedVault() { return base.resolve("mv1-test-vault.saved"); }
    private Path savedRegistry() { return base.resolve("mv1-test-registry.saved"); }
    private String sha(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        StringBuilder text = new StringBuilder(); for (byte b : digest) text.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return text.toString();
    }
    private List<String> evidence() throws Exception {
        List<String> lines = Files.readAllLines(marker, StandardCharsets.UTF_8);
        check(lines.size() == 3, "fixture_evidence_shape"); VaultRegistry.validateId(lines.get(0));
        check(lines.get(1).matches("[0-9a-f]{64}") && lines.get(2).matches("[0-9a-f]{64}"), "fixture_evidence_ids");
        return lines;
    }
    private Path root(List<String> evidence) { return base.resolve("totipo-local-v1/roots").resolve(evidence.get(0)).resolve("store"); }
    private String openId() throws Exception {
        return ((ForegroundVaultCoordinator)field(controller, "vault")).sessionVaultId().hex();
    }
    private void identity(List<String> evidence) throws Exception {
        check(application.replicaOwner().localId().equals(evidence.get(0)), "remembered_local_id");
        check(openId().equals(evidence.get(1)), "same_authenticated_protocol_id");
        check(sha(metadata()).equals(evidence.get(2)), "registry_bytes_preserved");
    }
    private void texts(View view, List<String> text) {
        if (view instanceof TextView label) text.add(label.getText().toString());
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) texts(group.getChildAt(i), text);
    }
    private void diagnostic(String expected) {
        var text = new ArrayList<String>(); runOnMainSync(() -> texts(activity.getWindow().getDecorView(), text));
        check(text.stream().anyMatch(t -> t.contains(expected)), "visible_recovery_diagnostic");
    }
    private void scopedPreferences() throws Exception {
        Path fixture = Files.createTempDirectory(getTargetContext().getCacheDir().toPath(), "mv1-preferences-").toRealPath();
        String a = null, b = null;
        try {
            var registry = new VaultRegistry(fixture); a = registry.reserve(null).id(); b = registry.reserve(null).id();
            var first = new AndroidSyncFolderPort(getTargetContext(), a);
            var second = new AndroidSyncFolderPort(getTargetContext(), b);
            check(first.load() == null && second.load() == null, "scoped_preferences_start_empty");
            var stored = new SyncFolderBinding.Stored("content://mv1-fixture.invalid/tree/first", true, true);
            check(first.save(stored), "scoped_binding_write_checked"); first.pendingPublication(true);
            var restored = new AndroidSyncFolderPort(getTargetContext(), a);
            check(stored.equals(restored.load()) && restored.pendingPublication(), "scoped_binding_and_hint_restore");
            check(second.load() == null && !second.pendingPublication(), "other_entry_has_no_inherited_binding_or_hint");
            var other = new SyncFolderBinding.Stored("content://mv1-fixture.invalid/tree/second", true, false);
            check(second.save(other), "second_scoped_binding_saved");
            var binding = new SyncFolderBinding(restored); binding.restore();
            check(binding.view().status() == SyncFolderBinding.Status.ACCESS_LOST, "stored_uri_has_no_implicit_grant");
            check(binding.disconnect(), "scoped_disconnect_checked"); restored.pendingPublication(false);
            check(new AndroidSyncFolderPort(getTargetContext(), a).load() == null && !restored.pendingPublication(), "scoped_disconnect_persists");
            check(other.equals(new AndroidSyncFolderPort(getTargetContext(), b).load()), "disconnect_preserves_other_entry");
        } finally {
            if (a != null) getTargetContext().deleteSharedPreferences("sync_binding_mv1_" + a);
            if (b != null) getTargetContext().deleteSharedPreferences("sync_binding_mv1_" + b);
            try (var files = Files.walk(fixture)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.delete(path);
            }
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); phase = args.getString("phase"); start(); }
    @Override public void onStart() {
        Bundle output = new Bundle(); int code = 0;
        try {
            check(getTargetContext().getPackageName().equals("org.totipo.securityqualification"), "isolated_package_only");
            // Instrumentation.onStart can race Application.onCreate. A main-loop
            // barrier observes completed Application startup, including intentional
            // fail-closed startup with no controller.
            runOnMainSync(() -> {
                application = (TotipoApplication)getTargetContext().getApplicationContext();
                controller = application.vaultController();
            });
            base = getTargetContext().getNoBackupFilesDir().toPath().toRealPath();
            marker = base.resolve("mv1-test-evidence");
            if (phase.equals("mv1-first")) {
                check(!Files.exists(marker), "fresh_fixture_only"); state(AndroidVaultController.State.NO_LOCAL_VAULT);
                launch(); authenticate(true, false);
                var registry = new VaultRegistry(base); check(registry.entries().size() == 1, "one_managed_entry");
                check(registry.entries().get(0).state() == VaultRegistry.State.ESTABLISHED, "creation_established");
                Files.write(marker, List.of(application.replicaOwner().localId(), openId(), sha(metadata())), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                lock(); passwordOnly(); scopedPreferences();
            } else if (phase.equals("mv1-restart")) {
                var evidence = evidence(); state(AndroidVaultController.State.LOCKED); launch(); passwordOnly();
                authenticate(false, true); authenticate(false, false); identity(evidence); lock();
                try (var lease = application.replicaOwner().acquire()) {
                    check(lease.root().equals(root(evidence)), "immutable_selected_root");
                    check(!Files.exists(savedVault()), "no_prior_vault_fault");
                    Files.move(lease.root().resolve("vault"), savedVault(), StandardCopyOption.ATOMIC_MOVE);
                }
            } else if (phase.equals("mv1-missing")) {
                var evidence = evidence(); state(AndroidVaultController.State.ERROR_LOCKED); launch(); diagnostic("recover storage");
                check(!Files.exists(root(evidence).resolve("vault")), "missing_vault_not_recreated");
                runOnMainSync(() -> check(!controller.create("PublicMV1DevicePassword".toCharArray()), "create_refused_for_remembered_missing_data"));
                try (var lease = application.replicaOwner().acquire()) {
                    Files.move(savedVault(), lease.root().resolve("vault"), StandardCopyOption.ATOMIC_MOVE);
                }
                runOnMainSync(() -> check(controller.retryDiscovery(), "explicit_recovery_check")); state(AndroidVaultController.State.LOCKED);
                authenticate(false, false); identity(evidence); lock();
                check(!Files.exists(savedRegistry()), "no_prior_metadata_fault"); Files.copy(metadata(), savedRegistry());
                Files.write(metadata(), new byte[]{1, 2});
            } else if (phase.equals("mv1-corrupt")) {
                var evidence = evidence(); check(controller == null, "corrupt_registry_has_no_controller");
                check(application.startupDiagnostic().contains("Corrupt"), "corrupt_registry_diagnostic"); launch(); diagnostic("Corrupt");
                check(Arrays.equals(Files.readAllBytes(metadata()), new byte[]{1, 2}), "corrupt_metadata_not_overwritten");
                check(org.totipo.Totipo.vaultId(Files.readAllBytes(root(evidence).resolve("vault"))).hex().equals(evidence.get(1)), "vault_preserved_beside_corruption");
                Files.move(savedRegistry(), metadata(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } else if (phase.equals("mv1-restore")) {
                var evidence = evidence(); state(AndroidVaultController.State.LOCKED); launch(); authenticate(false, false); identity(evidence); lock();
                Path legacy = base.resolve("totipo-vault"); check(!Files.exists(legacy), "no_preexisting_legacy_fixture");
                Files.createDirectory(legacy); Files.write(legacy.resolve("vault"), new byte[]{7, 8, 9}, StandardOpenOption.CREATE_NEW);
            } else if (phase.equals("mv1-legacy")) {
                var evidence = evidence(); check(controller == null, "legacy_has_no_controller");
                check(VaultRegistry.LEGACY_DIAGNOSTIC.equals(application.startupDiagnostic()), "incompatible_installation_diagnostic");
                launch(); diagnostic("Incompatible single-vault"); diagnostic("unsynchronized local vaults");
                check(sha(metadata()).equals(evidence.get(2)), "registry_not_changed_by_legacy_detection");
                check(org.totipo.Totipo.vaultId(Files.readAllBytes(root(evidence).resolve("vault"))).hex().equals(evidence.get(1)), "managed_vault_not_changed_by_legacy_detection");
                Path legacy = base.resolve("totipo-vault");
                check(Arrays.equals(Files.readAllBytes(legacy.resolve("vault")), new byte[]{7, 8, 9}), "legacy_bytes_preserved");
                try (var children = Files.list(legacy)) { check(children.count() == 1, "only_known_synthetic_legacy_file"); }
                Files.delete(legacy.resolve("vault")); Files.delete(legacy);
            } else if (phase.equals("mv1-finish")) {
                var evidence = evidence(); state(AndroidVaultController.State.LOCKED); launch(); passwordOnly();
                authenticate(false, false); identity(evidence); lock();
            } else throw new IllegalArgumentException("Unknown fixed phase");
            output.putString("stream", "MV1_UI_PASS phase=" + phase + " checks=" + checks + "\n"); code = -1;
        } catch (Throwable failure) {
            output.putString("stream", "MV1_UI_FAIL phase=" + phase + " step=" + step + " type=" + failure.getClass().getSimpleName() + "\n");
            if (application != null) output.putString("startup_diagnostic", application.startupDiagnostic());
            if (failure.getStackTrace().length > 0) output.putInt("failure_line", failure.getStackTrace()[0].getLineNumber());
        } finally {
            try {
                // A harmless modifier event refreshes device user activity before the
                // window closes, bridging cold-process phases without global settings.
                if (activity != null && activity.hasWindowFocus()) sendKeyDownUpSync(KeyEvent.KEYCODE_SHIFT_LEFT);
                if (activity != null) runOnMainSync(() -> activity.finish());
                if (application != null) runOnMainSync(() -> application.unregisterActivityLifecycleCallbacks(awake));
            } catch (Throwable failure) { code = 0; output.putString("stream", "MV1_UI_CLEANUP_FAIL\n"); }
            finish(code, output);
        }
    }
}
