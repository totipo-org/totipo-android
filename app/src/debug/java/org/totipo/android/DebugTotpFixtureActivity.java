package org.totipo.android;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.totipo.*;
import org.totipo.android.reconcile.DebugTotpFixture;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;

/** Debug-only opt-in fixture and actual Android adapter checks. Never accepts credentials. */
public final class DebugTotpFixtureActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        TextView status = new TextView(this); panel.addView(status);
        try { checkAdapter(); status.setText("Adapter checks passed. Use a disposable vault only. Lock before arming."); }
        catch (RuntimeException | AssertionError failed) { status.setText("Adapter checks failed."); }
        Button seed = new Button(this); seed.setText("Arm public RFC test fixture for next unlock/create");
        seed.setOnClickListener(ignored -> {
            var controller = ((TotipoApplication) getApplication()).vaultController();
            if (controller == null || (controller.snapshot().state() != AndroidVaultController.State.LOCKED
                    && controller.snapshot().state() != AndroidVaultController.State.NO_LOCAL_VAULT)) {
                status.setText("Lock the disposable vault first."); return;
            }
            DebugTotpFixture.arm();
            startActivity(new Intent(this, MainActivity.class)); finish();
        });
        panel.addView(seed); setContentView(panel);
    }
    private void checkAdapter() {
        var clip = PlatformCodeClipboard.clip("test-marker", "public-placeholder");
        if (!clip.getDescription().getExtras().getBoolean("android.content.extra.IS_SENSITIVE")
                || !"test-marker".equals(clip.getDescription().getExtras().getString(PlatformCodeClipboard.OWNER)))
            throw new AssertionError();
        var adapter = new TokenListAdapter(this, ignored -> {}, (id, kind) -> {}, () -> {});
        if (adapter.getCount() != 0) throw new AssertionError();
        List<ObservedToken> entries = new ArrayList<>();
        for (int i = 0; i < 512; i++) entries.add(new ObservedToken(new TokenId(String.format("%064x", i)),
                List.of(new TokenDescriptor(TokenStatus.ACTIVE, "Fixture", "Account", TotpAlgorithm.SHA1,
                        6, Duration.ofSeconds(30))), List.of(), List.of(), false));
        adapter.replace(List.copyOf(entries), true);
        if (adapter.getCount() != 512) throw new AssertionError();
        LinearLayout parent = new LinearLayout(this);
        View row = adapter.getView(0, null, parent);
        for (int i = 1; i < 512; i++) if (adapter.getView(i, row, parent) != row) throw new AssertionError();
        if (((LinearLayout) row).getChildCount() != 2) throw new AssertionError();
        adapter.replace(List.of(), true);
        if (adapter.getCount() != 0) throw new AssertionError();
    }
}
