package org.totipo.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
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

/** Detached list with one controller-owned, process-only code presentation. */
final class TokenListAdapter extends BaseAdapter {
    private final Context context;
    private final Consumer<TokenId> rowAction;
    private final java.util.function.BiConsumer<TokenId, TokenChange.Kind> change;
    private List<ObservedToken> tokens = List.of();
    private boolean enabled;
    private RevealedTotp shown;
    private Row shownRow;
    private long seconds;
    private final Runnable retire;
    private List<ObservedToken> source = List.of();
    private String query = "";
    void search(String text) {
        query = text.toLowerCase(java.util.Locale.ROOT).trim();
        boolean hadShown = shown != null;
        replace(source, enabled);
        // Also revoke an in-flight reveal whose result has not reached this adapter.
        if (!hadShown) retire.run();
    }
    TokenListAdapter(Context context, Consumer<TokenId> rowAction, java.util.function.BiConsumer<TokenId, TokenChange.Kind> change, Runnable retire) {
        this.context = context; this.rowAction = rowAction; this.change = change; this.retire = retire;
    }
    void replace(List<ObservedToken> values, boolean enabled) {
        source = values;
        tokens = values.stream().filter(t -> t.conflict() || !t.unresolved().isEmpty()
                || t.alternatives().stream().anyMatch(d -> d.status() == TokenStatus.ACTIVE)).filter(t -> matchesSearch(t, query)).collect(java.util.stream.Collectors.toList());
        this.enabled = enabled;
        if (shown != null && tokens.stream().noneMatch(t -> displays(t, shown))) {
            setShown(null); seconds = 0; retire.run();
        }
        notifyDataSetChanged();
    }
    static boolean matchesSearch(ObservedToken token, String text) {
        String query = text.toLowerCase(java.util.Locale.ROOT).trim();
        return query.isEmpty() || token.alternatives().stream().anyMatch(d ->
                (d.issuer() + " " + d.account()).toLowerCase(java.util.Locale.ROOT).contains(query));
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
        return descriptor.issuer() + "\n" + descriptor.account()
                + (usable(token) ? "" : "\nNeeds attention");
    }
    static String summary(org.totipo.TokenDescriptor value) {
        if (value.status() == TokenStatus.TOMBSTONED) return "Deleted";
        return value.issuer() + " — " + value.account() + "\n" + value.algorithm()
                + " · " + value.digits() + " digits · " + value.period().getSeconds() + " s";
    }
    static boolean displays(ObservedToken token, RevealedTotp code) {
        return code != null && usable(token) && token.id().equals(code.tokenId());
    }
    void replace(List<ObservedToken> values, boolean enabled, RevealedTotp code, long remaining) {
        setShown(code); seconds = remaining;
        replace(values, enabled);
    }
    private void setShown(RevealedTotp code) {
        if (!java.util.Objects.equals(shown, code) && shownRow != null) {
            shownRow.code.setText(""); shownRow.countdown.setText("");
            shownRow.ring.setRemaining(0, 0); shownRow.ring.setVisibility(View.GONE);
            shownRow.value.setVisibility(View.GONE); shownRow = null;
        }
        shown = code;
    }
    void clearWidgets() { setShown(null); seconds = 0; replace(List.of(), false); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    final class Row extends LinearLayout {
        final LinearLayout body, identity, value, countdownLine;
        final CountdownRingView ring;
        private long countdownPeriod = -1;
        private boolean ordinary;
        private final android.graphics.Paint codeMetrics = new android.graphics.Paint();
        final TextView issuer, account, code, countdown;
        final Button more, resolve;
        Row() {
            super(context);
            setOrientation(HORIZONTAL); setGravity(android.view.Gravity.CENTER_VERTICAL);
            setSaveEnabled(false); setSaveFromParentEnabled(false);
            body = new LinearLayout(context); body.setGravity(android.view.Gravity.CENTER_VERTICAL);
            body.setPadding(dp(8), dp(8), dp(8), dp(8)); body.setMinimumHeight(dp(56));
            body.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            addView(body, new LinearLayout.LayoutParams(0, -2, 1));
            identity = new LinearLayout(context); identity.setOrientation(VERTICAL);
            issuer = text(16); account = text(14);
            identity.addView(issuer); identity.addView(account);
            body.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
            value = new LinearLayout(context); value.setOrientation(VERTICAL);
            value.setGravity(android.view.Gravity.END); value.setPadding(dp(8), 0, 0, 0);
            code = text(24); code.setTypeface(android.graphics.Typeface.MONOSPACE); code.setIncludeFontPadding(false);
            code.setSingleLine(true); code.setHorizontallyScrolling(false);
            code.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            countdown = text(12); countdown.setIncludeFontPadding(false); countdown.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            countdown.setGravity(android.view.Gravity.RIGHT); countdown.setSingleLine(true);
            countdownLine = new LinearLayout(context); countdownLine.setGravity(android.view.Gravity.CENTER_VERTICAL);
            countdownLine.addView(countdown);
            ring = new CountdownRingView(context, countdown.getCurrentTextColor());
            var ringParams = new LinearLayout.LayoutParams(dp(18), dp(18)); ringParams.leftMargin = dp(4);
            countdownLine.addView(ring, ringParams);
            value.addView(code); value.addView(countdownLine);
            body.addView(value, new LinearLayout.LayoutParams(-2, -2));
            resolve = new Button(context); resolve.setText("Resolve"); resolve.setSaveEnabled(false);
            body.addView(resolve);
            more = new Button(context); more.setText("⋮"); more.setSaveEnabled(false);
            more.setMinWidth(0); more.setMinimumWidth(0); more.setPadding(0, 0, 0, 0);
            addView(more, new LinearLayout.LayoutParams(dp(48), dp(48)));
            more.setContentDescription("Token actions: Edit or Delete");
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            // Reserve the preferred two-line height in BOTH states, independently of
            // horizontal code fitting. GONE still returns all value width to identity.
            codeMetrics.set(code.getPaint());
            codeMetrics.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                    24, getResources().getDisplayMetrics()));
            body.setMinimumHeight(ordinary ? Math.max(dp(56), dp(16)
                    + (int) Math.ceil(codeMetrics.getFontSpacing())
                    + Math.max(dp(18), (int) Math.ceil(countdown.getPaint().getFontSpacing()))) : dp(56));
            if (value.getVisibility() == View.VISIBLE && MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
                // Reserve readable identity width even with a large system font. Fit the
                // full code by reducing its preferred size, never by clipping digits.
                float preferred = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                        24, getResources().getDisplayMetrics());
                float textWidth = codeMetrics.measureText(code.getText().toString());
                float available = Math.max(1, MeasureSpec.getSize(widthSpec) - dp(48 + 16 + 8 + 56));
                float size = preferred * Math.min(1, available / Math.max(1, textWidth));
                if (Math.abs(code.getTextSize() - size) > 0.1f)
                    code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
            }
            if (value.getVisibility() == View.VISIBLE) {
                int width = value.getPaddingLeft() + Math.max(
                        (int) Math.ceil(code.getPaint().measureText(code.getText().toString())),
                        countdown.getLayoutParams().width + dp(4 + 18));
                value.getLayoutParams().width = width;
            }
            super.onMeasure(widthSpec, heightSpec);
        }
        void countdownPeriod(long period) {
            if (countdownPeriod == period) return;
            countdownPeriod = period;
            countdown.getLayoutParams().width = countdownWidth(countdown.getPaint(), period);
            countdown.requestLayout();
        }
        private TextView text(int size) {
            TextView text = new TextView(context); text.setTextSize(size);
            text.setSaveEnabled(false); text.setFreezesText(false);
            text.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            text.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_NONE);
            return text;
        }
    }
    public View getView(int position, View convertView, ViewGroup parent) {
        Row row;
        if (convertView == null) row = new Row();
        else row = (Row) convertView;
        ObservedToken token = getItem(position);
        boolean revealed = displays(token, shown);
        row.ordinary = usable(token);
        boolean actionable = enabled && (usable(token) || TokenChange.resolvable(token));
        if (usable(token)) {
            row.issuer.setText(token.alternatives().get(0).issuer());
            row.account.setText(token.alternatives().get(0).account());
        } else row.issuer.setText(rowText(token));
        row.account.setVisibility(usable(token) ? View.VISIBLE : View.GONE);
        row.issuer.setSingleLine(usable(token)); row.account.setSingleLine(true);
        row.issuer.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.account.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (row == shownRow) shownRow = null;
        if (revealed) shownRow = row;
        String formatted = revealed ? MainActivity.grouped(shown.code()) : "";
        if (!android.text.TextUtils.equals(row.code.getText(), formatted)) row.code.setText(formatted);
        row.countdown.setText(revealed ? seconds + " s" : "");
        long period = revealed ? java.time.Duration.between(shown.validFrom(), shown.validUntil()).getSeconds() : 0;
        row.countdownPeriod(period);
        row.ring.setRemaining(revealed ? seconds : 0, period);
        row.ring.setVisibility(revealed ? View.VISIBLE : View.GONE);
        row.value.setVisibility(revealed ? View.VISIBLE : View.GONE);
        row.body.setEnabled(actionable);
        row.body.setContentDescription(rowText(token));
        String action = token.conflict() ? "Resolve" : revealed ? "Copy code" : "Reveal code";
        row.body.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (actionable) info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                        android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, action));
            }
        });
        row.body.setOnClickListener(ignored -> {
            if (!actionable) return;
            if (token.conflict()) change.accept(token.id(), TokenChange.Kind.RESOLVE);
            else rowAction.accept(token.id());
        });
        row.resolve.setVisibility(token.conflict() ? View.VISIBLE : View.GONE);
        row.resolve.setEnabled(enabled && TokenChange.resolvable(token));
        row.resolve.setOnClickListener(ignored -> change.accept(token.id(), TokenChange.Kind.RESOLVE));
        row.more.setVisibility(usable(token) ? View.VISIBLE : View.GONE); row.more.setEnabled(enabled);
        row.more.setOnClickListener(ignored -> overflow(row.more, token.id()).show());
        return row;
    }
    /** Measure valid decimal candidates, bounded by the period (also handles uint32 periods).
     * Decimal glyph advances are additive; measure complete candidates including the suffix.
     * Work is bounded by digit count, never by the number of seconds in a period.
     */
    static int countdownWidth(android.graphics.Paint paint, long period) {
        String maximum = Long.toString(Math.max(1, period));
        char widest = '0';
        for (char digit = '1'; digit <= '9'; digit++)
            if (paint.measureText(String.valueOf(digit)) > paint.measureText(String.valueOf(widest))) widest = digit;
        float width = paint.measureText(maximum + " s");
        for (int length = 1; length <= maximum.length(); length++) {
            String limit = length == maximum.length() ? maximum : "9".repeat(length);
            width = Math.max(width, paint.measureText(limit + " s"));
            for (int position = 0; position < length; position++)
                for (char digit = position == 0 ? '1' : '0'; digit < limit.charAt(position); digit++) {
                    String candidate = limit.substring(0, position) + digit
                            + String.valueOf(widest).repeat(length - position - 1) + " s";
                    width = Math.max(width, paint.measureText(candidate));
                }
        }
        return (int) Math.ceil(width);
    }
    PopupMenu overflow(View anchor, TokenId id) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add(0, 1, 0, "Edit").setOnMenuItemClickListener(item -> { change.accept(id, TokenChange.Kind.EDIT); return true; });
        menu.getMenu().add(0, 2, 1, "Delete…").setOnMenuItemClickListener(item -> { change.accept(id, TokenChange.Kind.DELETE); return true; });
        return menu;
    }
}

/** Decorative renderer. Clockwise arc from twelve o'clock drains on authoritative ticks. */
final class CountdownRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint foreground = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float progress;

    CountdownRingView(Context context, int color) {
        super(context);
        float stroke = 2 * getResources().getDisplayMetrics().density;
        for (Paint paint : new Paint[]{track, foreground}) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(stroke); paint.setColor(color);
        }
        track.setAlpha(Math.round(android.graphics.Color.alpha(color) * 0.2f));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_NONE);
        setFocusable(false); setClickable(false); setSaveEnabled(false);
    }
    static float fraction(long remaining, long period) {
        if (period <= 0 || remaining <= 0) return 0;
        return (float) Math.min(1.0, (double) remaining / period);
    }
    void setRemaining(long remaining, long period) {
        progress = fraction(remaining, period); invalidate();
    }
    float progress() { return progress; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float diameter = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom()) - foreground.getStrokeWidth();
        if (diameter <= 0) return;
        float left = getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - diameter) / 2;
        float top = getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - diameter) / 2;
        bounds.set(left, top, left + diameter, top + diameter);
        canvas.drawOval(bounds, track);
        canvas.drawArc(bounds, -90, 360 * progress, false, foreground);
    }
}
