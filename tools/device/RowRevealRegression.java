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
    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
        checks++;
    }
    private static ObservedToken token(String id, int digits, boolean conflict) {
        var descriptor = new TokenDescriptor(TokenStatus.ACTIVE, "GitHub with a long issuer",
                "alice.with.a.long.account@example.com", TotpAlgorithm.SHA1, digits, Duration.ofSeconds(30));
        return new ObservedToken(new TokenId(id.repeat(64)), conflict ? List.of(descriptor, descriptor) : List.of(descriptor),
                List.of(), List.of(), conflict);
    }
    private static RevealedTotp code(ObservedToken token, String digits) {
        return new RevealedTotp(token.id(), digits, Instant.EPOCH, Instant.EPOCH.plusSeconds(30), digits.length());
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
    private static void scaledLayouts(Context base) {
        for (float scale : new float[]{1.0f, 1.5f, 2.0f}) {
            var configuration = new android.content.res.Configuration(base.getResources().getConfiguration());
            configuration.fontScale = scale;
            Context context = base.createConfigurationContext(configuration);
            context.setTheme(android.R.style.Theme_Material_Light);
            var adapter = new TokenListAdapter(context, id -> {}, (id, kind) -> {}, () -> {});
            var parent = new LinearLayout(context);
            for (String digits : List.of("012345", "0123456", "01234567")) {
                var token = token("a", digits.length(), false);
                adapter.replace(List.of(token), true, code(token, digits), 18);
                var row = (TokenListAdapter.Row) adapter.getView(0, null, parent);
                for (int width : new int[]{280, 320, 640}) {
                    layout(row, context, width);
                    check(row.identity.getWidth() > 0 && row.identity.getRight() <= row.value.getLeft(), "scaled identity disjoint");
                    check(row.value.getRight() <= row.body.getWidth() - row.body.getPaddingRight(), "scaled value contained");
                    check(row.code.getLayout().getLineCount() == 1 && row.code.getLayout().getLineWidth(0)
                            <= row.code.getWidth() - row.code.getPaddingLeft() - row.code.getPaddingRight(), "scaled full code not clipped");
                }
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
            result.putString("stream", "ROW_REVEAL_PASS checks=" + checks + "\n");
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
                check(height <= concealedHeight + Math.round(8 * context.getResources().getDisplayMetrics().density), "row height stays compact concealed=" + concealedHeight + " revealed=" + height);
            }
            action(row.body, "Copy code");
            check(row.code.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE
                    && row.countdown.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE
                    && row.countdown.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO, "code and countdown quiet");
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
        check(row.code.getText().length() == 0 && row.value.getVisibility() == View.GONE, "recycling A into B clears code");
        adapter.replace(List.of(a, b), true, code(b, "87654321"), 17);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        check(row.code.getText().length() == 0, "switch to B conceals A");
        row = (TokenListAdapter.Row) adapter.getView(1, row, parent);
        check("8765 4321".contentEquals(row.code.getText()), "B receives only B code");
        adapter.search("alice");
        check(retired[0] == 0 && "8765 4321".contentEquals(row.code.getText()), "matching search preserves presentation");
        adapter.search("no match"); check(adapter.getCount() == 0 && retired[0] == 1, "filter retires reveal");
        check(row.code.getText().length() == 0 && row.value.getVisibility() == View.GONE, "filtered bound widget clears immediately");
        adapter.search(""); row = (TokenListAdapter.Row) adapter.getView(1, row, parent);
        check(row.code.getText().length() == 0, "filter restoration cannot resurrect code");
        adapter.replace(List.of(a), true, code(a, "12345678"), 1);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        adapter.replace(List.of(a), true, null, 0);
        check(row.code.getText().length() == 0, "expiry clears bound widget immediately");
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        check(row.value.getVisibility() == View.GONE && row.code.getText().length() == 0, "expired snapshot clears row");
        var conflict = token("a", 8, true);
        adapter.replace(List.of(conflict), true, code(a, "12345678"), 18);
        row = (TokenListAdapter.Row) adapter.getView(0, row, parent);
        check(row.value.getVisibility() == View.GONE && row.more.getVisibility() == View.GONE, "conflict has no code or overflow");
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
