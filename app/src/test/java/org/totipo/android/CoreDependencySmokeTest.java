package org.totipo.android;

import org.junit.Test;
import org.totipo.VaultSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CoreDependencySmokeTest {
    @Test
    public void releasedPublicApiLoads() {
        assertEquals("org.totipo.VaultSession", VaultSession.class.getName());
        assertTrue(AutoCloseable.class.isAssignableFrom(VaultSession.class));
    }
}
