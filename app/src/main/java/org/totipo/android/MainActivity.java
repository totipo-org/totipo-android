package org.totipo.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Launch-only M0 bootstrap; no vault or product behavior. */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView label = new TextView(this);
        label.setText(R.string.bootstrap_message);
        label.setGravity(Gravity.CENTER);
        setContentView(label);
    }
}
