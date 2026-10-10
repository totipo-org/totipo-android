package org.totipo.android;

import android.app.Activity;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.CancellationSignal;

@android.annotation.TargetApi(30)
final class FrameworkBiometricPrompt implements BiometricUnlock.Prompt {
    private final Activity activity;
    FrameworkBiometricPrompt(Activity activity) { this.activity = activity; }
    public Runnable authenticate(BiometricCredentials.Operation operation, Result callback) {
        CancellationSignal cancellation = new CancellationSignal();
        var prompt = new BiometricPrompt.Builder(activity).setTitle("Unlock Totipo")
                .setSubtitle("Use a strong biometric")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButton("Use vault password", activity.getMainExecutor(), (dialog, which) -> callback.cancelled())
                .build();
        prompt.authenticate(new BiometricPrompt.CryptoObject(operation.cipher()), cancellation,
                activity.getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        if (result.getCryptoObject() != null && result.getCryptoObject().getCipher() == operation.cipher()) callback.authorized();
                        else callback.cancelled();
                    }
                    @Override public void onAuthenticationError(int code, CharSequence message) { callback.cancelled(); }
                    // A non-match keeps the framework prompt active, never opens the vault.
                });
        return cancellation::cancel;
    }
}
