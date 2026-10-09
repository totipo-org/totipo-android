package org.totipo.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** External input ends here. Only M2B's controller can author; no session/editor access. */
public final class OtpAuthEnrollmentActivity extends Activity {
    private OtpAuthUriParser.Draft draft;
    private AndroidVaultController controller;
    private boolean submitted;
    private final AndroidVaultController.Listener listener = this::changed;
    private LinearLayout content;

    @Override protected void onCreate(Bundle savedState) {
        String input = null;
        if (savedState == null) input = consume(getIntent());
        else { OtpAuthTransport.clear(getIntent()); setIntent(new Intent()); }
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        controller = ((TotipoApplication) getApplication()).vaultController();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::cancel);
        if (savedState != null) message(R.string.enrollment_expired);
        else enroll(input);
    }
    @Override protected void onNewIntent(Intent incoming) {
        discard();
        String input = consume(incoming);
        super.onNewIntent(new Intent());
        submitted = false;
        enroll(input);
    }
    private String consume(Intent incoming) {
        try { return OtpAuthTransport.extract(incoming); }
        catch (RuntimeException malformed) { return null; }
        finally {
            OtpAuthTransport.clear(incoming);
            setIntent(new Intent());
        }
    }
    private void enroll(String input) {
        // Cheap unrelated-share rejection precedes any controller observation or draft construction.
        if (input == null || !input.regionMatches(true, 0, "otpauth://", 0, 10)) {
            message(R.string.enrollment_unsupported); return;
        }
        if (controller == null || !controller.canAddToken()) {
            message(unavailableMessage()); return;
        }
        try {
            draft = OtpAuthUriParser.parse(input);
            confirmation();
        } catch (OtpAuthUriParser.Rejected rejected) {
            discard();
            message(rejected.reason == OtpAuthUriParser.Reason.UNSUPPORTED
                    ? R.string.enrollment_unsupported : R.string.enrollment_invalid);
        } catch (RuntimeException failure) {
            discard(); message(R.string.enrollment_invalid);
        }
    }
    private int unavailableMessage() {
        if (controller != null && (controller.snapshot().state() == AndroidVaultController.State.BUSY
                || controller.snapshot().state() == AndroidVaultController.State.OPEN)) return R.string.enrollment_busy;
        return R.string.enrollment_unlock;
    }
    private void confirmation() {
        layout();
        label(getString(R.string.enrollment_label)).setTextSize(24);
        value("Issuer", draft.issuer());
        value("Account", draft.account());
        value("Algorithm", draft.algorithm.name());
        value("Digits", Integer.toString(draft.digits));
        value("Period (seconds)", Long.toString(draft.periodSeconds));
        button("Add", this::add);
        button("Cancel", this::cancel);
    }
    private void add() {
        if (draft == null || submitted) return;
        AddTokenRequest request = draft.transfer();
        discard();
        try {
            submitted = controller.addToken(request); // Owns request on accepted AND rejected admission.
            if (submitted) message(R.string.enrollment_adding);
            else message(unavailableMessage());
        } catch (RuntimeException failure) {
            request.close(); message(R.string.enrollment_unavailable);
        }
    }
    private void changed(AndroidVaultController.Snapshot state) {
        if (submitted) {
            if (state.state() == AndroidVaultController.State.BUSY) return;
            submitted = false;
            // The controller's M2B outcome wording is the sole publication policy.
            if (state.addOutcome() != null) {
                if (state.addOutcome().status() == AddTokenOutcome.Status.ADDED) { openTotipo(); return; }
                layout(); label(state.message()); button("Open Totipo", this::openTotipo);
                button("Close", this::cancel);
            } else message(R.string.enrollment_unavailable);
        } else if (draft != null && state.state() != AndroidVaultController.State.OPEN) {
            discard(); message(R.string.enrollment_unlock);
        }
    }
    private void openTotipo() {
        discard();
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }
    private void discard() { if (draft != null) { draft.close(); draft = null; } }
    private void cancel() { discard(); finish(); }
    @SuppressWarnings("deprecation")
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { cancel(); }
    private void message(int resource) {
        layout(); label(getString(resource));
        button("Open Totipo", this::openTotipo); button("Close", this::cancel);
    }
    private void layout() {
        // Drop old metadata widgets as well as their semantic owner on expiration/cancel.
        if (content != null) content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setSaveEnabled(false); scroll.setSaveFromParentEnabled(false);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        content.setSaveEnabled(false); content.setSaveFromParentEnabled(false);
        if (Build.VERSION.SDK_INT >= 30) scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        scroll.addView(content); setContentView(scroll);
    }
    private TextView label(String text) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(16);
        view.setSaveEnabled(false); view.setSaveFromParentEnabled(false); view.setFreezesText(false);
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); content.addView(view); return view;
    }
    private void value(String title, String text) {
        TextView name = label(title), value = label(text);
        value.setId(View.generateViewId()); value.setSingleLine(true); name.setLabelFor(value.getId());
    }
    private void button(String text, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setSaveEnabled(false);
        button.setOnClickListener(view -> action.run()); content.addView(button);
    }
    @Override protected void onStart() {
        super.onStart(); if (controller != null) controller.attach(listener);
    }
    @Override protected void onStop() {
        if (controller != null) controller.detach(listener);
        // No background draft retention. Returning requires scanning/sharing again.
        if (draft != null) { discard(); message(R.string.enrollment_expired); }
        super.onStop();
    }
    @Override protected void onDestroy() {
        discard(); if (content != null) content.removeAllViews(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (draft != null) { discard(); message(R.string.enrollment_expired); }
        state.putBoolean("consumed", true); // Non-secret recreation marker only; no framework/view saving.
    }
    @Override protected void onRestoreInstanceState(Bundle state) { }
    @Override public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("Totipo enrollment"); // Omit framework dump of externally supplied metadata.
    }
}
