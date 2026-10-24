package org.totipo.android;

/** Activity-owned prompt. Every callback targets its captured controller and request. */
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
    private AndroidVaultController.Enrollment enrollment;
    private AndroidVaultController.BiometricRequest request;
    private boolean active;
    private long generation;
    private volatile String message = "";
    BiometricUnlock(AndroidVaultController controller, BiometricCredentials credentials, Prompt prompt,
                    InactivityLock.Scheduler scheduler) {
        if (!controller.localId().equals(credentials.localId())) throw new IllegalArgumentException("Biometric entry ownership mismatch");
        this.controller = controller; this.credentials = credentials; this.prompt = prompt; this.scheduler = scheduler;
    }
    boolean available() { return credentials.available(); }
    boolean configured() { return credentials.configured(); }
    String message() { return message; }
    void automaticUnlock() {
        // Consume even an unavailable/unenrolled opportunity: hardware recovery
        // requires an explicit retry, never a render/foreground retry loop.
        if (controller.claimAutomaticBiometricUnlock()) unlock();
    }
    void enroll(AndroidVaultController.Enrollment enrollment) {
        close();
        this.enrollment = enrollment; password = enrollment.password();
        try {
            // Validate authority before even preparing this entry's key operation.
            if (!controller.completeBiometricEnrollment(enrollment, () -> {})) { close(); return; }
            start(credentials.enrollment(enrollment.vaultId()));
        } catch (Exception unavailable) { failed(); close(); changed.run(); }
    }
    void unlock() {
        close();
        if (!available() || !configured()) return;
        request = controller.beginBiometricUnlock();
        if (request == null) return;
        try { start(credentials.unlock()); }
        catch (Exception unavailable) { failed(); close(); changed.run(); }
    }
    private void failed() {
        message = "Biometric unlock unavailable. Use the vault password. Disable biometric unlock before recovery or reenrollment.";
    }
    private void start(BiometricCredentials.Operation operation) {
        active = true; message = "";
        long current = ++generation;
        var originEnrollment = enrollment;
        var originRequest = request;
        cancelDeadline = scheduler.after(60_000, () -> { close(); changed.run(); });
        Runnable cancellation = prompt.authenticate(operation, new Prompt.Result() {
            public void authorized() {
                if (!active || current != generation) return;
                char[] recovered = null;
                try {
                    if (originEnrollment != null) {
                        if (!originEnrollment.vaultId().equals(operation.vaultId())) throw new IllegalStateException("Enrollment identity mismatch");
                        if (controller.completeBiometricEnrollment(originEnrollment, () -> operation.encrypt(password)))
                            message = "Biometric unlock enabled for this vault.";
                    } else {
                        // Recheck before decryption and again at controller mutation/open boundaries.
                        if (!controller.biometricCurrent(originRequest)) return;
                        recovered = operation.decrypt();
                        controller.unlockBiometric(originRequest, recovered, operation.vaultId(), BiometricUnlock.this::failed);
                        recovered = null; // Controller owns/wipes every admitted or rejected buffer.
                    }
                } catch (Exception invalid) { failed(); }
                finally { if (recovered != null) java.util.Arrays.fill(recovered, '\0'); finishPrompt(); changed.run(); }
            }
            public void cancelled() { if (current == generation) { close(); changed.run(); } }
        });
        if (active && current == generation) cancelPrompt = cancellation;
        else cancellation.run(); // Also safe with synchronous test callbacks.
    }
    boolean disable() {
        close(); controller.cancelBiometricEnrollment();
        if (!controller.selectedAuthority()) return false;
        try { credentials.delete(); message = "Biometric unlock disabled for this vault."; changed.run(); return true; }
        catch (Exception unavailable) { message = "Biometric cleanup incomplete. Retry Disable biometric unlock."; changed.run(); return false; }
    }
    private void finishPrompt() {
        active = false; generation++;
        if (cancelDeadline != null) cancelDeadline.run(); cancelDeadline = null;
        if (cancelPrompt != null) cancelPrompt.run(); cancelPrompt = null;
        if (password != null) password.close(); password = null;
        enrollment = null;
    }
    @Override public void close() {
        controller.cancelBiometricUnlock(request); request = null;
        finishPrompt();
    }
}
