package com.prorocketeers.lukas.routing.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void stripsCarriageReturnAndNewlineToPreventForgedLogLines() {
        assertThat(LogSanitizer.sanitize("BEL\r\nFAKE INJECTED LOG LINE")).isEqualTo("BEL__FAKE INJECTED LOG LINE");
    }

    @Test
    void leavesOrdinaryTextUnchanged() {
        assertThat(LogSanitizer.sanitize("BEL")).isEqualTo("BEL");
    }
}
