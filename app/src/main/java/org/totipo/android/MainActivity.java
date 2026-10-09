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
import org.totipo.android.AndroidVaultController.Snapshot;
import org.totipo.android.AndroidVaultController.State;

/** Presentation only: application controller commands and detached snapshots. */
public final class MainActivity extends Activity {
    private AndroidVaultController controller;
    private final AndroidVaultController.Listener listener = this::render;
    private LinearLayout content;
    private TextView status, code, remaining, selected;
    private LinearLayout revealPanel;
    private TokenListAdapter tokens;
    private org.totipo.TokenId selectedId;
    private EditText password, confirmation;
    private Button action, refresh, lock;
    private TextView syncStatus;
    private Button chooseFolder, importChanges, disconnectFolder, checkFolder;
    private static final int SYNC_TREE_REQUEST = 310;
    private String surface;
    private boolean adding, submitted;
    private EditText issuer, account, secret, period;
    private Spinner algorithm, digits;
    private Button add, cancel, scan;


    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        controller = ((TotipoApplication) getApplication()).vaultController();
        if (controller == null) {
            TextView unsupported = new TextView(this);
            unsupported.setText(R.string.unsupported_runtime);
            unsupported.setPadding(24, 80, 24, 24); setContentView(unsupported);
        } else {
            if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
            render(controller.snapshot());
        }
    }
    @Override protected void onStart() {
        super.onStart();
        if (controller != null) { render(controller.snapshot()); controller.attach(listener); }
    }
    @Override protected void onStop() { clearSecret(); if (controller != null) controller.detach(listener); super.onStop(); }
    // No session closure on Activity stop/destruction. No credential Bundle or saved widget state.
    private void render(Snapshot state) {
        if (submitted && state.state() == State.OPEN && state.addOutcome() != null) {
            submitted = false;
            if (state.addOutcome().status() == AddTokenOutcome.Status.ADDED
                    || state.addOutcome().status() == AddTokenOutcome.Status.PUBLICATION_UNCERTAIN) {
                clearSecret(); adding = false;
            }
        }
        if (state.state() != State.OPEN && state.state() != State.BUSY) { clearSecret(); adding = submitted = false; }

        String next = switch (state.state()) {
            case NO_LOCAL_VAULT, CREATING -> "create";
            case LOCKED, UNLOCKING -> "unlock";
            case OPEN, BUSY -> adding ? "add" : "open";
            default -> "status";
        };
        if (!next.equals(surface)) build(next);
        status.setText(state.message());
        var sync = controller.syncView();
        syncStatus.setText(sync.message() + (state.state() == State.LOCKED
                ? " Unlock Totipo to import changes." : ""));
        boolean configured = sync.binding().status() != org.totipo.android.sync.SyncFolderBinding.Status.NOT_CONFIGURED;
        chooseFolder.setText(configured ? "Change folder" : "Choose folder");
        if (sync.binding().status() == org.totipo.android.sync.SyncFolderBinding.Status.ACCESS_LOST)
            chooseFolder.setText("Choose folder again");
        chooseFolder.setEnabled(controller.canManageSyncFolder());
        importChanges.setEnabled(controller.canImportProviderChanges());
        disconnectFolder.setVisibility(configured ? View.VISIBLE : View.GONE);
        disconnectFolder.setEnabled(controller.canManageSyncFolder());
        checkFolder.setVisibility(configured ? View.VISIBLE : View.GONE);
        checkFolder.setEnabled(controller.canManageSyncFolder());
        if (action != null) {
            action.setEnabled(state.state() == State.NO_LOCAL_VAULT || state.state() == State.LOCKED
                    || state.state() == State.ERROR_LOCKED || state.state() == State.FAILED_CLOSE || state.state() == State.ERROR_OPEN);
            if (next.equals("status")) {
                action.setText(state.state() == State.ERROR_LOCKED ? "Retry storage check" : "Retry Lock");
                action.setVisibility(state.state() == State.ERROR_LOCKED || state.state() == State.FAILED_CLOSE
                        || state.state() == State.ERROR_OPEN ? View.VISIBLE : View.GONE);
            }
        }
        if (next.equals("add")) {
            boolean enabled = state.state() == State.OPEN;
            for (View field : new View[]{issuer, account, secret, period, algorithm, digits, add, cancel, scan}) field.setEnabled(enabled);
        }
        if (add != null && next.equals("open")) add.setEnabled(state.state() == State.OPEN);
        if (refresh != null) {
            refresh.setEnabled(state.state() == State.OPEN); lock.setEnabled(state.state() == State.OPEN);
            var view = state.view();
            tokens.replace(view == null ? java.util.List.of() : view.tokens(), state.state() == State.OPEN);
            var shown = state.revealedCode();
            revealPanel.setVisibility(shown == null ? View.GONE : View.VISIBLE);
            String formatted = shown == null ? "" : grouped(shown.code());
            if (!TextUtils.equals(code.getText(), formatted)) code.setText(formatted);
            remaining.setText(shown == null ? "" : state.remainingSeconds() + " s");
            if (shown == null) { selected.setText(""); selectedId = null; }
            if (shown != null && view != null && !shown.tokenId().equals(selectedId)) {
                selectedId = shown.tokenId();
                for (var token : view.tokens()) if (token.id().equals(shown.tokenId())
                        && token.alternatives().size() == 1) {
                    var descriptor = token.alternatives().get(0);
                    selected.setText(descriptor.issuer() + " — " + descriptor.account()); break;
                }
            }
        }
    }
    static String grouped(String value) {
        int split = (value.length() + 1) / 2;
        return value.substring(0, split) + " " + value.substring(split);
    }

    private void build(String next) {
        clearPasswords();
        clearSecret();
        clearCodeWidgets();
        code = remaining = selected = null; tokens = null; revealPanel = null;
        issuer = account = secret = period = null; algorithm = digits = null; add = cancel = scan = null;
        surface = next; password = confirmation = null; action = refresh = lock = null;
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
        label("Totipo").setTextSize(28);
        status = label(""); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        label("Sync folder").setTextSize(20);
        syncStatus = label("");
        label("Totipo currently imports token changes from this folder.");
        LinearLayout folderActions = new LinearLayout(this); content.addView(folderActions);
        chooseFolder = buttonIn(folderActions, "Choose folder", this::chooseSyncFolder);
        importChanges = buttonIn(folderActions, "Import changes", () -> controller.importProviderChanges());
        LinearLayout folderSettings = new LinearLayout(this); content.addView(folderSettings);
        checkFolder = buttonIn(folderSettings, "Retry access", () -> controller.checkSyncFolder());
        disconnectFolder = buttonIn(folderSettings, "Disconnect", () -> controller.disconnectSyncFolder());
        if (next.equals("create") || next.equals("unlock")) {
            label(next.equals("create") ? "Create Vault" : "Unlock Vault").setTextSize(22);
            password = passwordField("Password");
            if (next.equals("create")) confirmation = passwordField("Confirm password");
            action = button(next.equals("create") ? "Create Vault" : "Unlock", () -> authenticate(false));
            password.requestFocus();
        } else if (next.equals("add")) {
            label("Add token").setTextSize(22);
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
            tokens = new TokenListAdapter(this, id -> controller.showCode(id));
            TextView empty = label("No tokens yet");
            ListView list = new ListView(this); list.setSaveEnabled(false);
            list.setAdapter(tokens); list.setEmptyView(empty);
            content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            revealPanel = new LinearLayout(this); revealPanel.setOrientation(LinearLayout.VERTICAL);
            revealPanel.setSaveEnabled(false); revealPanel.setSaveFromParentEnabled(false);
            selected = new TextView(this); code = new TextView(this); remaining = new TextView(this);
            for (TextView text : new TextView[] {selected, code, remaining}) {
                text.setSaveEnabled(false); text.setFreezesText(false);
                text.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
                text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
                revealPanel.addView(text);
            }
            code.setTextSize(32);
            LinearLayout revealActions = new LinearLayout(this); revealPanel.addView(revealActions);
            buttonIn(revealActions, "Hide code", () -> controller.hideCode());
            buttonIn(revealActions, "Copy code", () -> controller.copyShownCode());
            revealPanel.setVisibility(View.GONE); content.addView(revealPanel);
            LinearLayout vaultActions = new LinearLayout(this); content.addView(vaultActions);
            add = button("Add token", () -> {
                if (controller.snapshot().state() != State.OPEN) return;
                controller.hideCode(); adding = true; render(controller.snapshot());
            });
            refresh = buttonIn(vaultActions, "Refresh", () -> controller.refresh());
            lock = buttonIn(vaultActions, "Lock", () -> { clearPasswords(); controller.lock(); });
        } else {
            action = button("Retry", () -> {
                clearPasswords();
                if (controller.snapshot().state() == State.ERROR_LOCKED) controller.retryDiscovery(); else controller.lock();
            });
        }
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
        try { startActivityForResult(picker, SYNC_TREE_REQUEST); }
        catch (android.content.ActivityNotFoundException unavailable) { syncStatus.setText("Folder picker unavailable."); }
    }
    @Override protected void onActivityResult(int request, int result, android.content.Intent data) {
        super.onActivityResult(request, result, data);
        if (request == SYNC_TREE_REQUEST && controller != null) controller.chooseSyncFolder(
                result != RESULT_OK, data == null || data.getData() == null ? null : data.getData().toString(),
                data == null ? 0 : data.getFlags());
    }
    private void clearSecret() { if (secret != null) secret.setText(""); }
    private void cancelAdd() { clearSecret(); adding = submitted = false; render(controller.snapshot()); }
    // Legacy API 30–32 path; API 33+ registers the platform predictive-back callback above.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { navigateBack(); }
    private void navigateBack() {
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
        TextView titleView = label(title);
        EditText field = new EditText(this); field.setId(View.generateViewId()); titleView.setLabelFor(field.getId());
        field.setSaveEnabled(false); field.setSaveFromParentEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        content.addView(field); return field;
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
        // Copy only at submission; controller takes exclusive ownership and clears even rejection.
        char[] credential = new char[password.length()];
        password.getText().getChars(0, credential.length, credential, 0);
        boolean accepted = create ? controller.create(credential) : controller.unlock(credential);
        if (accepted) {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(password.getWindowToken(), 0);
        }
    }
    private void clearPasswords() {
        if (password != null) password.getText().clear();
        if (confirmation != null) confirmation.getText().clear();
    }
    private void clearCodeWidgets() {
        if (code != null) code.setText("");
        if (remaining != null) remaining.setText("");
        if (selected != null) selected.setText("");
        if (tokens != null) tokens.replace(java.util.List.of(), false);
        selectedId = null;
    }
    @Override protected void onDestroy() { clearPasswords(); clearCodeWidgets(); super.onDestroy(); }
}
