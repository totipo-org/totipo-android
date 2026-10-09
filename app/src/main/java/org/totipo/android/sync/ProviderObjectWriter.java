package org.totipo.android.sync;

import java.io.OutputStream;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.totipo.android.provider.ProviderSnapshot.*;

/** CREATE-ONLY transport, called exclusively on the sole provider lane. A created handle
 * is operation-local; preflight locators never reach output(). No cleanup or retry. */
public final class ProviderObjectWriter {
    private ProviderObjectWriter() {}
    public interface Port {
        Document create(Document parent, String name) throws Exception;
        Document metadata(Document created) throws Exception;
        OutputStream output(Document created) throws Exception;
        Bytes readBack(Document created) throws Exception;
    }
    public record Result(List<DetachedImmutableObject> attempted, boolean uncertain, boolean unsupportedName, boolean readBackFailed, Scan postflight) {
        public Result { attempted = List.copyOf(attempted); }
    }
    public static Result publish(SyncFolderBinding.Port transport, String tree, Document parent,
                                 List<DetachedImmutableObject> missing, BooleanSupplier cancelled) {
        List<DetachedImmutableObject> attempted = new ArrayList<>();
        boolean uncertain = false, unsupportedName = false, readBackFailed = false;
        try {
            Port writer = (Port) transport;
            for (var object : missing) {
                if (cancelled.getAsBoolean()) break;
                if (!transport.grants(tree).write()) throw new IllegalStateException();
                attempted.add(object); // Even create failure may have a remote side effect.
                Document created = writer.create(parent, object.id().hex());
                if (created == null) throw new IllegalStateException();
                if (cancelled.getAsBoolean()) break;
                Document metadata = writer.metadata(created);
                if (metadata != null && !object.id().hex().equals(metadata.displayName())) unsupportedName = true;
                if (metadata == null || metadata.isDirectory() || !object.id().hex().equals(metadata.displayName())
                        || !parent.tree().equals(metadata.tree()) || !Objects.equals(parent.id(), metadata.parentId())
                        || !Objects.equals(created.id(), metadata.id())) throw new IllegalStateException();
                if (cancelled.getAsBoolean()) break;
                if (!transport.grants(tree).write()) throw new IllegalStateException();
                try (OutputStream output = writer.output(created)) {
                    if (output == null) throw new IllegalStateException();
                    // Lock may occur inside write; it cannot roll this remote operation back.
                    output.write(object.representation());
                }
                if (cancelled.getAsBoolean()) break;
                readBackFailed = true;
                Bytes read = writer.readBack(created);
                if (read == null || read.state() != ByteState.PRESENT
                        || !Arrays.equals(read.bytes(), object.representation())) throw new IllegalStateException();
                readBackFailed = false;
            }
        } catch (Throwable unavailable) { uncertain = true; }
        Scan postflight = null;
        if (!cancelled.getAsBoolean()) {
            try { postflight = transport.scan(tree); }
            catch (Throwable unavailable) { uncertain = true; }
        }
        return new Result(attempted, uncertain, unsupportedName, readBackFailed, postflight);
    }
}
