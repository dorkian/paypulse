package com.paypulse.gateway.correlation;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Decides which correlation id a request gets at the public edge.
 *
 * <p>Pure function: no web classes, no state. That makes the rule trivial to unit test,
 * and this rule is the part worth testing hard (S11.1).
 *
 * <p>Anything coming from outside is untrusted input (S8.3). A 10,000-character header
 * would bloat every log line of that request, and odd characters (a newline, for example)
 * could forge extra log lines. So we keep only short, safe ids and replace the rest.
 * Internal services can then trust what the gateway forwards.
 */
public final class CorrelationIds {

    // Letters, digits and hyphen, 1 to 64 characters. A UUID (36 chars) fits.
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    // Utility class: nobody should create an instance.
    private CorrelationIds() {
    }

    /**
     * @param incoming the value of the X-Correlation-Id header, or null if absent
     * @return the same value if it is safe, otherwise a fresh random UUID
     */
    public static String resolve(String incoming) {
        // matches() must match the WHOLE text. With find() a value like "abc\n" would
        // slip through, because "$" is allowed to match just before a trailing newline.
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        // Covers null, blank, too long, and any forbidden character.
        return UUID.randomUUID().toString();
    }
}
