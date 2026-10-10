package org.totipo.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.PopupMenu;
import java.util.List;
import java.util.function.Consumer;
import org.totipo.TokenId;
import org.totipo.TokenStatus;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;

/** Complete detached list, constant-size recycled rows. No Java ownership or live codes. */
final class TokenListAdapter extends BaseAdapter {
    private final Context context;
    private final Consumer<TokenId> reveal;
    private final java.util.function.BiConsumer<TokenId, TokenChange.Kind> change;
    private List<ObservedToken> tokens = List.of();
    private boolean enabled;
    TokenListAdapter(Context context, Consumer<TokenId> reveal, java.util.function.BiConsumer<TokenId, TokenChange.Kind> change) {
        this.context = context; this.reveal = reveal; this.change = change;
    }
    void replace(List<ObservedToken> values, boolean enabled) {
        if (tokens == values && this.enabled == enabled) return;
        tokens = values.stream().filter(t -> t.conflict() || !t.unresolved().isEmpty()
                || t.alternatives().stream().anyMatch(d -> d.status() == TokenStatus.ACTIVE)).collect(java.util.stream.Collectors.toList());
        this.enabled = enabled; notifyDataSetChanged();
    }
    public int getCount() { return tokens.size(); }
    public ObservedToken getItem(int position) { return tokens.get(position); }
    // IDs are 256-bit; do not compress them into colliding Android stable long identities.
    public long getItemId(int position) { return position; }
    public boolean areAllItemsEnabled() { return false; }
    public boolean isEnabled(int position) { return enabled && (usable(getItem(position)) || TokenChange.resolvable(getItem(position))); }
    static boolean usable(ObservedToken token) {
        return !token.conflict() && token.unresolved().isEmpty() && token.alternatives().size() == 1
                && token.alternatives().get(0).status() == TokenStatus.ACTIVE;
    }
    static String rowText(ObservedToken token) {
        if (token.conflict()) return "Token conflict\n" + token.alternatives().stream()
                .map(TokenListAdapter::summary).distinct().collect(java.util.stream.Collectors.joining("\n"));
        if (token.alternatives().size() != 1) return "Token\nNeeds attention";
        var descriptor = token.alternatives().get(0);
        return descriptor.issuer() + "\n" + descriptor.account() + "\n"
                + (usable(token) ? "Active" : "Needs attention");
    }
    static String summary(org.totipo.TokenDescriptor value) {
        if (value.status() == TokenStatus.TOMBSTONED) return "Deleted";
        return value.issuer() + " — " + value.account() + "\n" + value.algorithm()
                + " · " + value.digits() + " digits · " + value.period().getSeconds() + " s";
    }
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout row;
        if (convertView == null) {
            row = new LinearLayout(context); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(16, 12, 16, 12); row.setSaveEnabled(false);
            TextView description = new TextView(context); description.setSaveEnabled(false);
            LinearLayout actions = new LinearLayout(context);
            Button show = new Button(context); show.setText("Show code"); show.setSaveEnabled(false);
            Button more = new Button(context); more.setText("⋮"); more.setSaveEnabled(false);
            actions.addView(show); actions.addView(more);
            row.addView(description); row.addView(actions);
        } else row = (LinearLayout) convertView;
        ObservedToken token = getItem(position);
        ((TextView) row.getChildAt(0)).setText(rowText(token));
        LinearLayout actions = (LinearLayout) row.getChildAt(1);
        Button show = (Button) actions.getChildAt(0), more = (Button) actions.getChildAt(1);
        show.setText(token.conflict() ? "Resolve" : "Show code");
        show.setEnabled(enabled && (token.conflict() ? TokenChange.resolvable(token) : usable(token)));
        show.setOnClickListener(ignored -> { if (token.conflict()) change.accept(token.id(), TokenChange.Kind.RESOLVE); else reveal.accept(token.id()); });
        more.setVisibility(usable(token) ? View.VISIBLE : View.GONE); more.setEnabled(enabled);
        more.setContentDescription("Token actions: Edit or Delete");
        more.setOnClickListener(ignored -> {
            PopupMenu menu = new PopupMenu(context, more);
            menu.getMenu().add("Edit").setOnMenuItemClickListener(item -> { change.accept(token.id(), TokenChange.Kind.EDIT); return true; });
            menu.getMenu().add("Delete…").setOnMenuItemClickListener(item -> { change.accept(token.id(), TokenChange.Kind.DELETE); return true; });
            menu.show();
        });
        return row;
    }
}
