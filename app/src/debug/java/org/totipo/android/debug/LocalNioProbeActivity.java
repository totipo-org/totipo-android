package org.totipo.android.debug;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Disposable diagnostic launcher; never accepts a vault path or provider URI. */
public final class LocalNioProbeActivity extends Activity {
    private TextView output;
    private Button run;
    private volatile String report = "No local NIO qualification run yet.";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(),
                    padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(),
                    padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        run = new Button(this);
        run.setText("Run local NIO qualification");
        layout.addView(run);
        Button copy = new Button(this);
        copy.setText("Copy diagnostic report");
        layout.addView(copy);
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText(report);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);
        layout.requestApplyInsets();
        copy.setOnClickListener(v -> ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                .setPrimaryClip(ClipData.newPlainText("M1C local NIO qualification", report)));
        run.setOnClickListener(v -> startRun());
    }

    private void startRun() {
        run.setEnabled(false);
        output.setText("Running disposable local NIO qualification…");
        // Application context outlives Activity recreation; no real vault/provider inputs exist.
        Path base = getApplicationContext().getNoBackupFilesDir().toPath()
                .resolve("m1c-nio-qualification");
        new Thread(() -> {
            String result = LocalNioQualification.run(base);
            try {
                // Stable, private copy for exact adb retrieval. No secret or absolute path in report.
                Files.write(base.resolve("latest-report.txt"), result.getBytes(StandardCharsets.UTF_8));
            } catch (Exception | LinkageError failure) {
                result += "\nReport persistence failed: " + failure.getClass().getName();
            }
            report = result;
            runOnUiThread(() -> { output.setText(report); run.setEnabled(true); });
        }, "m1c-local-nio").start();
    }
}
