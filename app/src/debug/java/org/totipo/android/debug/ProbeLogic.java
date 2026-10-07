package org.totipo.android.debug;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure report/observation logic; no Android or Totipo protocol dependencies. */
final class ProbeLogic {
    private ProbeLogic() {}

    static byte[] pattern() {
        byte[] bytes = new byte[1024];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31 + 7);
        return bytes;
    }

    static String sha(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    // Opaque IDs may contain paths or account identifiers. Full hashes preserve comparisons.
    static String opaque(String value) {
        return value == null ? "unknown" : "sha256:" + sha(value.getBytes(StandardCharsets.UTF_8));
    }

    static String line(String value) {
        return value == null ? "unknown" : value.replace('\n', ' ').replace('\r', ' ')
                .replaceAll("[\\p{Cntrl}]", "?");
    }

    static Map<String, Boolean> flags(long flags) {
        String[] names = {"supportsThumbnail", "supportsWrite", "supportsDelete", "supportsCreate",
                "dirPrefersGrid", "dirPrefersLastModified", "supportsRename", "supportsCopy",
                "supportsMove", "virtualDocument", "supportsRemove", "supportsSettings",
                "webLinkable", "partialDocument", "supportsMetadata", "dirBlocksOpenDocumentTree", "supportsTrash", "supportsRestore"};
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (int bit = 0; bit < names.length; bit++) result.put(names[bit], (flags & (1L << bit)) != 0);
        return result;
    }

    static Map<String, Boolean> syncFlags(long flags) {
        String[] names = {"availableLocally", "localChanges", "uploadProgress", "downloadProgress", "uploadError", "downloadError"};
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (int bit = 0; bit < names.length; bit++) result.put(names[bit], (flags & (1L << bit)) != 0);
        return result;
    }

    static int exactMatches(Collection<String> names, String requested) {
        if (requested == null) throw new IllegalArgumentException("Unknown requested name");
        int count = 0;
        for (String name : names) if (requested.equals(name)) count++;
        return count;
    }

    static boolean isNewIdentity(String id, Collection<String> before) {
        return id != null && !before.contains(id);
    }

    static boolean owns(String selectedTree, String recordedTree, String uri,
                        Map<String, String> recordedUriIds, String id) {
        return selectedTree != null && selectedTree.equals(recordedTree) && id != null
                && Objects.equals(recordedUriIds.get(uri), id);
    }
}
