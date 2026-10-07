package org.totipo.android.debug;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Disposable-tree experiments only; this class is absent from release sources. */
// Synchronous journal/report writes run on WORKER and must precede provider mutations.
@android.annotation.SuppressLint("ApplySharedPref")
public final class StorageProbeActivity extends Activity {
    private static final int PICK_TREE = 73;
    private static final int READ_LIMIT = 4096;
    // Serial across Activity recreation as well as button presses.
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private TextView output;
    private final List<Button> buttons = new ArrayList<>();
    private Uri tree;
    private ProbeDocuments docs;
    private JSONArray owned;
    private String report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("storage-probe-debug", MODE_PRIVATE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView warning = new TextView(this);
        warning.setText("DEBUG STORAGE PROBE — select ONLY a disposable test directory. Never select a real Totipo vault. All operations target the selected tree; no recursive inspection. Authentication stays in the provider UI.\nReports redact tree names, opaque IDs and unrelated siblings. Flags are provider-advertised capabilities, not guarantees.");
        layout.addView(warning);
        button(layout, "Select disposable tree", () -> new AlertDialog.Builder(this)
                .setMessage("Confirm that you will select a disposable test directory, never a real vault. Changing trees starts a new report; copy the current report first. Old artifacts require manual deletion if you change trees.")
                .setPositiveButton("Select disposable directory", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                    startActivityForResult(intent, PICK_TREE);
                }).setNegativeButton("Cancel", null).show());
        button(layout, "Show selected tree / provider info", () -> run("Provider info", this::info));
        button(layout, "Reopen persisted tree + enumerate + read owned documents", () -> run("Persisted access / harmless availability", () -> {
            info(); enumerate();
            for (int i = 0; i < owned.length(); i++) {
                JSONObject item = owned.getJSONObject(i);
                attemptRead(item, "persistedRead");
            }
            log("Unavailability is not corruption. For lock testing, unlock/lock in the provider app, then rerun this same harmless action.");
        }));
        button(layout, "Enumerate direct children (redacted)", () -> run("Direct children", this::enumerate));
        button(layout, "Run create-new test", () -> run("Create test", this::createTest));
        button(layout, "Run same-name conflict test", () -> run("Conflict test", this::conflictTest));
        button(layout, "Run complete private-stage materialization", () -> run("Materialization test", () -> materialize(false)));
        button(layout, "Run controlled incomplete materialization (256 / 1024 bytes)", () -> run("Controlled incomplete materialization", () -> materialize(true)));
        button(layout, "DELETE only recorded probe-created documents", () -> new AlertDialog.Builder(this)
                .setMessage("Best-effort deletion of exact recorded probe-created identities only. The selected tree and user-created siblings are never deleted. Preserve evidence by copying the report first.")
                .setPositiveButton("Delete recorded artifacts", (dialog, which) -> run("Cleanup", this::cleanup))
                .setNegativeButton("Cancel", null).show());
        button(layout, "Copy diagnostic report", () -> {
            String saved = prefs.getString("report", "No observations yet.");
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Totipo storage probe", saved));
        });
        output = new TextView(this);
        output.setTextIsSelectable(true);
        output.setText(prefs.getString("report", "Select a disposable tree to begin. If a provider is absent from the picker, record that manually; no vendor API workaround."));
        layout.addView(output);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        // A restart is observable; do not automatically contact the provider.
        if (prefs.contains("tree")) run("Activity launch (no provider I/O)", () -> log("Stored tree loaded. processSession="
                + PROCESS_SESSION + " sdk=" + android.os.Build.VERSION.SDK_INT
                + "\nUse Reopen persisted tree to test grant/provider/document access."));
    }
    private static final String PROCESS_SESSION = UUID.randomUUID().toString();
    private interface Operation { void execute() throws Exception; }
    private void button(LinearLayout layout, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(view -> action.run());
        buttons.add(button);
        layout.addView(button);
    }
    private void run(String section, Operation operation) {
        for (Button button : buttons) button.setEnabled(false);
        WORKER.execute(() -> {
            try {
                load();
                log("\n" + section + "\n--------\nobservedAt=" + java.time.Instant.now());
                operation.execute();
            } catch (Exception e) {
                log("operation=unavailable/error/unknown exception=" + e.getClass().getSimpleName()
                        + " (message omitted: may contain personal paths/account data)");
            } finally {
                runOnUiThread(() -> {
                    output.setText(prefs.getString("report", "No observations"));
                    for (Button button : buttons) button.setEnabled(true);
                });
            }
        });
    }
    private void load() throws Exception {
        String stored = prefs.getString("tree", null);
        tree = stored == null ? null : Uri.parse(stored);
        docs = tree == null ? null : new ProbeDocuments(getContentResolver(), tree);
        owned = new JSONArray(prefs.getString("owned", "[]"));
        report = prefs.getString("report", "Totipo M1B provider observations v1\nSuccessful test != universal provider guarantee.\nForce-stop != power loss. FileDescriptor.sync != cloud sync completion.\nProvider flags != proven behavior. No Totipo storage/protocol operations.\nIDs and tree names are SHA-256 redacted; unrelated filenames omitted.\n");
    }
    private void log(String value) {
        if (report == null) report = prefs.getString("report", "");
        report += value + "\n";
        // Bound retained diagnostic history; no arbitrary directory dump.
        if (report.length() > 180000) report = "[Earlier observations truncated; copy batches separately]\n" + report.substring(report.length() - 160000);
        prefs.edit().putString("report", report).commit();
    }
    private void requireTree() throws IOException {
        if (tree == null) throw new IOException("No selected disposable tree");
    }
    private void requireMutation() throws IOException {
        requireTree();
        if (prefs.getBoolean("halted", false)) {
            log("STOP FOR DESIGN REVIEW: suspicious replacement/reuse or identity ambiguity previously observed. Mutations disabled for this tree, including cleanup.");
            throw new IOException("Design review required");
        }
    }
    private void halt(String reason) {
        prefs.edit().putBoolean("halted", true).commit();
        log("STOP FOR DESIGN REVIEW: " + reason + ". No further mutations on this tree.");
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_TREE) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            run("Tree picker", () -> log("selection=cancelled/no URI; picker absence must be recorded by human"));
            return;
        }
        Uri selected = data.getData();
        int grants = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        boolean persistable = (data.getFlags() & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0;
        run("Tree selection", () -> {
            if (!DocumentsContract.isTreeUri(selected)) throw new IOException("Picker returned non-tree URI");
            if (!selected.toString().equals(prefs.getString("tree", null))) {
                prefs.edit().putString("tree", selected.toString()).remove("owned").remove("report")
                        .remove("first").remove("halted").commit();
                load();
                log("Tree picker\n--------\nselection=explicit disposable tree\ninvocation=" + UUID.randomUUID());
            }
            log("picker.read=" + ((grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
                    + "\npicker.write=" + ((grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)
                    + "\npicker.persistable=" + persistable);
            if (persistable && grants != 0) {
                try {
                    boolean readGrant = (grants & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
                    boolean writeGrant = (grants & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
                    if (readGrant && writeGrant) getContentResolver().takePersistableUriPermission(selected,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    else if (readGrant) getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    else if (writeGrant) getContentResolver().takePersistableUriPermission(selected, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    log("takePersistableGrant=succeeded");
                }
                catch (Exception e) { log("takePersistableGrant=failed exception=" + e.getClass().getSimpleName()); }
            } else log("takePersistableGrant=not available");
            info();
        });
    }
    private void info() throws Exception {
        requireTree();
        log("authority=" + ProbeLogic.line(tree.getAuthority())
                + "\ntreeUri=content://" + ProbeLogic.line(tree.getAuthority()) + "/tree/[redacted]\nrootDocumentId="
                + ProbeLogic.opaque(DocumentsContract.getTreeDocumentId(tree)));
        int read = checkUriPermission(tree, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        int write = checkUriPermission(tree, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        log("grant.read=" + (read == android.content.pm.PackageManager.PERMISSION_GRANTED)
                + "\ngrant.write=" + (write == android.content.pm.PackageManager.PERMISSION_GRANTED));
        boolean persistedRead = false, persistedWrite = false;
        for (UriPermission grant : getContentResolver().getPersistedUriPermissions()) {
            if (tree.equals(grant.getUri())) { persistedRead |= grant.isReadPermission(); persistedWrite |= grant.isWritePermission(); }
        }
        log("persisted.read=" + persistedRead + "\npersisted.write=" + persistedWrite);
        ProviderInfo provider = getPackageManager().resolveContentProvider(tree.getAuthority(), 0);
        if (provider != null) {
            log("provider.package=" + ProbeLogic.line(provider.packageName));
            try {
                PackageInfo pkg = getPackageManager().getPackageInfo(provider.packageName, 0);
                log("provider.versionName=" + ProbeLogic.line(pkg.versionName) + "\nprovider.versionCode=" + pkg.versionCode);
            } catch (Exception e) { log("provider.version=unknown exception=" + e.getClass().getSimpleName()); }
        } else log("provider.package/version=not observable (possibly package visibility)");
        ProbeDocuments.Row root = docs.query(docs.root());
        log("treeDisplayName=" + ProbeLogic.opaque(root.name) + " (redacted)\nroot.mime=" + ProbeLogic.line(root.mime));
        flags(root.flags);
        syncState(docs.root());
        log("provider.metadata=available (observation only)");
    }
    private void flags(Long value) {
        log("flags=" + value + " (provider-advertised capability)");
        if (value == null) return;
        for (Map.Entry<String, Boolean> flag : ProbeLogic.flags(value).entrySet()) log(flag.getKey() + "=" + flag.getValue());
        log("unknownFlagBits=0x" + Long.toHexString(value & ~0x3ffffL));
    }
    private void syncState(Uri uri) {
        try {
            Long state = docs.syncState(uri);
            log("contentSyncState.flags=" + state + " (optional provider-advertised state; not proven cloud durability)");
            if (state != null) {
                for (Map.Entry<String, Boolean> flag : ProbeLogic.syncFlags(state).entrySet()) log("contentSyncState." + flag.getKey() + "=" + flag.getValue());
                log("contentSyncState.unknownBits=0x" + Long.toHexString(state & ~0x3fL));
            }
        } catch (Exception e) { log("contentSyncState=unknown/unsupported/unavailable exception=" + e.getClass().getSimpleName()); }
    }
    private ProbeDocuments.Snapshot enumerate() throws Exception {
        requireTree();
        ProbeDocuments.Snapshot snapshot = docs.children();
        log("enumeration.directChildren=" + snapshot.rows.size() + "\nenumeration.complete=" + snapshot.complete
                + "\nenumeration.loading=" + snapshot.loading + "\nenumeration.providerError=" + snapshot.providerError);
        int omitted = 0;
        for (ProbeDocuments.Row row : snapshot.rows) {
            if (ownedId(row.id)) row("child", row);
            else omitted++;
        }
        log("unrelated/unownedChildrenOmitted=" + omitted);
        return snapshot;
    }
    private boolean ownedId(String id) throws Exception {
        if (id == null) return false;
        for (int i = 0; i < owned.length(); i++) if (id.equals(owned.getJSONObject(i).getString("id"))) return true;
        return false;
    }
    private void row(String label, ProbeDocuments.Row row) {
        // Actual names are only disclosed if generated by this invocation.
        String name = ProbeLogic.opaque(row.name);
        for (int i = 0; i < owned.length(); i++) {
            JSONObject item = owned.optJSONObject(i);
            if (item != null && item.optString("id").equals(row.id)) {
                name = displayName(row.name, item.optString("requested"));
                break;
            }
        }
        log(label + ".id=" + ProbeLogic.opaque(row.id) + "\n" + label + ".name=" + name
                + "\n" + label + ".mime=" + ProbeLogic.line(row.mime) + "\n" + label + ".size=" + row.size);
        flags(row.flags);
    }
    private String displayName(String actual, String requested) {
        if (actual != null && actual.startsWith(requested)
                && actual.substring(requested.length()).matches("[ .()0-9_-]{0,30}")) return ProbeLogic.line(actual);
        return ProbeLogic.opaque(actual);
    }
    private String name(String kind) { return "totipo-probe-" + kind + "-" + UUID.randomUUID(); }

    /** Ownership requires a fresh ID AND an observed direct child; no name-based deletion. */
    private JSONObject create(String requested, String kind, ProbeDocuments.Snapshot before) throws Exception {
        requireMutation();
        before.requireObservation();
        log("requested=" + requested + "\nexactNameMatchesBefore=" + before.matches(requested));
        Uri returned;
        try { returned = DocumentsContract.createDocument(getContentResolver(), docs.root(), "application/octet-stream", requested); }
        catch (Exception e) { log("create=rejected/error exception=" + e.getClass().getSimpleName()); throw e; }
        if (returned == null) { log("create=null URI / rejected or unknown"); return null; }
        String id = DocumentsContract.getDocumentId(returned);
        log("returnedId=" + ProbeLogic.opaque(id) + "\nreturnedUri=content://" + ProbeLogic.line(returned.getAuthority()) + "/[redacted]");
        if (!tree.getAuthority().equals(returned.getAuthority())
                || DocumentsContract.getTreeDocumentId(tree).equals(id)
                || !ProbeLogic.isNewIdentity(id, before.ids())) {
            halt("createDocument returned an existing identity or different authority; reuse/replacement/unknown");
            return null;
        }
        ProbeDocuments.Snapshot after = docs.children();
        log("exactNameMatchesAfter=" + after.matches(requested) + "\nafter.complete=" + after.complete
                + "\nafter.loading=" + after.loading + "\nafter.providerError=" + after.providerError);
        after.requireObservation();
        log("priorIdsRetained=" + after.ids().containsAll(before.ids())
                + "\nnewIdRowCount=" + java.util.Collections.frequency(after.ids(), id));
        if (java.util.Collections.frequency(after.ids(), id) > 1) {
            halt("duplicate rows for returned identity make observation ambiguous"); return null;
        }
        if (!after.ids().contains(id)) { log("new identity not visible as direct child; no writing/cleanup ownership claimed; manual cleanup may be needed"); return null; }
        if (!after.ids().containsAll(before.ids())) { halt("previous child identity disappeared during create; replacement/unknown"); return null; }
        Uri canonical = docs.document(id);
        ProbeDocuments.Row actual = docs.query(canonical);
        if (!id.equals(actual.id)) { halt("metadata identity differs from returned identity"); return null; }
        if (Document.MIME_TYPE_DIR.equals(actual.mime)) { halt("create returned a directory for a file request; refusing ownership/write/delete"); return null; }
        row("returned", actual);
        log("actualDisplayName=" + displayName(actual.name, requested));
        log("requestedEqualsActual=" + requested.equals(actual.name) + "\nnewUniqueIdObserved=true");
        JSONObject item = new JSONObject().put("tree", tree.toString()).put("uri", canonical.toString())
                .put("id", id).put("requested", requested).put("kind", kind).put("actual", actual.name);
        owned.put(item);
        if (!prefs.edit().putString("owned", owned.toString()).commit()) throw new IOException("Ownership journal persistence failed");
        return item;
    }
    private void createTest() throws Exception {
        requireMutation();
        if (prefs.contains("first")) { log("First create already recorded. Run conflict on it; cleanup first to start another pair."); return; }
        String requested = name("create");
        ProbeDocuments.Snapshot before = enumerate();
        before.requireObservation();
        if (before.matches(requested) != 0) throw new IOException("Name not absent at observation");
        JSONObject item = create(requested, "create", before);
        if (item != null) {
            prefs.edit().putString("first", item.toString()).commit();
            attemptRead(item, "firstRead");
        }
        enumerate();
        log("Observed create only; absence check and create are separate operations, not proof of atomicity.");
    }
    private void conflictTest() throws Exception {
        requireMutation();
        String firstJson = prefs.getString("first", null);
        if (firstJson == null) { log("Run create-new test first."); return; }
        JSONObject first = new JSONObject(firstJson);
        Uri firstUri = ownedUri(first);
        String requested = first.getString("requested");
        ProbeDocuments.Snapshot before = enumerate();
        before.requireObservation();
        ProbeDocuments.Row original = docs.query(firstUri);
        row("originalBefore", original);
        if (!before.ids().contains(first.getString("id")) || !requested.equals(original.name)) {
            log("Cannot establish first document present with requested name; conflict experiment not run."); return;
        }
        byte[] originalBytes = null;
        try { originalBytes = read(firstUri); log("originalBefore.sha256=" + ProbeLogic.sha(originalBytes)); }
        catch (Exception e) { log("originalBefore.content=unavailable exception=" + e.getClass().getSimpleName()); }
        JSONObject second = null;
        try { second = create(requested, "conflict", before); }
        catch (Exception e) { log("secondCreate=error/unknown exception=" + e.getClass().getSimpleName()); }
        ProbeDocuments.Snapshot after = enumerate();
        log("exactNameMatchesAfter=" + after.matches(requested) + "\noriginalIdListedAfter=" + after.ids().contains(first.getString("id")));
        for (ProbeDocuments.Row match : after.rows) if (requested.equals(match.name)) row("exactMatch", match);
        if (second != null) log("observedOutcome=" + (requested.equals(second.optString("actual"))
                ? "second distinct ID with identical display name" : "different display name returned"));
        try {
            ProbeDocuments.Row current = docs.query(firstUri);
            row("originalAfter", current);
            log("original.metadataAccessible=true");
            byte[] currentBytes = read(firstUri);
            log("originalAfter.sha256=" + ProbeLogic.sha(currentBytes));
            if (originalBytes != null) {
                boolean same = Arrays.equals(originalBytes, currentBytes);
                log("original.bytesUnchanged=" + same);
                if (!same) halt("original content changed during same-name create; replacement/external change requires review");
            }
            if (!first.getString("id").equals(current.id) || !requested.equals(current.name)) halt("original identity/name changed during same-name create");
        } catch (Exception e) { log("original.readAfter=unavailable/unknown exception=" + e.getClass().getSimpleName()); }
        if (after.complete && !after.loading && !after.providerError && !after.ids().contains(first.getString("id"))) halt("original identity absent after same-name create");
        log("No intentional write to the original during conflict. Rejection vs provider error may be indistinguishable. Duplicate counts are observations, not a universal uniqueness guarantee.");
    }
    private void materialize(boolean partial) throws Exception {
        requireMutation();
        File stage = new File(getNoBackupFilesDir(), "storage-probe-stage.bin");
        byte[] expected = ProbeLogic.pattern();
        try (FileOutputStream out = new FileOutputStream(stage)) {
            out.write(expected);
            out.flush();
            try { out.getFD().sync(); log("privateStage.sync=succeeded"); }
            catch (IOException e) { log("privateStage.sync=failed exception=" + e.getClass().getSimpleName()); }
        }
        byte[] verified;
        try (InputStream in = new FileInputStream(stage)) { verified = bounded(in); }
        if (!Arrays.equals(expected, verified)) throw new IOException("Private stage verification failed");
        log("privateStage.completeBeforePublication=yes\nprivateStage.bytes=" + verified.length + "\nstage.sha256=" + ProbeLogic.sha(verified));
        String requested = name(partial ? "partial" : "materialize");
        ProbeDocuments.Snapshot before = enumerate();
        before.requireObservation();
        if (before.matches(requested) != 0) throw new IOException("Name not absent");
        JSONObject item = create(requested, partial ? "partial" : "materialize", before);
        if (item == null) return;
        if (!requested.equals(item.optString("actual"))) { log("write=skipped; requested/actual name mismatch"); return; }
        Uri destination = ownedUri(item);
        int wanted = partial ? 256 : verified.length;
        int written = 0;
        ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(destination, "w");
        if (descriptor == null) throw new IOException("Null output descriptor");
        try (FileOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
             FileInputStream in = new FileInputStream(stage)) {
            byte[] buffer = new byte[256];
            while (written < wanted) {
                int count = in.read(buffer, 0, Math.min(buffer.length, wanted - written));
                if (count < 0) throw new IOException("Unexpected stage EOF");
                out.write(buffer, 0, count);
                written += count;
            }
            out.flush();
            log("bytesWritten=" + written);
            try { out.getFD().sync(); log("providerFd.sync=succeeded (does not imply remote/cloud durability)"); }
            catch (IOException e) { log("providerFd.sync=failed/not supported exception=" + e.getClass().getSimpleName()); }
        } catch (Exception e) {
            log("bytesWrittenBeforeError=" + written + " (successful stream writes; provider outcome unknown)");
            throw e;
        }
        log("providerOutput.closed=true");
        attemptRead(item, partial ? "partialRead" : "materializedRead");
        enumerate();
        if (partial) log("controlled incomplete materialization: prefix written and closed intentionally. Relaunch, then Reopen persisted tree to re-enumerate/re-read. This is not a crash or power-loss test.");
    }
    private Uri ownedUri(JSONObject item) throws Exception {
        Map<String, String> identities = new HashMap<>();
        for (int i = 0; i < owned.length(); i++) {
            JSONObject entry = owned.getJSONObject(i);
            identities.put(entry.getString("uri"), entry.getString("id"));
        }
        String uri = item.getString("uri");
        if (!ProbeLogic.owns(tree == null ? null : tree.toString(), item.getString("tree"), uri, identities, item.getString("id"))) throw new IOException("Not a recorded owned identity for selected tree");
        return Uri.parse(uri);
    }
    private void attemptRead(JSONObject item, String label) {
        try {
            Uri uri = ownedUri(item);
            row(label, docs.query(uri));
            syncState(uri);
            byte[] bytes = read(uri);
            log(label + ".accessible=true\n" + label + ".bytesRead=" + bytes.length + "\n" + label + ".sha256=" + ProbeLogic.sha(bytes));
            String kind = item.getString("kind");
            if (kind.equals("materialize") || kind.equals("partial")) {
                byte[] expected = kind.equals("partial") ? Arrays.copyOf(ProbeLogic.pattern(), 256) : ProbeLogic.pattern();
                log(label + ".exactExpectedEquality=" + Arrays.equals(expected, bytes)
                        + "\n" + label + ".fullStageEquality=" + Arrays.equals(ProbeLogic.pattern(), bytes));
            }
        } catch (Exception e) { log(label + "=unavailable/error/unknown exception=" + e.getClass().getSimpleName()); }
    }
    private byte[] read(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Null input stream");
            return bounded(input);
        }
    }
    private byte[] bounded(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        while (bytes.size() <= READ_LIMIT) {
            int count = input.read(buffer, 0, Math.min(buffer.length, READ_LIMIT + 1 - bytes.size()));
            if (count < 0) return bytes.toByteArray();
            if (count == 0) throw new IOException("No progress reading provider stream");
            bytes.write(buffer, 0, count);
        }
        throw new IOException("Read exceeds diagnostic bound");
    }
    private void cleanup() throws Exception {
        requireMutation();
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < owned.length(); i++) {
            JSONObject item = owned.getJSONObject(i);
            boolean deleted = false;
            try {
                Uri uri = ownedUri(item);
                ProbeDocuments.Row current = docs.query(uri);
                if (!item.getString("id").equals(current.id)) throw new IOException("Identity changed; refusing delete");
                if (Document.MIME_TYPE_DIR.equals(current.mime)) throw new IOException("Refusing directory deletion");
                if (current.flags == null || (current.flags & Document.FLAG_SUPPORTS_DELETE) == 0) {
                    log("delete=" + ProbeLogic.opaque(current.id) + " unsupported/not advertised; leave for manual deletion");
                } else {
                    deleted = DocumentsContract.deleteDocument(getContentResolver(), uri);
                    log("delete=" + ProbeLogic.opaque(current.id) + " returned=" + deleted);
                }
            } catch (Exception e) { log("delete=failed/unavailable exception=" + e.getClass().getSimpleName() + "; manual deletion may be needed"); }
            if (!deleted) remaining.put(item);
            else if (item.getString("kind").equals("create")) prefs.edit().remove("first").commit();
        }
        owned = remaining;
        prefs.edit().putString("owned", remaining.toString()).commit();
        log("remainingRecordedArtifacts=" + remaining.length() + "; selected tree never deleted");
    }
}
