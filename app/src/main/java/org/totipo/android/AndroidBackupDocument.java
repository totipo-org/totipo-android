package org.totipo.android;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.*;

/** Operation-scoped SAF adapter. Constructed with Application resolver; invoked only
 * on the bounded provider lane, never by Activity presentation or canonical storage. */
final class AndroidBackupDocument implements BackupExport.Destination {
    private final ContentResolver resolver;
    private final Uri uri;
    AndroidBackupDocument(ContentResolver resolver, Uri uri) { this.resolver = resolver; this.uri = uri; }
    public OutputStream openOutput() throws IOException { return resolver.openOutputStream(uri, "w"); }
    public InputStream openReadback() throws IOException { return resolver.openInputStream(uri); }
}
