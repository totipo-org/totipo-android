package org.totipo.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.junit.Test;
import static org.junit.Assert.*;

/** Source/manifest boundary guards; physical resolution remains a separate qualification. */
public final class OtpAuthProbeSourceGuardTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String PROBE = "org.totipo.android.debug.OtpAuthIntentProbeActivity";

    @Test public void debugFilterIsExactAndReleaseHasNoExternalEntry() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var debug = factory.newDocumentBuilder().parse(Path.of("src/debug/AndroidManifest.xml").toFile());
        Element probe = null;
        var activities = debug.getElementsByTagName("activity");
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (PROBE.equals(activity.getAttributeNS(ANDROID, "name"))) {
                assertNull(probe); probe = activity;
            }
        }
        assertNotNull(probe);
        assertEquals("true", probe.getAttributeNS(ANDROID, "exported"));
        assertEquals("true", probe.getAttributeNS(ANDROID, "stateNotNeeded"));
        var filters = probe.getElementsByTagName("intent-filter");
        assertEquals(2, filters.getLength());
        Element view = (Element) filters.item(0), send = (Element) filters.item(1);
        assertEquals(1, view.getElementsByTagName("action").getLength());
        assertEquals("android.intent.action.VIEW", ((Element) view.getElementsByTagName("action").item(0)).getAttributeNS(ANDROID, "name"));
        var categories = view.getElementsByTagName("category");
        var names = new java.util.HashSet<String>();
        for (int i = 0; i < categories.getLength(); i++) names.add(((Element) categories.item(i)).getAttributeNS(ANDROID, "name"));
        assertEquals(java.util.Set.of("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"), names);
        assertEquals(1, view.getElementsByTagName("data").getLength());
        Element data = (Element) view.getElementsByTagName("data").item(0);
        assertEquals("otpauth", data.getAttributeNS(ANDROID, "scheme"));
        assertEquals("totp", data.getAttributeNS(ANDROID, "host"));
        assertEquals(1, send.getElementsByTagName("action").getLength());
        assertEquals("android.intent.action.SEND", ((Element) send.getElementsByTagName("action").item(0)).getAttributeNS(ANDROID, "name"));
        assertEquals(1, send.getElementsByTagName("category").getLength());
        assertEquals("android.intent.category.DEFAULT", ((Element) send.getElementsByTagName("category").item(0)).getAttributeNS(ANDROID, "name"));
        assertEquals(1, send.getElementsByTagName("data").getLength());
        Element shareData = (Element) send.getElementsByTagName("data").item(0);
        assertEquals("text/plain", shareData.getAttributeNS(ANDROID, "mimeType"));
        assertEquals(1, shareData.getAttributes().getLength());
        for (String sourceSet : List.of("main", "release", "debug")) {
            Path manifestPath = Path.of("src/" + sourceSet + "/AndroidManifest.xml");
            if (!Files.exists(manifestPath)) continue;
            String manifest = Files.readString(manifestPath);
            assertFalse(manifest.contains("android.permission.CAMERA"));
            assertFalse(manifest.contains("android.permission.INTERNET"));
            if (!sourceSet.equals("debug")) {
                assertFalse(manifest.contains("OtpAuthIntentProbe"));
                assertFalse(manifest.contains("otpauth"));
                assertFalse(manifest.contains("android.intent.action.SEND"));
            }
        }
        assertFalse(Files.exists(Path.of("src/main/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java")));
    }

    @Test public void probeDropsTransportDataAndOnlyShowsFixedLabels() throws Exception {
        String source = Files.readString(Path.of("src/debug/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java"));
        for (String forbidden : List.of("Log.", "System.out", "System.err", "printStackTrace", "getMessage()",
                "toString()", "getPath()", "getQuery", "getStringExtra", "putExtra", "putString", "setData(data)",
                "SharedPreferences", "FileOutputStream", "Clipboard", "ContentResolver", "java.net", "getContentResolver", "android.util.Log", "AddTokenRequest", "Base32", "startActivityForResult"))
            assertFalse(forbidden, source.contains(forbidden));
        for (String required : List.of("Intent.ACTION_VIEW.equals(incoming.getAction())", "data != null && data.isHierarchical()",
                "\"otpauth\".equals(data.getScheme())", "\"totp\".equals(data.getHost())", "\"totp\".equals(data.getEncodedAuthority())",
                "incoming.setData(null)", "incoming.replaceExtras((Bundle) null)", "incoming.setClipData(null)",
                "setIntent(new Intent())", "super.onCreate(null)", "setSaveEnabled(false)", "setSaveFromParentEnabled(false)",
                "onSaveInstanceState", "onRestoreInstanceState", "otpauth TOTP link received", "Unsupported link",
                "new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)",
                "!Intent.ACTION_SEND.equals(incoming.getAction())", "!\"text/plain\".equals(incoming.getType())",
                "incoming.getSelector() != null", "extras.size() != 1", "payload instanceof CharSequence",
                "clip.getItemCount() != 1", "item.getUri() != null", "item.getIntent() != null",
                "item.getHtmlText() != null", "!text.equals(copyText(item.getText()))", "streamPresent",
                "MAX_TEXT_LENGTH = 8192", "incoming.removeExtra(Intent.EXTRA_TEXT)",
                "otpauth TOTP share received", "Unsupported shared content")) assertTrue(required, source.contains(required));
        assertFalse(source.contains("super.onSaveInstanceState"));
        assertFalse(source.contains("super.onRestoreInstanceState"));
        assertFalse(source.contains("super.dump("));
        String dump = source.substring(source.indexOf("@Override public void dump"), source.indexOf("private void showResult"));
        for (String forbidden : List.of("PUBLIC_FIXTURE", "getIntent", "incoming", "payload.", "getData", "getExtras"))
            assertFalse(forbidden, dump.contains(forbidden));
        String fields = source.substring(source.indexOf("private enum ActionShape"), source.indexOf("@Override protected void onCreate"));
        assertFalse(fields.contains("private String "));
        assertFalse(fields.contains("private CharSequence "));
        assertFalse(fields.contains("private Intent "));
        assertFalse(fields.contains("private Uri "));
    }

    @Test public void noQrDependencyDeclared() throws Exception {
        for (String name : List.of("build.gradle.kts", "../build.gradle.kts", "../settings.gradle.kts")) {
            String build = Files.readString(Path.of(name)).toLowerCase(java.util.Locale.ROOT);
            for (String forbidden : List.of("zxing", "camerax", "mlkit", "ml-kit", "play-services", "androidx.camera", "qrcode"))
                assertFalse(name + ": " + forbidden, build.contains(forbidden));
        }
    }

    @Test public void qualificationKeepsM2bSupplyChainInputsUnchanged() throws Exception {
        // M2C is a zero-dependency gate. Rebaseline deliberately for a later approved build-input change.
        var baseline = java.util.Map.ofEntries(
                java.util.Map.entry("app/build.gradle.kts", "5252eaa05661e7a6ceab7bc3fd9cde13592e64306b5f68465a4b1035d7fc87b3"),
                java.util.Map.entry("build.gradle.kts", "08ecc23eea4637d5b5d0bf36b9f9d39f67e23fa3a6be57f34f594d6ad8daa348"),
                java.util.Map.entry("settings.gradle.kts", "948017ca7cb553ecfd91a9f3eab56a1db4870c7eb4c0086cb2f4527503f89141"),
                java.util.Map.entry("gradle.properties", "3db21dde187dd9e9cba8400b07e91374f69131754994c004b2810949be97c27b"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.properties", "e81b90975868be2513ca00d6a5a01303a406a5cb01a306210b887e983e4b17ae"),
                java.util.Map.entry("gradle/wrapper/gradle-wrapper.jar", "238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5"),
                java.util.Map.entry("gradle/verification-metadata.xml", "28a3614f5764447de593aa2f1caf298bce9d756fb75e86d9fd6b1cd27281d6eb"),
                java.util.Map.entry("app/gradle.lockfile", "edd64f4193c53b5356998b60b1b739a32254a1c399bc0e32a3ff2a57063a2ca4"),
                java.util.Map.entry("buildscript-gradle.lockfile", "22efaa36cdbc4212cb62beb29837199812865f2b303caaef124b07e9915fc1a0"),
                java.util.Map.entry("flake.nix", "1ee5379470643f1699d65e7ba6ea834ffe4cc277d8df49cc59f68c1311b7a5a9"),
                java.util.Map.entry("flake.lock", "cb6b924b5f79a0b74108aba9666151cef918dbab26c8d3b7bb17fe00b6a3c8ed"),
                java.util.Map.entry("package-deps.json", "62fb17a174f1664c3ea1a50ffe223bd5cd937b78f4209d67e92cdc8b398727fb"));
        for (var entry : baseline.entrySet()) {
            Path input = Path.of("../" + entry.getKey());
            // package.nix deliberately excludes these three evaluator/cache inputs from Gradle's source tree.
            if (!Files.exists(input) && java.util.Set.of("flake.nix", "flake.lock", "package-deps.json").contains(entry.getKey())) continue;
            byte[] contents = Files.readAllBytes(input);
            String actual = java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(contents));
            assertEquals(entry.getKey(), entry.getValue(), actual);
        }
    }
}
