package com.prorocketeers.lukas.routing.api;

/**
 * Strips control characters (notably CR/LF) from untrusted values before they're written to the log, so a
 * request path segment like {@code BEL%0d%0aFAKE LOG LINE} can't forge what looks like a second, unrelated
 * log entry.
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        return value == null ? null : value.replaceAll("\\p{Cntrl}", "_");
    }
}
