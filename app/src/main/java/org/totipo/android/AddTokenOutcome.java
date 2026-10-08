package org.totipo.android;

import org.totipo.SaveResult;

/** Detached product outcome; preserves Java's definite failure reason without ownership. */
public record AddTokenOutcome(Status status, SaveResult.Reason reason) {
    public enum Status { ADDED, INVALID_SECRET, INVALID_FIELDS, CONFLICT, FAILED, PUBLICATION_UNCERTAIN,
        SESSION_UNAVAILABLE, BUSY }
}
