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
        int number = entries.indexOf(entry) + 1;
        String name = entry.label() == null ? "Vault " + number : entry.label();
        long same = entries.stream().filter(e -> java.util.Objects.equals(e.label(), entry.label())).count();
        return entry.label() != null && same > 1 ? name + " · Local entry " + number : name;
    }
}
