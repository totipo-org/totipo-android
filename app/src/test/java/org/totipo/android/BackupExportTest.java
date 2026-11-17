package org.totipo.android;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.totipo.backup.*;

/** Deterministic document-provider fault coverage, not physical SAF evidence. */
public final class BackupExportTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final byte[] ZIP = new byte[40000];
    private static final BackupExport.Journal JOURNAL = new BackupExport.Journal() {
        public boolean begin() { return true; } public boolean complete() { return true; }
    };
    private BackupExport ready() throws Exception {
        BackupExport operation = new BackupExport(17);
        operation.prepare(new BackupCapture() {
            public BackupSummary summary() {
                try { return new BackupSummary(null, 0, 0, true,
                        java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ZIP))); }
                catch (Exception failure) { throw new AssertionError(failure); }
            }
            public void writeTo(OutputStream output) throws IOException { output.write(ZIP); }
            public void close() {}
        });
        assertTrue(operation.picker()); return operation;
    }
    private static class Document implements BackupExport.Destination {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String fault = ""; BackupExport cancel; int opens;
        public OutputStream openOutput() throws IOException {
            opens++;
            if (fault.equals("open")) throw new IOException("private provider text");
            if (fault.equals("null")) return null;
            return new FilterOutputStream(bytes) {
                public void write(byte[] b, int o, int n) throws IOException {
                    if (fault.equals("write")) { out.write(b, o, 5); throw new IOException(); }
                    out.write(b, o, n); if (cancel != null) cancel.cancel();
                }
                public void flush() throws IOException { if (fault.equals("flush")) throw new IOException(); }
                public void close() throws IOException { if (fault.equals("close")) throw new IOException(); }
            };
        }
        public InputStream openReadback() throws IOException {
            if (fault.equals("unavailable")) throw new IOException();
            if (fault.equals("readNull")) return null;
            byte[] actual = bytes.toByteArray();
            if (fault.equals("short")) actual = java.util.Arrays.copyOf(actual, actual.length - 1);
            if (fault.equals("extra")) actual = java.util.Arrays.copyOf(actual, actual.length + 1);
            if (fault.equals("wrong")) actual[0] ^= 1;
            return new FilterInputStream(new ByteArrayInputStream(actual)) {
                public int read(byte[] b, int o, int n) throws IOException {
                    if (fault.equals("read")) throw new IOException();
                    return fault.equals("zero") ? 0 : super.read(b, o, n);
                }
                public void close() throws IOException { if (fault.equals("readClose")) throw new IOException(); }
            };
        }
    }
    @Test public void completeReadbackAndZeroProgressReads() throws Exception {
        for (String fault : new String[]{"", "zero"}) {
            var op = ready(); var doc = new Document(); doc.fault = fault;
            assertTrue(op.queue()); op.write(doc, JOURNAL);
            assertTrue(op.message(), op.message().startsWith("Backup saved."));
            assertArrayEquals(ZIP, doc.bytes.toByteArray());
            assertFalse(op.queue()); assertFalse(op.picker());
        }
    }
    @Test public void unavailableReadbackIsWrittenUnverified() throws Exception {
        for (String fault : new String[]{"unavailable", "readNull"}) {
            var op = ready(); var doc = new Document(); doc.fault = fault;
            assertTrue(op.queue()); op.write(doc, JOURNAL);
            assertTrue(op.message(), op.message().startsWith("Backup saved, but couldn't be verified."));
        }
    }
    @Test public void failedOpenWriteFlushCloseReadAndMismatchesNeverVerify() throws Exception {
        for (String fault : new String[]{"open", "null", "write", "flush", "close", "read", "readClose", "short", "extra", "wrong"}) {
            var op = ready(); var doc = new Document(); doc.fault = fault;
            assertTrue(op.queue()); op.write(doc, JOURNAL);
            assertTrue(fault + ": " + op.message(), op.message().startsWith("Backup failed."));
            assertFalse(op.active()); assertFalse(op.message().contains("private provider text"));
        }
    }
    @Test public void pickerCancellationAndQueuedCancellationNeverOpenProvider() throws Exception {
        for (boolean queued : new boolean[]{false, true}) {
            var op = ready(); var doc = new Document(); if (queued) assertTrue(op.queue());
            op.cancel(); op.write(doc, JOURNAL);
            assertEquals(0, doc.opens); assertFalse(op.writeStarted());
            assertTrue(op.message().startsWith("Backup canceled."));
        }
    }
    @Test public void cancellationAfterWriteRetainsUncertainVisiblePrefix() throws Exception {
        var op = ready(); var doc = new Document(); doc.cancel = op;
        assertTrue(op.queue()); op.write(doc, JOURNAL);
        assertTrue(doc.bytes.size() > 0); assertTrue(doc.bytes.size() < ZIP.length);
        assertTrue(op.message().contains("empty, incomplete or complete"));
    }
    @Test public void blockedOutputRetainsTaskUntilActualReturnAndStopsNewWrites() throws Exception {
        var op = ready(); var entered = new CountDownLatch(1); var leave = new CountDownLatch(1);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        BackupExport.Destination doc = new BackupExport.Destination() {
            public OutputStream openOutput() {
                entered.countDown();
                try { assertTrue(leave.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
                return new OutputStream() { public void write(int b) { writes.incrementAndGet(); } };
            }
            public InputStream openReadback() { throw new AssertionError(); }
        };
        assertTrue(op.queue()); Thread task = new Thread(() -> op.write(doc, JOURNAL)); task.start();
        assertTrue(entered.await(10, TimeUnit.SECONDS)); op.cancel(); assertTrue(op.active());
        leave.countDown(); task.join(10000); assertFalse(task.isAlive());
        assertFalse(op.active()); assertEquals(0, writes.get()); assertTrue(op.writeStarted());
    }
    @Test public void intentFailurePreventsOutputAndInterruptedMarkerNeverResumesAnything() throws Exception {
        var op = ready(); var doc = new Document(); assertTrue(op.queue());
        op.write(doc, new BackupExport.Journal() {
            public boolean begin() { return false; } public boolean complete() { throw new AssertionError(); }
        });
        assertEquals(0, doc.opens);
        var parent = temporary.newFolder().toPath(); var journal = new BackupExportJournal(parent);
        assertFalse(journal.interrupted()); assertTrue(journal.begin());
        assertTrue(new BackupExportJournal(parent).interrupted());
        assertEquals("totipo-backup-export-v1\n", Files.readString(parent.resolve("backup-export-v1.pending")));
        assertTrue(journal.complete()); assertFalse(journal.interrupted());
        Files.createSymbolicLink(parent.resolve("backup-export-v1.pending"), parent.resolve("unrelated"));
        assertFalse(journal.begin()); assertFalse(journal.complete());
    }
}
