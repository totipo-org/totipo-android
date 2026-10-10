package org.totipo.android;

/** Bounded Activity-owned prompt transaction. Cancellation wipes enrollment and leaves password fallback. */
final class BiometricUnlock implements AutoCloseable {
    interface Prompt {
        interface Result { void authorized(); void cancelled(); }
        Runnable authenticate(BiometricCredentials.Operation operation, Result result);
    }
    private final AndroidVaultController controller;
    private final BiometricCredentials credentials;
    private final Prompt prompt;
    private final InactivityLock.Scheduler scheduler;
    private Runnable cancelPrompt, cancelDeadline;
    Runnable changed = () -> {};
    private PasswordBuffer password;
    private boolean enrolling, active;
    private long generation;
    BiometricUnlock(AndroidVaultController controller, BiometricCredentials credentials, Prompt prompt,
                    InactivityLock.Scheduler scheduler) {
        this.controller = controller; this.credentials = credentials; this.prompt = prompt; this.scheduler = scheduler;
    }
    boolean available() { return credentials.available(); }
    boolean configured() { return credentials.configured(); }
    void enroll(AndroidVaultController.Enrollment enrollment) {
        close();
        password = enrollment.password(); enrolling = true;
        try { start(credentials.enrollment(enrollment.vaultId())); }
        catch (Exception unavailable) { invalidate(); close(); changed.run(); }
    }
    void unlock() {
        close();
        if (!available() || !configured()) return;
        try { start(credentials.unlock()); }
        catch (Exception unavailable) { invalidate(); close(); changed.run(); }
    }
    private void start(BiometricCredentials.Operation operation) {
        active = true;
        long current = ++generation;
        cancelDeadline = scheduler.after(60_000, this::close);
        cancelPrompt = prompt.authenticate(operation, new Prompt.Result() {
            public void authorized() {
                if (!active || current != generation) return;
                char[] recovered = null;
                try {
                    if (enrolling) {
                        if (controller.snapshot().state() == AndroidVaultController.State.OPEN) operation.encrypt(password);
                    } else {
                        recovered = operation.decrypt();
                        controller.unlockBiometric(recovered, operation.vaultId(), BiometricUnlock.this::invalidate);
                        recovered = null; // Controller owns and wipes every admitted/rejected buffer.
                    }
                } catch (Exception invalid) { invalidate(); }
                finally { if (recovered != null) java.util.Arrays.fill(recovered, '\0'); close(); changed.run(); }
            }
            public void cancelled() { if (current == generation) { close(); changed.run(); } }
        });
    }
    boolean disable() {
        close();
        try { credentials.delete(); return !credentials.configured(); }
        catch (Exception unavailable) { return false; }
    }
    private void invalidate() { try { credentials.delete(); } catch (Exception unavailable) { /* fail closed on next crypto preparation */ } }
    @Override public void close() {
        boolean deletePending = enrolling;
        active = false; generation++;
        if (cancelDeadline != null) cancelDeadline.run(); cancelDeadline = null;
        if (cancelPrompt != null) cancelPrompt.run(); cancelPrompt = null;
        if (password != null) password.close(); password = null;
        enrolling = false;
        if (deletePending && !credentials.configured()) invalidate();
    }
}
