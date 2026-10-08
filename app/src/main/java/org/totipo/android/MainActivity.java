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
    private String surface;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        controller = ((TotipoApplication) getApplication()).vaultController();
        if (controller == null) {
            TextView unsupported = new TextView(this);
            unsupported.setText(R.string.unsupported_runtime);
            unsupported.setPadding(24, 80, 24, 24); setContentView(unsupported);
        } else render(controller.snapshot());
    }
    @Override protected void onStart() {
        super.onStart();
        if (controller != null) { render(controller.snapshot()); controller.attach(listener); }
    }
    @Override protected void onStop() { if (controller != null) controller.detach(listener); super.onStop(); }
    // No session closure on Activity stop/destruction. No credential Bundle or saved widget state.
    private void render(Snapshot state) {
        String next = switch (state.state()) {
            case NO_LOCAL_VAULT, CREATING -> "create";
            case LOCKED, UNLOCKING -> "unlock";
            case OPEN, BUSY -> "open";
            default -> "status";
        };
        if (!next.equals(surface)) build(next);
        status.setText(state.message());
        if (action != null) {
            action.setEnabled(state.state() == State.NO_LOCAL_VAULT || state.state() == State.LOCKED
                    || state.state() == State.ERROR_LOCKED || state.state() == State.FAILED_CLOSE || state.state() == State.ERROR_OPEN);
            if (next.equals("status")) {
                action.setText(state.state() == State.ERROR_LOCKED ? "Retry storage check" : "Retry Lock");
                action.setVisibility(state.state() == State.ERROR_LOCKED || state.state() == State.FAILED_CLOSE
                        || state.state() == State.ERROR_OPEN ? View.VISIBLE : View.GONE);
            }
        }
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
        clearCodeWidgets();
        code = remaining = selected = null; tokens = null; revealPanel = null;
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
        if (next.equals("create") || next.equals("unlock")) {
            label(next.equals("create") ? "Create Vault" : "Unlock Vault").setTextSize(22);
            password = passwordField("Password");
            if (next.equals("create")) confirmation = passwordField("Confirm password");
            action = button(next.equals("create") ? "Create Vault" : "Unlock", () -> authenticate(false));
            password.requestFocus();
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
            refresh = buttonIn(vaultActions, "Refresh", () -> controller.refresh());
            lock = buttonIn(vaultActions, "Lock", () -> { clearPasswords(); controller.lock(); });
        } else {
            action = button("Retry", () -> {
                clearPasswords();
                if (controller.snapshot().state() == State.ERROR_LOCKED) controller.retryDiscovery(); else controller.lock();
            });
        }
    }
    private TextView label(String text) {
        TextView label = new TextView(this); label.setText(text); label.setTextSize(16);
        content.addView(label); return label;
    }
    private EditText passwordField(String title) {
        TextView label = label(title);
        EditText field = new EditText(this); field.setId(View.generateViewId()); label.setLabelFor(field.getId());
        field.setSingleLine(true); field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSaveEnabled(false); field.setFreezesText(false);
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
