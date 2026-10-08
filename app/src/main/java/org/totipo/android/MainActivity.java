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
import android.widget.ScrollView;
import android.widget.TextView;
import org.totipo.android.AndroidVaultController.Snapshot;
import org.totipo.android.AndroidVaultController.State;

/** Presentation only: application controller commands and detached snapshots. */
public final class MainActivity extends Activity {
    private AndroidVaultController controller;
    private final AndroidVaultController.Listener listener = this::render;
    private LinearLayout content;
    private TextView status, details;
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
    @Override protected void onStart() { super.onStart(); if (controller != null) controller.attach(listener); }
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
            StringBuilder text = new StringBuilder();
            if (view != null) {
                text.append(view.tokens().size()).append(" tokens\n");
                text.append(view.observation() instanceof org.totipo.ObservationProgress.Finished
                        ? "Latest observation finished\n" : "Observation in progress\n");
                if (!view.diagnostics().isEmpty() || !view.integrityProblems().isEmpty()) {
                    text.append("Diagnostics: ").append(view.diagnostics().size())
                            .append("; integrity warnings: ").append(view.integrityProblems().size()).append('\n');
                }
                for (var token : view.tokens()) {
                    text.append('\n');
                    if (token.alternatives().isEmpty()) text.append("Unresolved token\n");
                    for (var descriptor : token.alternatives()) {
                        text.append(descriptor.issuer()).append(" — ").append(descriptor.account()).append('\n');
                        text.append(descriptor.status()).append('\n');
                    }
                    if (token.conflict()) text.append("Conflicting alternatives\n");
                    if (!token.unresolved().isEmpty()) text.append("Unresolved references: ").append(token.unresolved().size()).append('\n');
                }
                if (view.tokens().isEmpty()) text.append("No tokens observed. Token enrollment and rotating codes are deferred.");
            }
            details.setText(text.toString());
        }
    }
    private void build(String next) {
        clearPasswords();
        surface = next; password = confirmation = null; action = refresh = lock = null;
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        scroll.setFillViewport(true); scroll.addView(content); setContentView(scroll);
        label("Totipo").setTextSize(28);
        status = label(""); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (next.equals("create") || next.equals("unlock")) {
            label(next.equals("create") ? "Create Vault" : "Unlock Vault").setTextSize(22);
            password = passwordField("Password");
            if (next.equals("create")) confirmation = passwordField("Confirm password");
            action = button(next.equals("create") ? "Create Vault" : "Unlock", () -> authenticate(false));
            password.requestFocus();
        } else if (next.equals("open")) {
            label("Local vault open").setTextSize(22);
            details = label("");
            refresh = button("Refresh", () -> controller.refresh());
            lock = button("Lock", () -> { clearPasswords(); controller.lock(); });
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
        Button button = new Button(this); button.setText(title);
        button.setOnClickListener(ignored -> command.run()); content.addView(button); return button;
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
    @Override protected void onDestroy() { clearPasswords(); super.onDestroy(); }
}
