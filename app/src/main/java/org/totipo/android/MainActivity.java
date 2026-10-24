package org.totipo.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;
import android.text.TextWatcher;
import android.text.Editable;
import org.totipo.android.AndroidVaultController.Snapshot;
import org.totipo.android.AndroidVaultController.State;

/** Presentation only: application controller commands and detached snapshots. */
public final class MainActivity extends Activity {
    private AndroidVaultController controller;
    private String pickerActivation;
    private String boundActivation;
    private boolean replacing;
    private final java.util.function.Consumer<VaultSelection.Snapshot> selectionObserver = value -> {
        String activation = value == null ? null : value.activationId();
        if (!java.util.Objects.equals(boundActivation, activation)) {
            clearPasswords(); clearSecret(); clearCodeWidgets(); dismissTokenChange();
            if (this.biometric != null) this.biometric.close();
            if (controller != null) controller.detach(this.listener);
            if (!replacing) { replacing = true; recreate(); }
        } else if (controller != null && !controller.selectedAuthority()) render(controller.snapshot());
        else if (controller != null && !replacing) render(controller.snapshot());
        else if (controller == null && !replacing) renderSelection();
    };
    private BiometricUnlock biometric;
    private android.widget.CheckBox enableBiometric;
    private boolean resumed;
    private String biometricUi;
    private EditText vaultName;
    private boolean updatingVaultName;
    private TotipoApplication application() { return (TotipoApplication) getApplication(); }
    private void renderSelection() {
        build("selection");
        boolean initialSetup = application().localEntries().size() == 1 && application().unusedReservation(application().localEntries().get(0));
        if (Build.VERSION.SDK_INT < 30) { label(getString(R.string.unsupported_runtime)); return; }
        String diagnostic = application().startupDiagnostic();
        if (diagnostic != null) {
            label("Storage recovery required").setTextSize(20);
            label(diagnostic); label("Preserve application data. Do not clear data or uninstall to resolve this condition."); return;
        }
        label(application().managementNotice());
        if (application().managementBusy()) { label("Please wait for cleanup to finish."); return; }
        var entries = application().localEntries();
        label("Your vaults").setTextSize(20);
        for (var entry : entries) {
            if (entry.state() != VaultRegistry.State.ESTABLISHED) continue;
            String title = application().localName(entry);
            boolean highlighted = entry.id().equals(application().lastSelected());
            LinearLayout row = new LinearLayout(this); content.addView(row);
            Button select = new Button(this); select.setAllCaps(false);
            select.setText(title + (highlighted ? " · Last selected" : "") + "\n" + application().entrySummary(entry));
            select.setContentDescription("Open " + title + " · " + application().entrySummary(entry));
            select.setSelected(highlighted); select.setMinHeight(dp(64));
            select.setOnClickListener(ignored -> application().selectFromUi(entry.id(), false));
            row.addView(select, new LinearLayout.LayoutParams(0, -2, 1));
            Button more = buttonIn(row, "⋮", () -> {});
            more.setContentDescription("Manage " + title);
            more.setOnClickListener(ignored -> entryMenu(more, entry));
        }
        if (initialSetup) label("Create a local vault or join an existing vault.");
        sectionGap(); label("Add vault").setTextSize(20);
        button("Create Local Vault", () -> application().beginSetup(false));
        button("Join Existing Vault", () -> application().beginSetup(true));
        var recovery = entries.stream().filter(e -> e.state() != VaultRegistry.State.ESTABLISHED
                && !(entries.size() == 1 && application().unusedReservation(e))).collect(java.util.stream.Collectors.toList());
        if (!recovery.isEmpty()) {
            sectionGap(); label("Recovery").setTextSize(20);
            label("Retained data requires this installation’s app data and the vault password.");
            for (var entry : recovery) {
                LinearLayout row = new LinearLayout(this); content.addView(row);
                TextView text = new TextView(this); text.setText(application().localName(entry) + "\n" + application().entrySummary(entry));
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                Button more = buttonIn(row, "⋮", () -> {}); more.setContentDescription("Manage " + application().localName(entry));
                more.setOnClickListener(ignored -> entryMenu(more, entry));
            }
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void sectionGap() { View gap = new View(this); content.addView(gap, new LinearLayout.LayoutParams(1, dp(24))); }
    private void entryMenu(View anchor, VaultRegistry.Entry entry) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rename").setOnMenuItemClickListener(item -> { renameEntry(entry); return true; });
        if (entry.state() == VaultRegistry.State.PENDING && !entry.creationAttempted()) {
            menu.getMenu().add("Resume Create").setOnMenuItemClickListener(item -> { application().selectFromUi(entry.id(), false); return true; });
            menu.getMenu().add("Resume Join").setOnMenuItemClickListener(item -> { application().selectFromUi(entry.id(), true); return true; });
            menu.getMenu().add("Recover unused allocation").setOnMenuItemClickListener(item -> { application().recoverAllocation(entry.id()); return true; });
        } else if (entry.state() == VaultRegistry.State.FORGETTING) {
            menu.getMenu().add("Retry Forget cleanup").setOnMenuItemClickListener(item -> { application().forgetEntry(entry.id()); return true; });
        } else if (entry.state() != VaultRegistry.State.ESTABLISHED) {
            menu.getMenu().add("Recover").setOnMenuItemClickListener(item -> { application().selectFromUi(entry.id(), false); return true; });
        }
        if (entry.state() == VaultRegistry.State.DETACHED)
            menu.getMenu().add("Retry retained permission cleanup").setOnMenuItemClickListener(item -> { application().forgetEntry(entry.id()); return true; });
        else if (entry.state() != VaultRegistry.State.FORGETTING)
            menu.getMenu().add("Forget…").setOnMenuItemClickListener(item -> { confirmForget(entry); return true; });
        menu.show();
    }
    private void renameEntry(VaultRegistry.Entry entry) {
        EditText field = new EditText(this); field.setHint("Local display label (optional)");
        field.setContentDescription("Local display label"); field.setSingleLine(true); field.setSaveEnabled(false);
        field.setText(entry.label() == null ? "" : entry.label());
        new AlertDialog.Builder(this).setTitle("Rename vault").setView(field)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> application().renameEntry(entry.id(), field.getText().toString())).show();
    }
    private void confirmForget(VaultRegistry.Entry entry) {
        new AlertDialog.Builder(this).setTitle("Forget " + application().localName(entry) + "?")
                .setMessage("This removes the vault from normal selection, its biometric credential and its sync folder association. Encrypted local data is kept in this installation's private storage. Recovering it requires the vault password. Clearing app data or uninstalling can permanently destroy it.")
                .setNegativeButton("Cancel", null).setPositiveButton("Forget", (dialog, which) -> application().forgetEntry(entry.id())).show();
    }
    private void changeVault() {
        clearPasswords(); clearSecret(); clearCodeWidgets(); dismissTokenChange();
        if (biometric != null) biometric.close();
        controller.cancelBiometricEnrollment(); controller.hideCode();
        adding = submitted = managing = joining = false; pickerActivation = null;
        application().changeVault();
        render(controller.snapshot());
    }
    @Override public void onUserInteraction() {
        super.onUserInteraction(); if (controller != null) controller.userInteraction();
    }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (controller != null) { controller.checkInactivity(); render(controller.snapshot()); }
        else if (!replacing) renderSelection();
    }
    @Override protected void onPause() {
        resumed = false;
        if (biometric != null) biometric.close();
        if (controller != null) controller.cancelBiometricEnrollment();
        super.onPause();
    }

    private final AndroidVaultController.Listener listener = new AndroidVaultController.Listener() {
        public void changed(Snapshot state) { if (!replacing) render(state); }
        public void revealChanged(Snapshot state) {
            if (tokens != null) tokens.updateRevealPresentation(state.revealedCode(), state.remainingSeconds());
        }
    };
    private LinearLayout content;
    private TextView status;
    private TokenListAdapter tokens;
    private EditText password, confirmation;
    private Button action, syncAction;
    private boolean managing;
    private TextView syncStatus;
    private Button joinVault, initializeFolder;
    private boolean joining;
    private Button chooseFolder, disconnectFolder, checkFolder;
    private static final int SYNC_TREE_REQUEST = 310;
    private String surface;
    private boolean adding, submitted;
    private EditText issuer, account, secret, period;
    private Spinner algorithm, digits;
    private Button add, cancel, scan;
    private AlertDialog tokenDialog;
    private TokenChange tokenChange;


    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        controller = ((TotipoApplication) getApplication()).vaultController();
        boundActivation = controller == null ? null : controller.activationId();
        // Retain only the picker activation across configuration recreation. Process
        // death loses this capability and rejects the outstanding result.
        Object retained = getLastNonConfigurationInstance();
        pickerActivation = retained instanceof String activation ? activation : null;
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        joining = application().joining(controller);
        if (controller == null) {
            renderSelection();
        } else {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            BiometricCredentials credentials;
            try { credentials = ((TotipoApplication) getApplication()).biometricCredentials(controller); }
            catch (java.io.IOException | IllegalStateException unavailable) { credentials = new UnavailableVaultBiometricCredentials(controller.localId()); }
            biometric = new BiometricUnlock(controller, credentials,
                    new FrameworkBiometricPrompt(this, ((TotipoApplication) getApplication()).selectedVaultLabel(controller)), (delay, action) -> {
                        handler.postDelayed(action, delay); return () -> handler.removeCallbacks(action);
                    });
            biometric.changed = () -> handler.post(() -> { if (resumed && !replacing) render(controller.snapshot()); });
            controller.checkInactivity();
            render(controller.snapshot());
        }
    }
    @Override protected void onStart() {
        super.onStart();
        ((TotipoApplication) getApplication()).selection().observe(selectionObserver);
        if (!replacing && controller != null) { controller.checkInactivity(); render(controller.snapshot()); controller.attach(listener); }
    }
    @Override protected void onStop() { ((TotipoApplication) getApplication()).selection().detach(selectionObserver); if (biometric != null) biometric.close(); if (controller != null) controller.cancelBiometricEnrollment(); clearPasswords(); clearSecret(); dismissTokenChange(); if (controller != null) controller.detach(listener); super.onStop(); }
    // No session closure on Activity stop/destruction. No credential Bundle or saved widget state.
    private void render(Snapshot state) {
        if (state.state() == State.LOCKING || state.state() == State.FAILED_CLOSE || state.state() == State.RETIRING || state.state() == State.RETIRED) {
            if (biometric != null) biometric.close(); dismissTokenChange(); managing = false;
        }
        String bio = biometric.available() + ":" + biometric.configured();
        if ("unlock".equals(surface) && !bio.equals(biometricUi)) surface = null;
        biometricUi = bio;
        if (tokenChange != null && controller.pendingTokenChange() != tokenChange) dismissTokenChange();
        if (submitted && state.state() == State.OPEN && state.addOutcome() != null) {
            submitted = false;
            if (state.addOutcome().status() == AddTokenOutcome.Status.ADDED
                    || state.addOutcome().status() == AddTokenOutcome.Status.PUBLICATION_UNCERTAIN) {
                clearSecret(); adding = false;
            }
        }
        if (state.state() != State.OPEN && state.state() != State.BUSY) { clearSecret(); adding = submitted = false; }

        String next = switch (state.state()) {
            case NO_LOCAL_VAULT, CREATING -> joining ? (controller.hasJoinCandidate() && state.state() == State.NO_LOCAL_VAULT ? "join" : "joinSetup") : "create";
            case LOCKED, UNLOCKING -> "unlock";
            case OPEN, BUSY -> adding ? "add" : "open";
            default -> "status";
        };
        if (managing && !joining && (state.state() == State.OPEN || state.state() == State.LOCKED || state.state() == State.NO_LOCAL_VAULT)) next = "manage";
        if (!next.equals(surface)) build(next);
        String message = next.equals("open") ? mainStatus(state) : state.message();
        if (!biometric.message().isEmpty() && (next.equals("unlock") || next.equals("open")))
            message = message.isEmpty() ? biometric.message() : message + "\n" + biometric.message();
        if (next.equals("open") || next.equals("manage") || next.equals("join") || next.equals("joinSetup")) {
            String notice = controller.folderConfigurationNotice();
            if (!notice.isEmpty()) message = message.isEmpty() ? notice : notice + "\n" + message;
        }
        if (!TextUtils.equals(status.getText(), message)) status.setText(message);
        status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        var sync = controller.syncView();
        if (chooseFolder != null) {
            syncStatus.setText(sync.message());
            boolean configured = sync.binding().status() != org.totipo.android.sync.SyncFolderBinding.Status.NOT_CONFIGURED;
            chooseFolder.setText(configured ? "Change sync folder" : "Choose sync folder");
            chooseFolder.setEnabled(controller.canManageSyncFolder());
            joinVault.setVisibility(state.state() == State.NO_LOCAL_VAULT ? View.VISIBLE : View.GONE);
            joinVault.setEnabled(controller.canJoinExistingVault());
            initializeFolder.setVisibility(state.state() == State.OPEN ? View.VISIBLE : View.GONE);
            initializeFolder.setEnabled(controller.canInitializeSyncFolder());
            disconnectFolder.setVisibility(configured ? View.VISIBLE : View.GONE);
            disconnectFolder.setEnabled(controller.canManageSyncFolder());
            checkFolder.setVisibility(configured ? View.VISIBLE : View.GONE);
            checkFolder.setEnabled(controller.canManageSyncFolder());
        }
        if (action != null) {
            action.setEnabled(state.state() == State.NO_LOCAL_VAULT || state.state() == State.LOCKED
                    || state.state() == State.ERROR_LOCKED || state.state() == State.FAILED_CLOSE || state.state() == State.ERROR_OPEN);
            if (next.equals("status")) {
                action.setText(state.state() == State.ERROR_LOCKED ? "Retry storage check"
                        : controller.retirement() == AndroidVaultController.Retirement.CLOSE_FAILED ? "Retry closure" : "Retry Lock");
                action.setVisibility(state.state() == State.ERROR_LOCKED || state.state() == State.FAILED_CLOSE
                        || state.state() == State.ERROR_OPEN ? View.VISIBLE : View.GONE);
            }
        }
        if (next.equals("add")) {
            boolean enabled = state.state() == State.OPEN;
            for (View field : new View[]{issuer, account, secret, period, algorithm, digits, add, cancel, scan}) field.setEnabled(enabled);
        }
        if (add != null && (next.equals("open") || next.equals("add"))) add.setEnabled(controller.canAddToken());
        if (syncAction != null) {
            renderSyncControl(syncAction, controller.dailySyncStatus());
            syncAction.setEnabled(controller.canSync());
        }

        if (tokens != null) {
            var view = state.view();
            tokens.replace(view == null ? java.util.List.of() : view.tokens(), controller.canAddToken(),
                    state.revealedCode(), state.remainingSeconds());
        }
        if (vaultName != null && controller.selectedAuthority()) {
            String value = application().setupName(controller).value();
            if (!TextUtils.equals(vaultName.getText(), value)) {
                updatingVaultName = true; vaultName.setText(value); updatingVaultName = false;
            }
        }
        maybeAutomaticBiometric();
    }
    private void maybeAutomaticBiometric() {
        if (resumed && !replacing && !isFinishing() && !isDestroyed() && hasWindowFocus()
                && getWindow().getDecorView().isAttachedToWindow() && "unlock".equals(surface)
                && controller != null && controller.selectedAuthority() && biometric != null)
            biometric.automaticUnlock();
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) maybeAutomaticBiometric();
    }
    private String heading(String next) {
        return switch (next) {
            case "selection" -> "Vaults";
            case "create" -> "Create local vault";
            case "join", "joinSetup" -> "Join existing vault";
            case "manage" -> "Sync folder";
            case "add" -> "Add token";
            default -> controller != null && controller.selectedAuthority() ? application().selectedVaultLabel(controller) : "Vault";
        };
    }
    private void buildVaultName() {
        SetupVaultName draft = application().setupName(controller);
        vaultName = metadataField(content, "Vault name (optional)", draft.value());
        vaultName.setSingleLine(true); vaultName.setHint("Personal, Work, Infrastructure");
        label("Identifies this vault only on this device. Up to 128 UTF-16 code units.");
        vaultName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (!updatingVaultName) draft.edit(text.toString());
            }
            public void afterTextChanged(Editable text) {}
        });
    }
    static String grouped(String value) {
        int split = (value.length() + 1) / 2;
        return value.substring(0, split) + " " + value.substring(split);
    }

    private void build(String next) {
        clearPasswords();
        clearSecret();
        clearCodeWidgets();
        tokens = null;
        issuer = account = secret = period = null; algorithm = digits = null; add = cancel = scan = null;
        enableBiometric = null; vaultName = null;
        surface = next; password = confirmation = null; action = syncAction = null;
        chooseFolder = disconnectFolder = checkFolder = joinVault = initializeFolder = null; syncStatus = null;
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        View root = next.equals("open") ? content : scroll;
        if (Build.VERSION.SDK_INT >= 30) root.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        content.setSaveEnabled(false); content.setSaveFromParentEnabled(false);
        if (!next.equals("open")) { scroll.setFillViewport(true); scroll.addView(content); }
        setContentView(root);
        LinearLayout toolbar = new LinearLayout(this); content.addView(toolbar);
        TextView title = new TextView(this); title.setText(heading(next)); title.setTextSize(28);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (next.equals("open")) {
            syncAction = buttonIn(toolbar, "Sync", () -> controller.sync());
            syncAction.setContentDescription("Sync");
            reserveSyncControlWidth(syncAction);
        }
        Button more = buttonIn(toolbar, "⋮", () -> {}); more.setContentDescription("More options");
        more.setOnClickListener(ignored -> showOverflow(more));
        more.setVisibility(controller == null || !controller.selectedAuthority() ? View.GONE : View.VISIBLE);
        status = label(""); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (next.equals("selection")) return;
        if (next.equals("create") || next.equals("joinSetup") || next.equals("join")) buildVaultName();
        if (next.equals("manage") || next.equals("joinSetup") || next.equals("join")) buildFolderManagement();
        if (next.equals("create")) label("Create a new independent local-only vault. A Sync folder can be configured after creation.");
        if (next.equals("joinSetup")) {
            label("Choose a folder containing an existing Totipo vault, then check the vault before entering its password.");
            button("Cancel setup", this::changeVault); return;
        }
        if (next.equals("manage")) button("Back", () -> { managing = false; surface = null; render(controller.snapshot()); });
        if (next.equals("create") || next.equals("unlock") || next.equals("join")) {
            password = passwordField("Password");
            if (next.equals("create")) confirmation = passwordField("Confirm password");
            action = button(next.equals("join") ? "Join existing vault" : next.equals("create") ? "Create Vault" : "Unlock", () -> authenticate(false));
            if (next.equals("join") || next.equals("create")) button("Cancel setup", this::changeVault);
            if (next.equals("unlock") && biometric.available()) {
                if (biometric.configured()) button("Use biometrics", () -> { biometric.unlock(); });
                {
                    enableBiometric = new android.widget.CheckBox(this);
                    enableBiometric.setText(biometric.configured() ? "Replace biometric unlock for this vault" : "Enable biometric unlock for this vault");
                    enableBiometric.setSaveEnabled(false); content.addView(enableBiometric);
                }
            }
            password.requestFocus();
        } else if (next.equals("add")) {
            scan = button(getString(R.string.scan_qr), () -> {
                clearSecret();
                try { startActivity(new android.content.Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); }
                catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
                    status.setText(R.string.camera_unavailable);
                }
            });
            label(getString(R.string.scan_qr_help));
            issuer = textField("Issuer"); account = textField("Account / label");
            secret = passwordField("Base32 secret");
            label("Letters A–Z and digits 2–7, case insensitive. Spaces, tabs, line breaks and hyphens are ignored. Optional RFC 4648 padding.");
            algorithm = selector("Algorithm", new String[]{"SHA1", "SHA256", "SHA512"});
            digits = selector("Digits", new String[]{"6", "7", "8"});
            period = textField("Period (seconds)"); period.setInputType(InputType.TYPE_CLASS_NUMBER); period.setText("30");
            add = button("Add", this::submitToken);
            cancel = button("Cancel", this::cancelAdd);
        } else if (next.equals("open")) {
            EditText search = new EditText(this); search.setHint("Search tokens…");
            search.setContentDescription("Search tokens"); search.setSingleLine(true); search.setSaveEnabled(false);
            content.addView(search);
            tokens = new TokenListAdapter(this, this::tapToken, this::openTokenChange, controller::hideCode);
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                public void onTextChanged(CharSequence text, int start, int before, int count) { tokens.search(text.toString()); }
                public void afterTextChanged(Editable text) {}
            });
            TextView empty = label("No matching tokens");
            ListView list = new ListView(this); list.setSaveEnabled(false);
            list.setAdapter(tokens); list.setEmptyView(empty);
            content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            add = button("Add token", () -> {
                if (controller.snapshot().state() != State.OPEN) return;
                controller.hideCode(); adding = true; render(controller.snapshot());
            });
        } else if (!next.equals("manage")) {
            action = button("Retry", () -> {
                clearPasswords();
                if (controller.snapshot().state() == State.ERROR_LOCKED) controller.retryDiscovery(); else controller.lock();
            });
        }
    }
    private static void reserveSyncControlWidth(Button control) {
        // Measure the styled/transformed labels, including normal Button padding and minimums.
        // Reserve before the first layout so the title and overflow never move during Sync.
        control.setMaxLines(1);
        int width = 0;
        for (String label : new String[]{"Sync", "Syncing…"}) {
            control.setText(label);
            control.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            width = Math.max(width, control.getMeasuredWidth());
        }
        control.setMinWidth(width);
        control.setText("Sync");
        control.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
    }
    private static void renderSyncControl(Button control, String sync) {
        boolean active = "Syncing…".equals(sync);
        String label = active ? "Syncing…" : "Sync";
        if (!TextUtils.equals(control.getText(), label)) control.setText(label);
        String name = active ? "Syncing" : "Sync";
        if (!TextUtils.equals(control.getContentDescription(), name)) control.setContentDescription(name);
    }
    private String mainStatus(Snapshot state) {
        return mainStatus(state, controller.dailySyncStatus(), controller.tokenChangeResult());
    }
    static String mainStatus(Snapshot state, String sync, TokenChange.Result changeResult) {
        // Transient progress belongs to the existing Sync control, never a new status row.
        if ("Syncing…".equals(sync)) sync = "";
        String local = "";
        if (state.state() == State.BUSY) local = state.message();
        else if (state.view() != null && (!state.view().diagnostics().isEmpty() || !state.view().integrityProblems().isEmpty()))
            local = "Vault needs attention";
        else if (changeResult != null && changeResult != TokenChange.Result.SAVED)
            local = switch (changeResult) {
                case STALE -> "Token changed. Review it and try again.";
                case INVALID -> "Check issuer and account.";
                case PUBLICATION_UNCERTAIN -> "Local save needs attention";
                default -> "Change could not be saved";
            };
        else if (state.addOutcome() != null) local = switch (state.addOutcome().status()) {
            case ADDED -> "";
            case INVALID_SECRET -> "Check the Base32 secret.";
            case INVALID_FIELDS -> "Check token details.";
            case PUBLICATION_UNCERTAIN -> "Local save needs attention";
            case CONFLICT -> "Vault changed. Review the token list.";
            case SESSION_UNAVAILABLE -> "Unlock the vault to continue";
            case BUSY -> "Vault is busy";
            case FAILED -> "Token could not be added";
        };
        return local.isEmpty() ? sync : sync.isEmpty() ? local : local + "\n" + sync;
    }
    private void showOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Change Vault").setOnMenuItemClickListener(item -> { changeVault(); return true; });
        menu.getMenu().add("Rename Vault").setOnMenuItemClickListener(item -> {
            EditText field = new EditText(this); field.setSingleLine(true); field.setSaveEnabled(false);
            field.setHint("Vault name (optional)"); field.setContentDescription("Vault name");
            field.setText(application().selectedAssignedName(controller));
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Rename vault").setView(field)
                    .setNegativeButton("Cancel", null).setPositiveButton("Save", null).show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                if (application().saveSelectedName(controller, field.getText().toString())) {
                    dialog.dismiss(); surface = null; render(controller.snapshot());
                } else field.setError(application().managementNotice());
            });
            return true;
        });
        menu.getMenu().add("Manage sync folder").setOnMenuItemClickListener(item -> {
            controller.hideCode(); managing = true; adding = false; surface = null; render(controller.snapshot()); return true;
        });
        menu.getMenu().add("Diagnostics").setOnMenuItemClickListener(item -> {
            controller.hideCode();
            new AlertDialog.Builder(this).setTitle("Diagnostics").setMessage(controller.diagnostics() + "\nApp: " + installedVersion())
                    .setPositiveButton("Close", null).show(); return true;
        });
        if (controller.snapshot().state() == State.OPEN)
            menu.getMenu().add("Disable biometric unlock").setOnMenuItemClickListener(item -> {
                if (!biometric.disable()) status.setText("Biometric unlock could not be disabled. Try again."); return true;
            });
        menu.getMenu().add("Lock").setEnabled(controller.snapshot().state() == State.OPEN)
                .setOnMenuItemClickListener(item -> { managing = false; clearPasswords(); controller.lock(); return true; });
        menu.show();
    }
    private String installedVersion() {
        try {
            var info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return (info.versionName == null || info.versionName.isEmpty() ? "Unavailable" : info.versionName)
                    + " (" + (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode) + ")";
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return "Unavailable";
        }
    }
    private void buildFolderManagement() {
        syncStatus = label("");
        chooseFolder = button("Choose sync folder", this::chooseSyncFolder);
        checkFolder = button("Retry access", () -> controller.checkSyncFolder());
        disconnectFolder = button("Disconnect sync folder", () -> controller.disconnectSyncFolder());
        joinVault = button("Join existing vault", () -> { managing = false; joining = true; controller.prepareJoin(); render(controller.snapshot()); });
        initializeFolder = button("Initialize sync folder", () -> controller.initializeSyncFolder());
        label("Totipo reads and writes encrypted vault data in the selected folder. Join opens an existing vault on this device. Initialize sets up an empty sync folder for this vault; use Sync to publish local changes. These setup actions never replace an existing vault. Disconnect removes the folder association and keeps the local vault and folder files.");
    }
    private void dismissTokenChange() {
        TokenChange previous = tokenChange; tokenChange = null;
        if (previous != null) controller.cancelTokenChange(previous);
        if (tokenDialog != null) { tokenDialog.dismiss(); tokenDialog = null; }
    }
    private void openTokenChange(org.totipo.TokenId id, TokenChange.Kind kind) {
        var change = controller.beginTokenChange(id, kind);
        if (change == null) return;
        tokenChange = change;
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setNegativeButton("Cancel", (dialog, which) -> dismissTokenChange());
        if (kind == TokenChange.Kind.DELETE) {
            builder.setTitle("Delete this token?").setMessage("This removes the token from the current vault state. Historical encrypted revisions may remain in local vault storage and the sync folder.")
                    .setPositiveButton("Delete", (dialog, which) -> { controller.confirmTokenChange(change, null, null, 0); dismissTokenChange(); });
        } else if (kind == TokenChange.Kind.EDIT) {
            var descriptor = change.basis().alternatives().get(0);
            LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(24, 8, 24, 8);
            fields.setSaveEnabled(false); fields.setSaveFromParentEnabled(false);
            EditText editIssuer = metadataField(fields, "Issuer", descriptor.issuer());
            EditText editAccount = metadataField(fields, "Account / label", descriptor.account());
            TextView setup = new TextView(this); setup.setText(descriptor.algorithm() + " · " + descriptor.digits()
                    + " digits · " + descriptor.period().getSeconds() + " s\nCurrent setup retained. Changing setup is not available yet."); fields.addView(setup);
            builder.setTitle("Edit token").setView(fields).setPositiveButton("Save", (dialog, which) -> {
                controller.confirmTokenChange(change, editIssuer.getText().toString(), editAccount.getText().toString(), 0); dismissTokenChange();
            });
        } else {
            String[] options = java.util.stream.IntStream.range(0, change.basis().alternatives().size())
                    .mapToObj(i -> "Option " + (i + 1) + "\n" + TokenListAdapter.summary(change.basis().alternatives().get(i))).toArray(String[]::new);
            int[] selectedOption = {-1};
            builder.setTitle("Resolve conflict").setSingleChoiceItems(options, -1, (dialog, which) -> {
                selectedOption[0] = which; ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            }).setPositiveButton("Resolve", (dialog, which) -> {
                controller.confirmTokenChange(change, null, null, selectedOption[0]); dismissTokenChange();
            });
        }
        tokenDialog = builder.create();
        tokenDialog.setOnCancelListener(dialog -> dismissTokenChange());
        tokenDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        tokenDialog.getWindow().setCallback(new InteractionWindow(tokenDialog.getWindow().getCallback(), controller::userInteraction));
        tokenDialog.show();
        if (kind == TokenChange.Kind.RESOLVE) tokenDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        if (kind == TokenChange.Kind.DELETE) tokenDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.rgb(176, 0, 32));
    }
    private EditText metadataField(LinearLayout parent, String title, String value) {
        TextView label = new TextView(this); label.setText(title); parent.addView(label);
        EditText field = new EditText(this); field.setId(View.generateViewId()); label.setLabelFor(field.getId());
        field.setSaveEnabled(false); field.setSaveFromParentEnabled(false); field.setFreezesText(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); field.setText(value); parent.addView(field); return field;
    }
    private void chooseSyncFolder() {
        if (!controller.canManageSyncFolder()) return;
        controller.hideCode();
        android.content.Intent picker = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
        picker.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        String previous = controller.initialTreeUri();
        if (previous != null) picker.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, android.net.Uri.parse(previous));
        pickerActivation = controller.activationId();
        try { startActivityForResult(picker, SYNC_TREE_REQUEST); }
        catch (android.content.ActivityNotFoundException unavailable) { status.setText("Folder picker unavailable."); }
    }
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        String origin = pickerActivation;
        if (request == SYNC_TREE_REQUEST) pickerActivation = null;
        if (request == SYNC_TREE_REQUEST && controller != null && controller.selectedAuthority()
                && controller.activationId().equals(origin) && !replacing) {
            controller.chooseSyncFolder(result != RESULT_OK, data == null || data.getData() == null ? null : data.getData().toString(),
                    data == null ? 0 : data.getFlags());
            if (joining && result == RESULT_OK && data != null && data.getData() != null)
                application().suggestJoinName(controller, data.getData());
        }
    }
    private void clearSecret() { if (secret != null) secret.setText(""); }
    private void cancelAdd() { clearSecret(); adding = submitted = false; render(controller.snapshot()); }
    // Legacy API 30–32 path; API 33+ registers the platform predictive-back callback above.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { navigateBack(); }
    private void navigateBack() {
        if (controller == null) {
            finish();
            return;
        }
        if (!controller.selectedAuthority()) { finish(); return; }
        if (joining || "create".equals(surface)) { changeVault(); return; }
        if (managing) { managing = false; surface = null; render(controller.snapshot()); return; }
        if (adding) { if (controller.snapshot().state() == State.OPEN) cancelAdd(); return; }
        finish();
    }
    private void submitToken() {
        if (submitted || controller.snapshot().state() != State.OPEN) return;
        long seconds;
        try {
            seconds = Long.parseLong(period.getText().toString());
            if (seconds < 1 || seconds > 4294967295L) throw new NumberFormatException();
        } catch (NumberFormatException invalid) { period.setError("Enter whole seconds from 1 to 4294967295."); return; }
        if (secret.length() == 0) { secret.setError("Enter a Base32 secret."); return; }
        // Copy at submission, never via immutable String; worker takes exclusive ownership.
        char[] input = new char[secret.length()];
        secret.getText().getChars(0, input.length, input, 0);
        clearSecret();
        var request = new AddTokenRequest(issuer.getText().toString(), account.getText().toString(),
                org.totipo.TotpAlgorithm.values()[algorithm.getSelectedItemPosition()],
                6 + digits.getSelectedItemPosition(), seconds, input);
        submitted = controller.addToken(request);
    }
    private EditText textField(String title) {
        return metadataField(content, title, "");
    }
    private Spinner selector(String title, String[] options) {
        TextView titleView = label(title);
        Spinner field = new Spinner(this); field.setId(View.generateViewId()); titleView.setLabelFor(field.getId());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); field.setAdapter(adapter);
        field.setSaveEnabled(false); content.addView(field); return field;
    }
    private TextView label(String text) {
        TextView label = new TextView(this); label.setText(text); label.setTextSize(16);
        content.addView(label); return label;
    }
    private EditText passwordField(String title) {
        TextView label = label(title);
        EditText field = new EditText(this); field.setId(View.generateViewId()); label.setLabelFor(field.getId());
        field.setSingleLine(true); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSaveEnabled(false); field.setSaveFromParentEnabled(false); field.setFreezesText(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(field); return field;
    }
    private Button button(String title, Runnable command) {
        return buttonIn(content, title, command);
    }
    private Button buttonIn(LinearLayout parent, String title, Runnable command) {
        Button button = new Button(this); button.setText(title);
        button.setOnClickListener(ignored -> command.run()); parent.addView(button); return button;
    }
    private void authenticate(boolean emptyConfirmed) {
        boolean create = surface.equals("create");
        if (controller.snapshot().state() != (create || surface.equals("join") ? State.NO_LOCAL_VAULT : State.LOCKED)) return;
        if (create && !TextUtils.equals(password.getText(), confirmation.getText())) {
            confirmation.setError("Passwords do not match"); confirmation.requestFocus(); return;
        }
        if (password.length() == 0 && !emptyConfirmed) {
            new AlertDialog.Builder(this).setTitle("Use an empty password?")
                    .setMessage("An empty password provides no password protection. Anyone with this vault can unlock it.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(create ? "Create Vault" : "Unlock", (dialog, which) -> authenticate(true)).show();
            return;
        }
        if ((create || surface.equals("join")) && !application().saveSelectedName(controller, application().setupName(controller).value())) {
            vaultName.setError(application().managementNotice()); return;
        }
        // Copy only at submission; controller takes exclusive ownership and clears even rejection.
        char[] credential = new char[password.length()];
        password.getText().getChars(0, credential.length, credential, 0);
        boolean accepted = surface.equals("join") ? controller.joinExistingVault(credential) : create ? controller.create(credential) : enableBiometric != null && enableBiometric.isChecked()
                ? controller.unlockAndEnroll(credential, enrollment -> {
                    if (resumed) biometric.enroll(enrollment); else enrollment.password().close();
                }) : controller.unlock(credential);
        if (accepted) clearPasswords();
        if (accepted) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(password.getWindowToken(), 0);
        }
    }
    private void clearPasswords() {
        if (password != null) password.getText().clear();
        if (confirmation != null) confirmation.getText().clear();
    }
    private void tapToken(org.totipo.TokenId id) {
        var shown = controller.snapshot().revealedCode();
        if (shown != null && shown.tokenId().equals(id)) {
            android.widget.Toast.makeText(this,
                    controller.copyShownCode() ? "Code copied" : "Code could not be copied", android.widget.Toast.LENGTH_SHORT).show();
        } else controller.showCode(id);
    }
    private void clearCodeWidgets() {
        if (tokens != null) tokens.clearWidgets();
    }
    @Override public Object onRetainNonConfigurationInstance() { return pickerActivation; }
    @Override protected void onDestroy() { clearPasswords(); clearCodeWidgets(); super.onDestroy(); }
}
