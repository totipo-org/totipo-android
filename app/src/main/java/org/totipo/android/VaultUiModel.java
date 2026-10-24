package org.totipo.android;

import java.util.List;

/** Non-secret startup/navigation policy; session authority remains in VaultSelection. */
final class VaultUiModel {
    private VaultUiModel() {}
    static String startupSelection(List<VaultRegistry.Entry> entries, String lastSelected) {
        var remembered = entries.stream().filter(e -> e.state() != VaultRegistry.State.DETACHED).collect(java.util.stream.Collectors.toList());
        if (remembered.size() != 1) return null;
        var only = remembered.get(0);
        return only.state() == VaultRegistry.State.ESTABLISHED && only.id().equals(lastSelected) ? only.id() : null;
    }
    static String name(List<VaultRegistry.Entry> entries, VaultRegistry.Entry entry) {
        if (entry.label() != null) return entry.label();
        int length = 6;
        for (var other : entries) {
            if (other.id().equals(entry.id())) continue;
            int common = 0;
            while (common < entry.id().length() && entry.id().charAt(common) == other.id().charAt(common)) common++;
            length = Math.max(length, common + 1);
        }
        return entry.id().substring(0, length).toUpperCase(java.util.Locale.ROOT) + " · Unnamed";
    }

    static String optionalLabel(String value) {
        return value == null || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)) ? null : value;
    }
}
