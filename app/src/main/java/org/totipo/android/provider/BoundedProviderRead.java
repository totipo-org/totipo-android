package org.totipo.android.provider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.totipo.android.provider.ProviderSnapshot.*;

/** Actual bytes decide the result, never provider size metadata. Owns and closes the input. */
final class BoundedProviderRead {
    private BoundedProviderRead() {}
    interface Opener { InputStream open() throws IOException; }

    static Bytes read(String epoch, Document document, int maximum, Opener opener) {
        ProviderTraversal.checkMaximum(maximum);
        if (Thread.currentThread().isInterrupted()) {
            return new Bytes(epoch, document, maximum, ByteState.UNAVAILABLE, new byte[0], Issue.INTERRUPTED);
        }
        byte[] buffer = new byte[maximum + 1];
        int count = 0;
        ByteState state = ByteState.UNAVAILABLE;
        Issue issue = Issue.NONE;
        boolean opened = false;
        try (InputStream input = opener.open()) {
            if (input == null) {
                issue = Issue.EXCEPTION;
            } else {
                opened = true;
                while (count < buffer.length) {
                    if (Thread.currentThread().isInterrupted()) {
                        issue = Issue.INTERRUPTED;
                        break;
                    }
                    int read = input.read(buffer, count, buffer.length - count);
                    if (read == 0) {
                        int value = input.read();
                        if (value >= 0) { buffer[count++] = (byte) value; continue; }
                        read = -1;
                    }
                    if (read == -1) {
                        state = count == maximum ? ByteState.PRESENT : ByteState.SHORT;
                        break;
                    }
                    count += read;
                }
                if (count == buffer.length) state = ByteState.OVERSIZED;
            }
        } catch (FileNotFoundException e) {
            state = opened ? ByteState.UNAVAILABLE : ByteState.MISSING;
            issue = Issue.EXCEPTION;
        } catch (IOException | RuntimeException e) {
            state = ByteState.UNAVAILABLE;
            issue = Thread.currentThread().isInterrupted() ? Issue.INTERRUPTED : Issue.EXCEPTION;
        }
        return new Bytes(epoch, document, maximum, state, Arrays.copyOf(buffer, count), issue);
    }
}
