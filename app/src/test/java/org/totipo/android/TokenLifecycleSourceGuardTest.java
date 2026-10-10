package org.totipo.android;

import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.totipo.*;
import org.totipo.android.reconcile.ForegroundVaultCoordinator.ObservedToken;
import static org.junit.Assert.*;

public final class TokenLifecycleSourceGuardTest {
    private String read(String name) throws Exception { return Files.readString(Path.of("src/main/java/org/totipo/android/"+name+".java")); }
    @Test public void mutationHasNoTransportOrSecretExportAndDiscardsUnsafeRetryCapabilities() throws Exception {
        String coordinator=read("reconcile/ForegroundVaultCoordinator");
        String mutation=coordinator.substring(coordinator.indexOf("public TokenChange.Result changeToken"),coordinator.indexOf("private static ObservedToken project"));
        for(String forbidden:List.of("providerIo", "publishLocalChanges", "importProviderChanges", "outbound", "bridge()", "Files.", "NewSecret", "decodeSecret", "Totipo.open", "requestRefresh")) assertFalse(forbidden,mutation.contains(forbidden));
        assertTrue(mutation.contains("captured.merge(token.id())"));assertTrue(mutation.contains("editor.keep(token.alternatives().get(option))"));
        assertTrue(mutation.contains("conflict.resolution().close()"));assertTrue(mutation.contains("uncertain.retry().close()"));
        assertFalse(mutation.contains("resolution().save()"));assertFalse(mutation.contains("retry().retry()"));
        String controller=read("AndroidVaultController");
        String command=controller.substring(controller.indexOf("public synchronized boolean confirmTokenChange"),controller.indexOf("private ForegroundVaultCoordinator vault"));
        for(String forbidden:List.of("providerIo", "publishLocalChanges", "importProviderChanges", "startProvider", "outbound")) assertFalse(forbidden,command.contains(forbidden));
    }
    @Test public void nativeActionsConfirmationUnselectedChoicesAndAccessibilityLabels() throws Exception {
        String activity=read("MainActivity"),adapter=read("TokenListAdapter");
        for(String label:List.of("Edit", "Delete…", "Resolve", "Token actions: Edit or Delete"))assertTrue(label,adapter.contains(label));
        for(String label:List.of("Delete this token?", "Historical encrypted revisions may remain", "Save", "Cancel", "Option ", "Changing setup is not available yet"))assertTrue(label,activity.contains(label));
        assertTrue(activity.contains("setSingleChoiceItems(options, -1"));assertTrue(activity.contains("setLabelFor(field.getId())"));
        assertTrue(activity.contains("setTextColor(android.graphics.Color.rgb(176, 0, 32))"));
        assertTrue(activity.contains("onStop() { clearSecret(); dismissTokenChange()"));
        assertTrue(adapter.contains("TokenStatus.ACTIVE)).filter"));
        assertFalse(activity.contains("onSaveInstanceState"));
    }
    @Test public void secretOnlyConflictStillHasTwoChoicesAndNoCodeOrEdit() {
        var descriptor=new TokenDescriptor(TokenStatus.ACTIVE,"Public <&>","account",TotpAlgorithm.SHA1,6,Duration.ofSeconds(30));
        var token=new ObservedToken(new TokenId("a".repeat(64)),List.of(descriptor,descriptor),List.of(),List.of(),true);
        assertEquals(2,token.alternatives().size());assertTrue(TokenChange.resolvable(token));assertFalse(TokenChange.live(token));assertFalse(TokenListAdapter.usable(token));
        assertTrue(TokenListAdapter.rowText(token).contains("Token conflict"));
        var deleted=new TokenDescriptor(TokenStatus.TOMBSTONED,"retained issuer","retained account",TotpAlgorithm.SHA1,6,Duration.ofSeconds(30));
        assertEquals("Deleted",TokenListAdapter.summary(deleted));
        assertFalse(token.toString().contains("secret"));
    }
}
