package com.paypulse.gateway.correlation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdsTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc-123", "A", "ABC-def-789", "1234567890"})
    void validIdIsKeptUnchanged(String incoming) {
        assertThat(CorrelationIds.resolve(incoming)).isEqualTo(incoming);
    }

    @Test
    void validIdOfExactly64CharactersIsKept() {
        String max = "a".repeat(64);
        assertThat(CorrelationIds.resolve(max)).isEqualTo(max);
    }

    @Test
    void aUuidIsKept() {
        String uuid = UUID.randomUUID().toString();
        assertThat(CorrelationIds.resolve(uuid)).isEqualTo(uuid);
    }

    // Each of these must be replaced by a brand-new valid UUID.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",                  // empty
            "   ",               // blank
            "has space",         // forbidden character: space
            "abc\n",             // trailing newline: the regex trap (log forging)
            "abc\nFAKE LOG LINE",// newline inside: log injection
            "abc_def",           // underscore is not allowed
            "caf\u00e9",         // non-ASCII letter
    })
    void unsafeOrMissingIdIsReplacedWithAUuid(String incoming) {
        String result = CorrelationIds.resolve(incoming);

        assertThat(result).isNotEqualTo(incoming);
        assertThat(isValidUuid(result)).isTrue();
    }

    @Test
    void idLongerThan64CharactersIsReplaced() {
        String tooLong = "a".repeat(65);

        String result = CorrelationIds.resolve(tooLong);

        assertThat(result).isNotEqualTo(tooLong);
        assertThat(isValidUuid(result)).isTrue();
    }

    @Test
    void twoCallsWithNullGiveTwoDifferentIds() {
        assertThat(CorrelationIds.resolve(null)).isNotEqualTo(CorrelationIds.resolve(null));
    }

    private static boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
