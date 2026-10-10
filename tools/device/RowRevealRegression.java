package org.totipo.android;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;

/** Isolated self-targeted instrumentation: synthetic rows only, no Totipo launch or vault access. */
public final class RowRevealRegression extends android.app.Instrumentation {
    private static int checks;
    private static final StringBuilder geometry = new StringBuilder();
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
    }
    private static ObservedToken token(String id, int digits, boolean conflict) {
        return token(id, digits, conflict, 30);
    }
    private static ObservedToken token(String id, int digits, boolean conflict, long period) {
        var descriptor = new TokenDescriptor(TokenStatus.ACTIVE, "GitHub with a long issuer",
                "alice.with.a.long.account@example.com", TotpAlgorithm.SHA1, digits, Duration.ofSeconds(period));
        return new ObservedToken(new TokenId(id.repeat(64)), conflict ? List.of(descriptor, descriptor) : List.of(descriptor),
                List.of(), List.of(), conflict);
    }
    private static RevealedTotp code(ObservedToken token, String digits) {
        return new RevealedTotp(token.id(), digits, Instant.EPOCH, Instant.EPOCH.plus(token.alternatives().get(0).period()), digits.length());
    }
    private static boolean hasText(View view, String value) {
        if (view instanceof TextView text && value.contentEquals(text.getText())) return true;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            if (hasText(group.getChildAt(i), value)) return true;
        return false;
    }
    private static void action(View body, String label) {
        var info = body.createAccessibilityNodeInfo();
        check(info.getActionList().stream().anyMatch(a -> a.getId() == AccessibilityNodeInfo.ACTION_CLICK
                && label.contentEquals(a.getLabel())), "accessible " + label);
        if (!label.equals("Resolve")) check(!hasText(body, label), "action label not visible");
    }
    private static int layout(TokenListAdapter.Row row, Context context, int widthDp) {
        int width = Math.round(widthDp * context.getResources().getDisplayMetrics().density);
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        row.layout(0, 0, width, row.getMeasuredHeight());
        check(row.body.getRight() <= row.more.getLeft(), "body and overflow disjoint");
        return row.getHeight();
    }
    private static String bounds(View view) {
        return view.getLeft() + ":" + view.getTop() + ":" + view.getRight() + ":" + view.getBottom();
    }
    private static String valueBounds(TokenListAdapter.Row row) {
        return bounds(row.code) + "/" + bounds(row.countdownLine) + "/" + bounds(row.ring)
                + "/" + bounds(row.identity) + "/" + bounds(row.value) + "/" + bounds(row.more);
    }
    private static void scaledLayouts(Context base) {
        for (float scale : new float[]{1.0f, 1.5f, 2.0f}) {
            var configuration = new android.content.res.Configuration(base.getResources().getConfiguration());
            configuration.fontScale = scale;
            Context context = base.createConfigurationContext(configuration);
            context.setTheme(android.R.style.Theme_Material_Light);
            var adapter = new TokenListAdapter(context, id -> {}, (id, kind) -> {}, () -> {});
            var parent = new LinearLayout(context);
            for (String digits : List.of("012345", "0123456", "01234567")) {
                for (long period : new long[]{30, 45, 60, 4294967295L}) {
                    var token = token("a", digits.length(), false, period);
                    var revealed = code(token, digits);
                    for (int width : new int[]{280, 320, 640}) {
                        adapter.replace(List.of(token), true, null, 0);
                        var row = (TokenListAdapter.Row) adapter.getView(0, null, parent);
                        int concealedHeight = layout(row, context, width);
                        int concealedIdentityWidth = row.identity.getWidth();
                        adapter.replace(List.of(token), true, revealed, 18);
                        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
                        int height = layout(row, context, width);
                        check(height == concealedHeight, "equal concealed/revealed height scale=" + scale + " width=" + width);
                        check(row.identity.getWidth() > 0 && row.identity.getRight() <= row.value.getLeft(), "scaled identity disjoint");
                        check(concealedIdentityWidth > row.identity.getWidth(), "concealed identity regains horizontal width");
                        check(row.value.getRight() <= row.body.getWidth() - row.body.getPaddingRight(), "scaled value contained");
                        check(row.code.getLayout().getLineCount() == 1 && row.code.getLayout().getLineWidth(0)
                                <= row.code.getWidth() - row.code.getPaddingLeft() - row.code.getPaddingRight(), "scaled full code not clipped");
                        check(row.countdownLine.getTop() >= row.code.getBottom(), "countdown on second line");
                        check(row.countdown.getRight() < row.ring.getLeft(), "ring follows countdown with gap");
                        check(row.ring.getBottom() <= row.countdownLine.getHeight(), "ring vertically contained");
                        check(row.countdownLine.getBottom() <= row.value.getHeight(), "second line vertically contained");
                        String stableBounds = valueBounds(row);
                        for (long seconds : new long[]{period, 20, 19, 18, 10, 9, 2, 1}) {
                            adapter.replace(List.of(token), true, revealed, seconds);
                            row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
                            check(layout(row, context, width) == height, "tick height stable");
                            check(stableBounds.equals(valueBounds(row)), "tick bounds stable scale=" + scale + " width=" + width + " digits=" + digits.length() + " period=" + period + " seconds=" + seconds + " before=" + stableBounds + " after=" + valueBounds(row));
                            check(row.countdown.getPaint().measureText(seconds + " s") <= row.countdown.getWidth(), "countdown text fits reservation");
                            check(row.countdown.getLayout().getLineCount() == 1, "countdown one line");
                            check(row.ring.progress() == CountdownRingView.fraction(seconds, period), "actual period fraction bound");
                        }
                        adapter.replace(List.of(token), true, null, 0);
                        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
                        check(layout(row, context, width) == concealedHeight, "conceal restores same height");
                        check(row.identity.getWidth() == concealedIdentityWidth, "conceal restores identity width");
                        if (digits.length() == 6 && period == 30)
                            geometry.append("GEOMETRY scale=" + scale + " widthDp=" + width
                                    + " concealed=" + concealedHeight + " revealed=" + height + "\n");
                    }
                }
            }
            // Verify the measured reservation against every valid value for ordinary periods.
            var text = new TextView(context); text.setTextSize(12);
            for (long period : new long[]{30, 45, 60, 120}) {
                int reserved = TokenListAdapter.countdownWidth(text.getPaint(), period);
                float widest = 0;
                for (long seconds = 1; seconds <= period; seconds++)
                    widest = Math.max(widest, text.getPaint().measureText(seconds + " s"));
                check(reserved == (int) Math.ceil(widest), "reservation matches widest valid measured text");
            }
            sequenceLayouts(context);
            screenLayouts(context);
        }
    }
    private static void measureGroup(View group, Context context, int widthDp, int heightDp) {
        int width = Math.round(widthDp * context.getResources().getDisplayMetrics().density);
        int height = Math.round(heightDp * context.getResources().getDisplayMetrics().density);
        group.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        group.layout(0, 0, width, height);
    }
    private static void sequenceLayouts(Context context) {
        var a = token("a", 6, false); var b = token("b", 8, false); var c = token("c", 7, false);
        List<ObservedToken> tokens = List.of(a, b, c);
        for (int width : new int[]{280, 320, 640}) {
            var adapter = new TokenListAdapter(context, id -> {}, (id, kind) -> {}, () -> {});
            var group = new LinearLayout(context); group.setOrientation(LinearLayout.VERTICAL);
            adapter.replace(tokens, true);
            for (int i = 0; i < tokens.size(); i++) group.addView(adapter.getView(i, null, group));
            measureGroup(group, context, width, 640);
            String before = bounds(group.getChildAt(0)) + "/" + bounds(group.getChildAt(1)) + "/" + bounds(group.getChildAt(2));
            for (long remaining : new long[]{18, 9, 0}) {
                adapter.replace(tokens, true, remaining == 0 ? null : code(b, "12345678"), remaining);
                for (int i = 0; i < tokens.size(); i++) adapter.getView(i, group.getChildAt(i), group);
                measureGroup(group, context, width, 640);
                check(before.equals(bounds(group.getChildAt(0)) + "/" + bounds(group.getChildAt(1)) + "/" + bounds(group.getChildAt(2))),
                        "neighbor Y positions and row bounds unchanged");
            }
            // Actual ListView with a fixed scrolled anchor; no manual restoration during transitions.
            var list = new android.widget.ListView(context);
            var many = java.util.stream.IntStream.range(0, 15).mapToObj(i ->
                    token(Integer.toHexString(i + 1), 8, false)).collect(java.util.stream.Collectors.toList());
            adapter.replace(many, true, null, 0); list.setAdapter(adapter);
            measureGroup(list, context, width, 240);
            list.setSelectionFromTop(4, -11); measureGroup(list, context, width, 240);
            int first = list.getFirstVisiblePosition(), top = list.getChildAt(0).getTop();
            int middle = first + Math.min(1, list.getChildCount() - 1);
            for (long remaining : new long[]{18, 9, 0}) {
                adapter.replace(many, true, remaining == 0 ? null : code(many.get(middle), "12345678"), remaining);
                list.requestLayout(); measureGroup(list, context, width, 240);
                check(list.getFirstVisiblePosition() == first && list.getChildAt(0).getTop() == top, "ListView anchor preserved");
            }
        }
    }
    private static void screenLayouts(Context context) {
        var tokens = java.util.stream.IntStream.range(1, 16).mapToObj(i ->
                token(Integer.toHexString(i), 6, false)).collect(java.util.stream.Collectors.toList());
        for (int width : new int[]{280, 320, 640}) for (String sync : List.of("", "Changes not synced")) {
            var root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
            var toolbar = new TextView(context); toolbar.setText("Totipo"); toolbar.setTextSize(28); root.addView(toolbar);
            var status = new TextView(context); root.addView(status);
            var search = new android.widget.EditText(context); search.setSingleLine(true); search.setHint("Search tokens…"); root.addView(search);
            var adapter = new TokenListAdapter(context, id -> {}, (id, kind) -> {}, () -> {});
            var list = new android.widget.ListView(context); adapter.replace(tokens, true); list.setAdapter(adapter);
            root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            var add = new android.widget.Button(context); add.setText("Add token"); root.addView(add);
            var open = new AndroidVaultController.Snapshot(AndroidVaultController.State.OPEN,
                    AndroidVaultController.Error.NONE, "Vault open", null);
            String message = MainActivity.mainStatus(open, sync, null);
            status.setText(message); status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
            measureGroup(root, context, width, 640);
            list.setSelectionFromTop(4, -11); root.requestLayout(); measureGroup(root, context, width, 640);
            String before = bounds(toolbar) + "/" + bounds(search) + "/" + bounds(list) + "/" + bounds(add);
            int first = list.getFirstVisiblePosition(), top = list.getChildAt(0).getTop();
            for (int step = 0; step < 5; step++) {
                boolean generating = step == 0;
                var shown = step > 0 && step < 4 ? code(tokens.get(first + 1), "123456") : null;
                long seconds = step == 1 ? 18 : shown == null ? 0 : 9;
                var snapshot = new AndroidVaultController.Snapshot(generating ? AndroidVaultController.State.BUSY : AndroidVaultController.State.OPEN,
                        AndroidVaultController.Error.NONE, generating ? "Generating code…" : "Vault open", null, shown, seconds);
                message = MainActivity.mainStatus(snapshot, sync, null);
                status.setText(message); status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.replace(tokens, !generating, shown, seconds);
                root.requestLayout(); measureGroup(root, context, width, 640);
                check(before.equals(bounds(toolbar) + "/" + bounds(search) + "/" + bounds(list) + "/" + bounds(add)),
                        "screen stable across generation/reveal/tick/copy/expiry step=" + step + " width=" + width + " sync=" + sync);
                check(list.getFirstVisiblePosition() == first && list.getChildAt(0).getTop() == top, "screen ListView anchor stable");
            }
        }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int status = -1;
        try {
            Throwable[] problem = {null};
            runOnMainSync(() -> { try { run(getTargetContext()); } catch (Throwable failure) { problem[0] = failure; } });
            if (problem[0] != null) throw new AssertionError(problem[0]);
            result.putString("stream", "ROW_REVEAL_PASS checks=" + checks + "\n" + geometry);
        } catch (Throwable failure) {
            status = 1; result.putString("stream", "ROW_REVEAL_FAIL " + failure + "\n");
        }
        finish(status, result);
    }
    private static void run(Context context) {
        context.setTheme(android.R.style.Theme_Material_Light);
        int[] taps = {0}, retired = {0}; TokenChange.Kind[] changed = {null};
        var adapter = new TokenListAdapter(context, id -> taps[0]++, (id, kind) -> changed[0] = kind, () -> retired[0]++);
        var parent = new LinearLayout(context);
        var a = token("a", 8, false); var b = token("b", 8, false);
        adapter.replace(List.of(a, b), true);
        var row = (TokenListAdapter.Row) adapter.getView(0, null, parent);
        check(!hasText(row, "Show code") && !hasText(row, "Tap to reveal") && !hasText(row, "Tap to copy"), "no visible instruction");
        check(row.value.getVisibility() == View.GONE, "concealed identity only");
        int concealedHeight = layout(row, context, 280);
        action(row.body, "Reveal code"); row.body.performClick(); check(taps[0] == 1, "first body tap");
        for (String digits : List.of("012345", "0123456", "01234567")) {
            adapter.replace(List.of(a, b), true, code(a, digits), 18);
            row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
            check(row.value.getVisibility() == View.VISIBLE && digits.equals(row.code.getText().toString().replace(" ", "")), "row-local full code");
            check(row.code.getEllipsize() == null, "no code ellipsis");
            for (int width : new int[]{280, 320, 640}) {
                int height = layout(row, context, width);
                check(row.identity.getWidth() > 0 && row.issuer.getWidth() > 0 && row.account.getWidth() > 0, "identity retains width");
                check(row.identity.getRight() <= row.value.getLeft(), "identity and value disjoint");
                check(row.value.getRight() <= row.body.getWidth() - row.body.getPaddingRight(), "value inside body");
                check(row.code.getLayout().getLineCount() == 1, "one full code line");
                check(row.code.getLayout().getLineWidth(0) <= row.code.getWidth() - row.code.getPaddingLeft() - row.code.getPaddingRight(), "code not clipped");
                check(height == concealedHeight, "row height stays compact concealed=" + concealedHeight + " revealed=" + height);
            }
            action(row.body, "Copy code");
            check(row.code.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE
                    && row.countdown.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE
                    && row.countdown.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO, "code and countdown quiet");
            check(row.ring.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    && !row.ring.isFocusable() && !row.ring.isClickable()
                    && row.ring.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE, "ring decorative and quiet");
            check("18 s".contentEquals(row.countdown.getText()), "textual countdown retained");
        }
        row.body.performClick(); check(taps[0] == 2, "second body tap delivered");
        check(row.value.getVisibility() == View.VISIBLE, "tap does not locally conceal");
        try { row.more.performClick(); } catch (android.view.WindowManager.BadTokenException expected) {
            // Detached test view has no window; listener still must not invoke body action.
        }
        check(taps[0] == 2, "overflow never invokes body action");
        var menu = adapter.overflow(row.more, a.id()).getMenu();
        menu.performIdentifierAction(1, 0); check(changed[0] == TokenChange.Kind.EDIT, "Edit menu callback");
        menu.performIdentifierAction(2, 0); check(changed[0] == TokenChange.Kind.DELETE, "Delete menu callback");
        row = (TokenListAdapter.Row) adapter.getView(1, row, parent);
        check(row.code.getText().length() == 0 && row.countdown.getText().length() == 0
                && row.value.getVisibility() == View.GONE && row.ring.getVisibility() == View.GONE
                && row.ring.progress() == 0, "recycling A into B clears code/countdown/ring");
        adapter.replace(List.of(a, b), true, code(b, "87654321"), 17);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        check(row.code.getText().length() == 0, "switch to B conceals A");
        row = (TokenListAdapter.Row) adapter.getView(1, row, parent);
        check("8765 4321".contentEquals(row.code.getText()), "B receives only B code");
        check(row.ring.getVisibility() == View.VISIBLE && row.ring.progress() == CountdownRingView.fraction(17, 30), "B receives own ring timing");
        adapter.search("alice");
        check(retired[0] == 0 && "8765 4321".contentEquals(row.code.getText()), "matching search preserves presentation");
        adapter.search("no match"); check(adapter.getCount() == 0 && retired[0] == 1, "filter retires reveal");
        check(row.code.getText().length() == 0 && row.value.getVisibility() == View.GONE, "filtered bound widget clears immediately");
        check(row.ring.getVisibility() == View.GONE && row.ring.progress() == 0, "filter immediately clears ring");
        adapter.search(""); row = (TokenListAdapter.Row) adapter.getView(1, row, parent);
        check(row.code.getText().length() == 0, "filter restoration cannot resurrect code");
        adapter.replace(List.of(a), true, code(a, "12345678"), 1);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        adapter.replace(List.of(a), true, null, 0);
        check(row.code.getText().length() == 0, "expiry clears bound widget immediately");
        check(row.ring.getVisibility() == View.GONE && row.ring.progress() == 0, "expiry clears ring immediately");
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        check(row.value.getVisibility() == View.GONE && row.code.getText().length() == 0, "expired snapshot clears row");
        var conflict = token("a", 8, true);
        adapter.replace(List.of(conflict), true, code(a, "12345678"), 18);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        check(row.value.getVisibility() == View.GONE && row.more.getVisibility() == View.GONE, "conflict has no code or overflow");
        check(row.code.getText().length() == 0 && row.countdown.getText().length() == 0
                && row.ring.getVisibility() == View.GONE && row.ring.progress() == 0, "conflict has no countdown/ring");
        action(row.body, "Resolve"); changed[0] = null; row.body.performClick();
        check(changed[0] == TokenChange.Kind.RESOLVE && taps[0] == 2, "conflict body only resolves");
        changed[0] = null; row.resolve.performClick(); check(changed[0] == TokenChange.Kind.RESOLVE && taps[0] == 2, "Resolve child consumes click");
        adapter.replace(List.of(a), true, code(a, "12345678"), 18);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        adapter.replace(List.of(), false, null, 0);
        check(adapter.getCount() == 0 && row.code.getText().length() == 0, "locked snapshot clears bound code and rows");
        adapter.replace(List.of(a), true, code(a, "12345678"), 18);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        int beforeDelete = retired[0]; adapter.replace(List.of(b), true);
        check(retired[0] == beforeDelete + 1 && row.code.getText().length() == 0, "deletion retires bound presentation");
        scaledLayouts(context);
    }
}
