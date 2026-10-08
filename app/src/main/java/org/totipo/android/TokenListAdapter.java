package org.totipo.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.function.Consumer;
import org.totipo.TokenId;
import org.totipo.TokenStatus;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;

/** Complete detached list, constant-size recycled rows. No Java ownership or live codes. */
final class TokenListAdapter extends BaseAdapter {
    private final Context context;
    private final Consumer<TokenId> reveal;
    private List<ObservedToken> tokens = List.of();
    private boolean enabled;
    TokenListAdapter(Context context, Consumer<TokenId> reveal) { this.context = context; this.reveal = reveal; }
    void replace(List<ObservedToken> values, boolean enabled) {
        if (tokens == values && this.enabled == enabled) return;
        tokens = values; this.enabled = enabled; notifyDataSetChanged();
    }
    public int getCount() { return tokens.size(); }
    public ObservedToken getItem(int position) { return tokens.get(position); }
    // IDs are 256-bit; do not compress them into colliding Android stable long identities.
    public long getItemId(int position) { return position; }
    public boolean areAllItemsEnabled() { return false; }
    public boolean isEnabled(int position) { return enabled && usable(getItem(position)); }
    static boolean usable(ObservedToken token) {
        return !token.conflict() && token.unresolved().isEmpty() && token.alternatives().size() == 1
                && token.alternatives().get(0).status() == TokenStatus.ACTIVE;
    }
    static String rowText(ObservedToken token) {
        if (token.alternatives().size() != 1) return "Token\nNeeds attention";
        var descriptor = token.alternatives().get(0);
        return descriptor.issuer() + "\n" + descriptor.account() + "\n"
                + (usable(token) ? "Active" : "Needs attention");
    }
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        if (convertView == null) {
            row = new LinearLayout(context); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 12, 16, 12); row.setSaveEnabled(false);
            TextView description = new TextView(context); description.setSaveEnabled(false);
            Button show = new Button(context); show.setText("Show code"); show.setSaveEnabled(false);
            row.addView(description); row.addView(show);
        } else row = (LinearLayout) convertView;
        ObservedToken token = getItem(position);
        ((TextView) row.getChildAt(0)).setText(rowText(token));
        Button show = (Button) row.getChildAt(1);
        show.setEnabled(isEnabled(position));
        show.setOnClickListener(ignored -> reveal.accept(token.id()));
        return row;
    }
}
