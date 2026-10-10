package org.totipo.android;

import javax.crypto.Cipher;

/** Framework crypto/storage seam; no Android objects are required by host tests. */
interface BiometricCredentials {
    interface Operation {
        Cipher cipher();
        String vaultId();
        void encrypt(PasswordBuffer password) throws Exception;
        char[] decrypt() throws Exception;
    }
    boolean available();
    boolean configured();
    Operation enrollment(String vaultId) throws Exception;
    Operation unlock() throws Exception;
    void delete() throws Exception;
}
