package org.totipo.android.sync;

import java.util.*;
import org.totipo.android.provider.ImmutableCandidateClassifier;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Pure policy. Inbound can admit independent safe candidates from incomplete coverage;
 * outbound requires complete absence evidence. Contradiction anywhere stops the whole batch;
 * ordinary blocked IDs do not prevent unrelated missing objects from proceeding. */
public final class OutboundImmutablePlanner {
    private OutboundImmutablePlanner() {}
    public enum Outcome { ALREADY_PRESENT_EXACT, MISSING_SAFE_TO_CREATE,
        BLOCKED_EXISTING_CANONICAL_CANDIDATE, INTEGRITY_CONTRADICTION, VALIDATION_UNAVAILABLE }
    public record Item(DetachedImmutableObject object, Outcome outcome) {}
    public record Plan(Document target, List<Item> items, String limitation) {
        public Plan { items = List.copyOf(items); }
        public List<DetachedImmutableObject> missing() {
            return limitation != null ? List.of() : items.stream()
                    .filter(i -> i.outcome() == Outcome.MISSING_SAFE_TO_CREATE).map(Item::object).collect(java.util.stream.Collectors.toList());
        }
        public boolean blocked() { return items.stream().anyMatch(i -> i.outcome() != Outcome.ALREADY_PRESENT_EXACT
                && i.outcome() != Outcome.MISSING_SAFE_TO_CREATE); }
    }
    public static Plan plan(List<DetachedImmutableObject> local, ImmutableCandidateClassifier.Result evidence, boolean write) {
        Scan scan = evidence.transport();
        if (!write) return new Plan(null, List.of(), "Folder is read-only for Totipo.");
        if (scan.state() != State.COMPLETE) return new Plan(null, List.of(), "Provider view incomplete; nothing published.");
        var directories = scan.root().rows().stream().filter(d -> d.isDirectory() && "objects-v1".equals(d.displayName())).collect(java.util.stream.Collectors.toList());
        if (directories.size() != 1) return new Plan(null, List.of(), directories.isEmpty()
                ? "Publishing requires an existing objects-v1 folder." : "Ambiguous objects-v1 folders; nothing published.");
        Document target = directories.get(0);
        // Provider child-creation support is independent of the persisted URI grant.
        if (!target.tree().equals(scan.tree()) || target.locator() == null || target.id() == null
                || target.flags() == null || (target.flags() & android.provider.DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) == 0)
            return new Plan(null, List.of(), "This folder does not support publishing changes.");
        var directory = scan.directories().stream().filter(d -> d.document().equals(target)).findFirst().orElse(null);
        if (directory == null || directory.children().state() != State.COMPLETE)
            return new Plan(null, List.of(), "Provider view incomplete; nothing published.");
        boolean integrity = evidence.groups().stream().anyMatch(g -> g.authenticated() == ImmutableCandidateClassifier.Authenticated.INTEGRITY_CONTRADICTION);
        List<Item> items = new ArrayList<>();
        for (var object : local.stream().sorted(Comparator.comparing(o -> o.id().hex())).collect(java.util.stream.Collectors.toList())) {
            var group = evidence.groups().stream().filter(g -> g.objectId().equals(object.id())).findFirst().orElse(null);
            Outcome outcome;
            if (group != null && group.authenticated() == ImmutableCandidateClassifier.Authenticated.INTEGRITY_CONTRADICTION) {
                outcome = Outcome.INTEGRITY_CONTRADICTION; integrity = true;
            } else if (group != null && group.representation() != null) {
                if (Arrays.equals(object.representation(), group.representation().representation())) outcome = Outcome.ALREADY_PRESENT_EXACT;
                else { outcome = Outcome.INTEGRITY_CONTRADICTION; integrity = true; }
            } else if (group != null && group.siblings().stream().anyMatch(c -> c.kind() == ImmutableCandidateClassifier.Kind.VALIDATION_UNAVAILABLE)) {
                outcome = Outcome.VALIDATION_UNAVAILABLE;
            } else if (directory.children().rows().stream().anyMatch(d -> object.id().hex().equals(d.displayName()))) {
                outcome = Outcome.BLOCKED_EXISTING_CANONICAL_CANDIDATE;
            } else outcome = Outcome.MISSING_SAFE_TO_CREATE;
            items.add(new Item(object, outcome));
        }
        return new Plan(target, items, integrity ? "Integrity problem. Nothing published." : null);
    }
    /** Fresh authenticated namespace evidence, not mutation returns, establishes success.
     * All originally missing objects must be exact; an unattempted suffix race stays uncertain. */
    public enum Verification { VERIFIED, INTEGRITY_CONTRADICTION, NOT_VERIFIED, NOT_OBSERVED, UNCERTAIN }
    public static Verification verify(DetachedImmutableObject object, ImmutableCandidateClassifier.Result postflight) {
        var group = postflight.groups().stream().filter(g -> g.objectId().equals(object.id())).findFirst().orElse(null);
        if (group != null && group.authenticated() == ImmutableCandidateClassifier.Authenticated.INTEGRITY_CONTRADICTION)
            return Verification.INTEGRITY_CONTRADICTION;
        if (group != null && group.representation() != null)
            return Arrays.equals(object.representation(), group.representation().representation())
                    ? Verification.VERIFIED : Verification.INTEGRITY_CONTRADICTION;
        if (group != null) return Verification.NOT_VERIFIED;
        boolean canonical = postflight.transport().directories().stream().flatMap(d -> d.children().rows().stream())
                .anyMatch(d -> object.id().hex().equals(d.displayName()));
        if (canonical) return Verification.NOT_VERIFIED;
        return postflight.transport().state() == State.COMPLETE ? Verification.NOT_OBSERVED : Verification.UNCERTAIN;
    }
    public static String confirm(Plan intended, List<DetachedImmutableObject> attempted, ImmutableCandidateClassifier.Result postflight) {
        boolean all = attempted.size() == intended.missing().size();
        if (postflight.groups().stream().anyMatch(g -> g.authenticated() == ImmutableCandidateClassifier.Authenticated.INTEGRITY_CONTRADICTION))
            return "Integrity problem. Publication needs attention.";
        for (var object : intended.missing()) {
            Verification verification = verify(object, postflight);
            if (verification == Verification.INTEGRITY_CONTRADICTION)
                return "Integrity problem. Publication needs attention.";
            all &= verification == Verification.VERIFIED;
        }
        if (all && !intended.blocked()) return "Local changes published";
        if (all) return "Changes published; existing provider candidates still block some local changes.";
        return "Publication uncertain. Check again before retrying.";
    }
}
