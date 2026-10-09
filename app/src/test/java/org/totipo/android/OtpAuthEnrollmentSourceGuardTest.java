package org.totipo.android;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.junit.Test;
import static org.junit.Assert.*;

/** Static tripwires only; these do not prove runtime erasure or whole-program information flow. */
public final class OtpAuthEnrollmentSourceGuardTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    @Test public void onlyProductionHandlerHasExactSeparateFilters() throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(Path.of("src/main/AndroidManifest.xml").toFile());
        var activities = doc.getElementsByTagName("activity"); Element ingress = null;
        for (int i = 0; i < activities.getLength(); i++) {
            Element activity = (Element) activities.item(i);
            if (".OtpAuthEnrollmentActivity".equals(activity.getAttributeNS(ANDROID, "name"))) {
                assertNull(ingress); ingress = activity;
            } else assertEquals(0, activity.getElementsByTagName("data").getLength());
        }
        assertNotNull(ingress); assertEquals("true", ingress.getAttributeNS(ANDROID, "exported"));
        assertEquals("@string/enrollment_label", ingress.getAttributeNS(ANDROID, "label"));
        var filters = ingress.getElementsByTagName("intent-filter"); assertEquals(2, filters.getLength());
        for (int i = 0; i < 2; i++) {
            Element filter = (Element) filters.item(i);
            assertEquals(1, filter.getElementsByTagName("action").getLength());
            assertEquals("android.intent.action." + (i == 0 ? "VIEW" : "SEND"),
                    ((Element) filter.getElementsByTagName("action").item(0)).getAttributeNS(ANDROID, "name"));
            Set<String> names = new java.util.HashSet<>(); var categories = filter.getElementsByTagName("category");
            for (int j = 0; j < categories.getLength(); j++) names.add(((Element) categories.item(j)).getAttributeNS(ANDROID, "name"));
            assertEquals(i == 0 ? Set.of("android.intent.category.DEFAULT", "android.intent.category.BROWSABLE")
                    : Set.of("android.intent.category.DEFAULT"), names);
            assertEquals(1, filter.getElementsByTagName("data").getLength());
            Element data = (Element) filter.getElementsByTagName("data").item(0);
            if (i == 0) {
                assertEquals("otpauth", data.getAttributeNS(ANDROID, "scheme"));
                assertEquals("totp", data.getAttributeNS(ANDROID, "host")); assertEquals(2, data.getAttributes().getLength());
            } else { assertEquals("text/plain", data.getAttributeNS(ANDROID, "mimeType")); assertEquals(1, data.getAttributes().getLength()); }
        }
        String debug = Files.readString(Path.of("src/debug/AndroidManifest.xml"));
        assertFalse(debug.contains("otpauth")); assertFalse(debug.contains("OtpAuthIntentProbe"));
        assertFalse(Files.exists(Path.of("src/debug/java/org/totipo/android/debug/OtpAuthIntentProbeActivity.java")));
    }
    @Test public void ingressHasNoAlternateAuthorshipOrPersistence() throws Exception {
        for (String name : List.of("OtpAuthUriParser", "OtpAuthTransport", "OtpAuthEnrollmentActivity")) {
            String source = Files.readString(Path.of("src/main/java/org/totipo/android/" + name + ".java"));
            for (String forbidden : List.of("Log.", "System.out", "System.err", "printStackTrace", "getMessage()",
                    "Intent.toString", "getQueryParameter", "URLDecoder", "putString", "putCharArray", "putByteArray",
                    "SharedPreferences", "FileOutputStream", "Clipboard", "TokenEditor", "createToken(", ".save(",
                    "VaultSession", "controller.unlock", "controller.create", "startActivityForResult"))
                assertFalse(name + ": " + forbidden, source.contains(forbidden));
        }
        String activity = Files.readString(Path.of("src/main/java/org/totipo/android/OtpAuthEnrollmentActivity.java"));
        assertTrue(activity.contains("controller.addToken(request)")); assertTrue(activity.contains("draft.transfer()"));
        assertTrue(activity.contains("setIntent(new Intent())")); assertTrue(activity.contains("super.onCreate(null)"));
        assertTrue(activity.contains("savedState == null")); assertTrue(activity.contains("savedState != null"));
        assertFalse(activity.contains("super.onSaveInstanceState")); assertFalse(activity.contains("super.dump("));
        assertTrue(activity.contains("state.putBoolean(\"consumed\", true)"));
        String transport = Files.readString(Path.of("src/main/java/org/totipo/android/OtpAuthTransport.java"));
        for (String required : List.of("incoming.removeExtra(Intent.EXTRA_TEXT)", "incoming.replaceExtras((Bundle) null)",
                "incoming.setData(null)", "incoming.setClipData(null)", "incoming.setSelector(null)")) assertTrue(transport.contains(required));
    }
}
