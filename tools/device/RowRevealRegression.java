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
            var syncButton = new android.widget.Button(context); syncButton.setText("Sync"); root.addView(syncButton);
            var open = new AndroidVaultController.Snapshot(AndroidVaultController.State.OPEN,
                    AndroidVaultController.Error.NONE, "Vault open", null);
            String message = MainActivity.mainStatus(open, sync, null);
            status.setText(message); status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
            measureGroup(root, context, width, 640);
            list.setSelectionFromTop(4, -11); root.requestLayout(); measureGroup(root, context, width, 640);
            String before = bounds(toolbar) + "/" + bounds(search) + "/" + bounds(list) + "/" + bounds(add);
            int first = list.getFirstVisiblePosition(), top = list.getChildAt(0).getTop();
            int[] invalidations = {0}, neighborWrites = {0};
            adapter.registerDataSetObserver(new android.database.DataSetObserver() {
                @Override public void onChanged() { invalidations[0]++; }
            });
            var neighbor = (TokenListAdapter.Row) list.getChildAt(0);
            neighbor.account.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                public void onTextChanged(CharSequence text, int start, int before, int count) { neighborWrites[0]++; }
                public void afterTextChanged(android.text.Editable text) {}
            });
            String neighborBefore = valueBounds(neighbor);
            for (int step = 0; step < 5; step++) {
                var shown = step > 0 && step < 4 ? code(tokens.get(first + 1), "123456") : null;
                long seconds = step == 1 ? 18 : shown == null ? 0 : 9;
                var snapshot = new AndroidVaultController.Snapshot(AndroidVaultController.State.OPEN,
                        AndroidVaultController.Error.NONE, "Vault open", null, shown, seconds);
                message = MainActivity.mainStatus(snapshot, sync, null);
                status.setText(message); status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
                adapter.updateRevealPresentation(shown, seconds);
                root.requestLayout(); measureGroup(root, context, width, 640);
                check(before.equals(bounds(toolbar) + "/" + bounds(search) + "/" + bounds(list) + "/" + bounds(add)),
                        "screen stable across generation/reveal/tick/copy/expiry step=" + step + " width=" + width + " sync=" + sync);
                check(list.getFirstVisiblePosition() == first && list.getChildAt(0).getTop() == top, "screen ListView anchor stable");
                check(search.isEnabled() && add.isEnabled() && syncButton.isEnabled() && toolbar.isEnabled(), "screen controls stable and enabled");
                check(message.contentEquals(status.getText()) && status.getVisibility() == (message.isEmpty() ? View.GONE : View.VISIBLE), "status stable");
                for (int child = 0; child < list.getChildCount(); child++) {
                    var row = (TokenListAdapter.Row) list.getChildAt(child);
                    check(row.body.isEnabled() && row.more.isEnabled(), "row and overflow remain enabled");
                }
                check(invalidations[0] == 0 && neighborWrites[0] == 0, "no adapter invalidation or neighboring rebind");
                check(list.getChildAt(0) == neighbor && neighborBefore.equals(valueBounds(neighbor)), "neighbor presentation untouched");
            }
        }
    }
    private static void await(java.util.Queue<Runnable> deliveries, java.util.function.BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        do {
            for (Runnable next; (next = deliveries.poll()) != null;) next.run();
            if (done.getAsBoolean()) return;
            Thread.sleep(5);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("controller wait timed out");
    }
    private static String screenState(android.widget.ListView list, View... controls) {
        StringBuilder value = new StringBuilder();
        for (View control : controls) {
            value.append(bounds(control)).append('/').append(control.isEnabled()).append('/').append(control.getVisibility());
            if (control instanceof TextView text) value.append('/').append(text.getText());
        }
        value.append('/').append(bounds(list)).append('/').append(list.getFirstVisiblePosition());
        for (int i = 0; i < list.getChildCount(); i++) {
            var row = (TokenListAdapter.Row) list.getChildAt(i);
            value.append('/').append(bounds(row)).append('/').append(row.body.isEnabled()).append('/').append(row.more.isEnabled());
        }
        return value.toString();
    }
    /** Real controller + real Android list, with crypto completion held by a latch. */
    private static void delayedControllerScreen(Context context) throws Exception {
        var deliveries = new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        java.util.concurrent.CountDownLatch[] entered = {new java.util.concurrent.CountDownLatch(1)};
        java.util.concurrent.CountDownLatch[] release = {new java.util.concurrent.CountDownLatch(1)};
        Instant[] now = {Instant.ofEpochSecond(31)}; long[] elapsed = {0}; Runnable[] tick = {null};
        int[] generations = {0}, copies = {0};
        var dispatcher = new AndroidVaultController.Dispatcher() {
            public void post(Runnable action) { deliveries.add(action); }
            public void assertDispatchThread() { if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) throw new AssertionError("not UI"); }
            public void assertWorkerThread() { if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) throw new AssertionError("not worker"); }
            public Runnable after(long delay, Runnable action) { tick[0] = action; return () -> { if (tick[0] == action) tick[0] = null; }; }
        };
        var directory = java.nio.file.Files.createTempDirectory(context.getCacheDir().toPath(), "reveal-controller-");
        var controller = new AndroidVaultController(new LocalReplicaOwner(directory), dispatcher, new AndroidVaultController.Backend() {
            org.totipo.android.reconcile.ForegroundVaultCoordinator.TotpResult generateTotp(
                    org.totipo.android.reconcile.ForegroundVaultCoordinator vault, TokenId id, Instant time, ObservedToken expected) {
                generations[0]++;
                var result = super.generateTotp(vault, id, time, expected);
                entered[0].countDown();
                try { if (!release[0].await(15, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
                return result;
            }
        }, new TotpPresentation.Time() {
            public Instant wall() { return now[0]; }
            public long elapsedMillis() { return elapsed[0]; }
        }, new TotpPresentation.Clipboard() {
            public boolean copy(String marker, String text) { copies[0]++; return true; }
            public void clearIfOwned(String marker, String text) {}
        });
        String stage = "discover";
        try {
            await(deliveries, () -> controller.snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT);
            stage = "create";
            check(controller.create("detached-only-test".toCharArray()), "device create admitted");
            await(deliveries, controller::canAddToken);
            for (int i = 0; i < 2; i++) {
                stage = "add " + i;
                try (var draft = OtpAuthUriParser.parse("otpauth://totp/Fixture:account" + i + "?secret=MY&issuer=Fixture")) {
                    check(controller.addToken(draft.transfer()), "device add admitted");
                }
                int count = i + 1;
                await(deliveries, () -> controller.canAddToken() && controller.snapshot().view().tokens().size() == count);
            }
            var root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
            var toolbar = new TextView(context); toolbar.setText("Totipo"); root.addView(toolbar);
            var status = new TextView(context); root.addView(status);
            var search = new android.widget.EditText(context); search.setSingleLine(true); root.addView(search);
            var list = new android.widget.ListView(context); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            var add = new android.widget.Button(context); add.setText("Add token"); root.addView(add);
            var sync = new android.widget.Button(context); sync.setText("Sync"); root.addView(sync);
            var adapter = new TokenListAdapter(context, id -> {
                var shown = controller.snapshot().revealedCode();
                if (shown != null && shown.tokenId().equals(id)) controller.copyShownCode(); else controller.showCode(id);
            }, (id, kind) -> {}, controller::hideCode);
            list.setAdapter(adapter);
            int[] globalRenders = {0}, notifications = {0}, neighborWrites = {0};
            controller.attach(new AndroidVaultController.Listener() {
                public void changed(AndroidVaultController.Snapshot state) {
                    globalRenders[0]++;
                    String message = MainActivity.mainStatus(state, controller.dailySyncStatus(), controller.tokenChangeResult());
                    status.setText(message); status.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
                    add.setEnabled(controller.canAddToken()); sync.setEnabled(controller.canSync());
                    adapter.replace(state.view() == null ? List.of() : state.view().tokens(), controller.canAddToken(), state.revealedCode(), state.remainingSeconds());
                }
                public void revealChanged(AndroidVaultController.Snapshot state) {
                    adapter.updateRevealPresentation(state.revealedCode(), state.remainingSeconds());
                }
            });
            stage = "attach";
            await(deliveries, () -> adapter.getCount() == 2);
            measureGroup(root, context, 320, 640);
            var target = (TokenListAdapter.Row) list.getChildAt(0);
            var neighbor = (TokenListAdapter.Row) list.getChildAt(1);
            neighbor.issuer.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                public void onTextChanged(CharSequence text, int start, int before, int count) { neighborWrites[0]++; }
                public void afterTextChanged(android.text.Editable text) {}
            });
            adapter.registerDataSetObserver(new android.database.DataSetObserver() {
                public void onChanged() { notifications[0]++; }
            });
            String before = screenState(list, toolbar, search, add, sync, status), neighborBefore = valueBounds(neighbor);
            int renders = globalRenders[0];
            target.body.performClick();
            check(entered[0].await(10, java.util.concurrent.TimeUnit.SECONDS), "real generation in flight");
            await(deliveries, () -> true);
            check(controller.snapshot().state() == AndroidVaultController.State.OPEN && controller.canAddToken(), "in-flight controller OPEN/Add stable");
            check(before.equals(screenState(list, toolbar, search, add, sync, status)), "in-flight whole screen stable");
            release[0].countDown(); stage = "reveal";
            await(deliveries, () -> controller.snapshot().revealedCode() != null);
            await(deliveries, () -> true); // Apply the posted row event.
            check(target.code.getText().length() > 0, "completion updates already-bound target"); action(target.body, "Copy code");
            for (int step = 0; step < 4; step++) {
                if (step == 0 || step == 1) { now[0] = now[0].plusSeconds(1); elapsed[0] += 1000; tick[0].run(); }
                if (step == 2) { var code = controller.snapshot().revealedCode(); float ring = target.ring.progress(); target.body.performClick(); check(copies[0] == 1 && code == controller.snapshot().revealedCode() && ring == target.ring.progress(), "copy keeps presentation/deadline"); }
                if (step == 3) { now[0] = Instant.ofEpochSecond(60); elapsed[0] = 29000; tick[0].run(); }
                await(deliveries, () -> true); measureGroup(root, context, 320, 640);
                if (step < 2) check(((28 - step) + " s").contentEquals(target.countdown.getText())
                        && target.ring.progress() == CountdownRingView.fraction(28 - step, 30), "real tick updates target countdown/ring");
                check(before.equals(screenState(list, toolbar, search, add, sync, status)), "real reveal/tick/copy/expiry screen stable");
                check(globalRenders[0] == renders && notifications[0] == 0 && neighborWrites[0] == 0, "no global render/invalidation/neighbor rebind");
                check(neighborBefore.equals(valueBounds(neighbor)) && neighbor.code.getText().length() == 0, "neighbor untouched");
            }
            check(target.code.getText().length() == 0 && target.ring.getVisibility() == View.GONE, "expiry clears bound target");
            check(generations[0] == 1, "ticks/copy/expiry never generate");
            entered[0] = new java.util.concurrent.CountDownLatch(1); release[0] = new java.util.concurrent.CountDownLatch(1);
            check(controller.showCode(adapter.getItem(0).id()), "second delayed reveal admitted");
            check(entered[0].await(10, java.util.concurrent.TimeUnit.SECONDS), "filter while real reveal pending");
            adapter.search("no match");
            check(adapter.getCount() == 0, "pending owner filtered out");
            adapter.search(controller.snapshot().view().tokens().get(1).alternatives().get(0).account());
            var recycled = (TokenListAdapter.Row) adapter.getView(0, target, list);
            release[0].countDown();
            var workerField = AndroidVaultController.class.getDeclaredField("worker"); workerField.setAccessible(true);
            ((java.util.concurrent.ThreadPoolExecutor) workerField.get(controller)).submit(() -> {}).get(10, java.util.concurrent.TimeUnit.SECONDS);
            await(deliveries, () -> true);
            check(controller.snapshot().revealedCode() == null && recycled.code.getText().length() == 0, "delayed result cannot inject code into recycled row");
            adapter.search("");
            check(controller.snapshot().revealedCode() == null, "clearing search cannot resurrect retired result");
            check(controller.lock(), "device cleanup lock");
            await(deliveries, () -> controller.snapshot().state() == AndroidVaultController.State.LOCKED);
        } catch (Exception | AssertionError failure) {
            throw new AssertionError(stage + ": " + controller.snapshot().state() + ": " + controller.snapshot().message() + ": " + failure);
        } finally { release[0].countDown(); controller.shutdown(); }
    }
    @Override public void onCreate(Bundle args) { syncBaseline = "true".equals(args.getString("syncBaseline")); syncOnly = "true".equals(args.getString("syncOnly")); super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); int status = -1;
        try {
            Throwable[] problem = {null};
            runOnMainSync(() -> { try { run(getTargetContext()); } catch (Throwable failure) { problem[0] = failure; } });
            if (problem[0] != null) throw new AssertionError(problem[0]);
            result.putString("stream", "ROW_REVEAL_PASS checks=" + checks + "\n" + geometry);
        } catch (Throwable failure) {
            status = 1; result.putString("stream", "ROW_REVEAL_FAIL " + android.util.Log.getStackTraceString(failure) + "\n" + geometry);
        }
        finish(status, result);
    }
    private static void run(Context context) throws Exception {
        context.setTheme(android.R.style.Theme_Material_Light);
        if (syncOnly) { delayedSyncScreen(context); return; }
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
        delayedControllerScreen(context);
        geometry.append("ROW_REVEAL_BASE checks=" + checks + "\n");
        int rowChecks = checks;
        delayedSyncScreen(context);
        geometry.append("SYNC_LAYOUT_PASS checks=" + (checks - rowChecks) + "\n");
    }
    // Exercise the actual MainActivity tree against the existing provider/controller seam.
    // Blocking queue and latches control completion; elapsed time never releases Sync.
    private static void syncAwait(java.util.concurrent.BlockingQueue<Runnable> queue,
                                  java.util.function.BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
        while (!done.getAsBoolean()) {
            Runnable next = queue.poll(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            if (next == null) throw new AssertionError("Sync delivery timeout");
            next.run();
        }
        Runnable next; while ((next = queue.poll()) != null) next.run();
    }
    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static String syncGeometry(LinearLayout root) {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (i == 1 || child.getVisibility() == View.GONE) continue; // Status has no bounds while GONE.
            value.append(i).append('=').append(bounds(child)).append(';');
            if (child instanceof LinearLayout bar) for (int j = 0; j < bar.getChildCount(); j++)
                value.append("bar").append(j).append('=').append(bounds(bar.getChildAt(j))).append(';');
            if (child instanceof android.widget.ListView list) {
                value.append("first=").append(list.getFirstVisiblePosition()).append(';');
                for (int j = 0; j < list.getChildCount(); j++) value.append("row").append(j).append('=').append(bounds(list.getChildAt(j))).append(';');
            }
        }
        return value.toString();
    }
    private static void delayedSyncScreen(Context context) throws Exception {
        var queue = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        var directory = java.nio.file.Files.createTempDirectory(context.getCacheDir().toPath(), "sync-layout-");
        var port = new LayoutPort(directory.resolve("totipo-vault"));
        var binding = new org.totipo.android.sync.SyncFolderBinding(port);
        var dispatcher = new AndroidVaultController.Dispatcher() {
            public void post(Runnable action) { queue.add(action); }
            public void assertDispatchThread() { if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) throw new AssertionError("Sync UI thread"); }
            public void assertWorkerThread() { if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) throw new AssertionError("Sync worker"); }
            public Runnable after(long delay, Runnable action) { return () -> {}; }
        };
        var controller = new AndroidVaultController(new LocalReplicaOwner(directory), dispatcher,
                new AndroidVaultController.Backend(), new TotpPresentation.Time() {
                    public Instant wall() { return Instant.ofEpochSecond(31); }
                    public long elapsedMillis() { return 0; }
                }, null, binding);
        controller.attach(state -> {}); // Every state transition wakes the blocking delivery pump.
        try {
            syncAwait(queue, () -> controller.snapshot().state() == AndroidVaultController.State.NO_LOCAL_VAULT && controller.canManageSyncFolder());
            check(controller.create("detached-sync-only".toCharArray()), "Sync fixture create");
            syncAwait(queue, controller::canAddToken);
            for (int i = 0; i < 8; i++) {
                try (var draft = OtpAuthUriParser.parse("otpauth://totp/Fixture:account" + i + "?secret=MY&issuer=Fixture")) {
                    check(controller.addToken(draft.transfer()), "Sync fixture token");
                }
                int count = i + 1;
                syncAwait(queue, () -> controller.canAddToken() && controller.snapshot().view().tokens().size() == count);
            }
            check(controller.chooseSyncFolder(false, LayoutPort.URI, 3), "Sync fixture bind");
            syncAwait(queue, controller::canSync);
            check(controller.sync(), "initial quiet Sync");
            syncAwait(queue, () -> controller.canSync() && !controller.dailySyncStatus().equals("Syncing…"));
            check(controller.dailySyncStatus().isEmpty(), "initial Sync success quiet: " + controller.diagnostics());
            for (float scale : new float[]{1f, 1.5f, 2f}) for (int width : new int[]{320, 640}) {
                var configuration = new android.content.res.Configuration(context.getResources().getConfiguration());
                configuration.fontScale = scale;
                Context scaled = context.createConfigurationContext(configuration);
                var info = new android.content.pm.ActivityInfo(); info.theme = android.R.style.Theme_Material_Light_NoActionBar;
                var activity = (MainActivity) new android.app.Instrumentation().newActivity(MainActivity.class,
                        scaled, null, new android.app.Application(), new android.content.Intent().setClass(scaled, MainActivity.class), info, "Totipo", null, null, null);
                activity.setTheme(android.R.style.Theme_Material_Light_NoActionBar);
                var controllerField = MainActivity.class.getDeclaredField("controller"); controllerField.setAccessible(true); controllerField.set(activity, controller);
                var render = MainActivity.class.getDeclaredMethod("render", AndroidVaultController.Snapshot.class); render.setAccessible(true);
                var listener = (AndroidVaultController.Listener) field(activity, "listener");
                controller.attach(listener);
                syncAwait(queue, () -> true);
                var root = (LinearLayout) field(activity, "content");
                var status = (TextView) field(activity, "status");
                var sync = (android.widget.Button) field(activity, "syncAction");
                var list = (android.widget.ListView) root.getChildAt(root.getChildCount() - 2);
                measureGroup(root, scaled, width, width == 640 ? 360 : 640);
                list.setSelectionFromTop(2, -11); root.requestLayout(); measureGroup(root, scaled, width, width == 640 ? 360 : 640);
                check(list.getChildCount() > 0, "Sync fixture has visible token rows");
                String before = syncGeometry(root);
                String syncBounds = bounds(sync), toolbarBounds = bounds(root.getChildAt(0));
                for (boolean failure : new boolean[]{false, true}) {
                    port.entered = new java.util.concurrent.CountDownLatch(1); port.release = new java.util.concurrent.CountDownLatch(1);
                    port.fail = failure; port.hold = true;
                    check(sync.performClick(), "existing Sync control clicked");
                    check(port.entered.await(10, java.util.concurrent.TimeUnit.SECONDS), "provider Sync held");
                    syncAwait(queue, () -> true);
                    measureGroup(root, scaled, width, width == 640 ? 360 : 640);
                    String during = syncGeometry(root);
                    geometry.append("SYNC_GEOMETRY scale=" + scale + " widthDp=" + width + " failure=" + failure
                            + " before=" + before + " during=" + during + " status=" + status.getText() + "\n");
                    boolean baseline = syncBaseline;
                    if (baseline) {
                        check(!before.equals(during) && "Syncing…".contentEquals(status.getText()), "baseline transient row moves screen");
                    } else {
                        check(before.equals(during), "entering Sync preserves toolbar/search/list/rows/Add/Sync");
                        check(status.getVisibility() == View.GONE && status.getText().length() == 0, "no transient status row");
                        check("Syncing…".contentEquals(sync.getText()) && !sync.isEnabled(), "Sync control active");
                        check("Syncing".contentEquals(sync.getContentDescription()), "active Sync accessible");
                        check(sync.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE, "Sync has no live announcements");
                        render.invoke(activity, controller.snapshot());
                        check(sync.getAccessibilityLiveRegion() == View.ACCESSIBILITY_LIVE_REGION_NONE, "repeat render remains quiet");
                        check(sync.getLayout().getLineCount() == 1, "Sync transient label stays one line");
                        check(sync.getLayout().getLineWidth(0) <= sync.getWidth() - sync.getCompoundPaddingLeft() - sync.getCompoundPaddingRight(), "Sync transient label fits measured reservation");
                    }
                    port.hold = false; port.release.countDown();
                    syncAwait(queue, () -> controller.canSync() && !controller.dailySyncStatus().equals("Syncing…"));
                    measureGroup(root, scaled, width, width == 640 ? 360 : 640);
                    String after = syncGeometry(root);
                    geometry.append("SYNC_AFTER scale=" + scale + " widthDp=" + width + " failure=" + failure + " bounds=" + after + " status=" + status.getText() + "\n");
                    check("Sync".contentEquals(sync.getText()) && "Sync".contentEquals(sync.getContentDescription()), "Sync returns ordinary name");
                    check(syncBounds.equals(bounds(sync)) && toolbarBounds.equals(bounds(root.getChildAt(0))), "Sync and toolbar bounds stable after success or error");
                    if (failure) {
                        check(status.getVisibility() == View.VISIBLE && "Sync folder unavailable".contentEquals(status.getText()), "actionable provider failure visible");
                    } else {
                        check(before.equals(after), "successful Sync geometry restored/stable");
                        check(status.getVisibility() == View.GONE && status.getText().length() == 0, "successful Sync quiet");
                    }
                }
                controller.detach(listener);
                port.fail = false;
                check(controller.sync(), "reset error via success");
                syncAwait(queue, () -> controller.canSync() && controller.dailySyncStatus().isEmpty());
            }
        } finally {
            port.hold = false; port.release.countDown();
            if (controller.lock()) syncAwait(queue, () -> controller.snapshot().state() == AndroidVaultController.State.LOCKED);
            controller.shutdown();
            try (var files = java.nio.file.Files.walk(directory)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
            }
        }
    }
    private static boolean syncBaseline, syncOnly;
    private static final class LayoutPort implements org.totipo.android.sync.SyncFolderBinding.Port {
        static final String URI = "content://fixture/tree/layout";
        final java.nio.file.Path root;
        org.totipo.android.sync.SyncFolderBinding.Stored stored;
        volatile boolean hold, fail;
        volatile java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1), release = new java.util.concurrent.CountDownLatch(1);
        LayoutPort(java.nio.file.Path root) { this.root = root; }
        public org.totipo.android.sync.SyncFolderBinding.Stored load() { return stored; }
        public boolean save(org.totipo.android.sync.SyncFolderBinding.Stored value) { stored = value; return true; }
        public boolean validTree(String uri) { return URI.equals(uri); }
        public org.totipo.android.sync.SyncFolderBinding.Grants grants(String uri) { return new org.totipo.android.sync.SyncFolderBinding.Grants(true, true); }
        public void take(String uri, boolean read, boolean write) {}
        public void release(String uri, boolean read, boolean write) {}
        public void probe(String uri) {
            if (hold) {
                entered.countDown();
                try { if (!release.await(20, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("held Sync timeout"); }
                catch (InterruptedException failure) { throw new AssertionError(failure); }
            }
            if (fail) throw new IllegalStateException("fixture unavailable");
        }
        public org.totipo.android.provider.ProviderSnapshot.Scan scan(String uri) {
            try {
                var tree = new org.totipo.android.provider.ProviderSnapshot.Tree("fixture", URI, "root");
                var directory = new org.totipo.android.provider.ProviderSnapshot.Document(tree, "content://fixture/objects", "objects", "root", "objects-v1", "vnd.android.document/directory", null, 8L);
                var vault = new org.totipo.android.provider.ProviderSnapshot.Document(tree, "content://fixture/vault", "vault", "root", "vault", "application/octet-stream", null, null);
                var candidates = new java.util.ArrayList<org.totipo.android.provider.ProviderSnapshot.Bytes>();
                try (var files = java.nio.file.Files.list(root.resolve("objects-v1"))) {
                    for (var path : files.toList()) {
                        String name = path.getFileName().toString();
                        var doc = new org.totipo.android.provider.ProviderSnapshot.Document(tree, "content://fixture/" + name, name, "objects", name, "application/octet-stream", null, null);
                        candidates.add(new org.totipo.android.provider.ProviderSnapshot.Bytes("e", doc, 1024, org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT, java.nio.file.Files.readAllBytes(path), org.totipo.android.provider.ProviderSnapshot.Issue.NONE));
                    }
                }
                var complete = org.totipo.android.provider.ProviderSnapshot.State.COMPLETE;
                return new org.totipo.android.provider.ProviderSnapshot.Scan("e", tree,
                        new org.totipo.android.provider.ProviderSnapshot.Listing("e", "root", List.of(vault, directory), complete, List.of()),
                        List.of(new org.totipo.android.provider.ProviderSnapshot.Directory(directory,
                            new org.totipo.android.provider.ProviderSnapshot.Listing("e", "objects", candidates.stream().map(org.totipo.android.provider.ProviderSnapshot.Bytes::document).toList(), complete, List.of()), candidates)), complete, List.of(),
                        List.of(new org.totipo.android.provider.ProviderSnapshot.Bytes("e", vault, 87, org.totipo.android.provider.ProviderSnapshot.ByteState.PRESENT, java.nio.file.Files.readAllBytes(root.resolve("vault")), org.totipo.android.provider.ProviderSnapshot.Issue.NONE)));
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        }
    }

}
