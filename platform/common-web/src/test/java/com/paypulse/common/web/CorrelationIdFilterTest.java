package com.paypulse.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: Spring mock objects, no Spring context, no server. */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesUuidWhenRequestHasNoHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();   // no X-Correlation-Id set
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(id).isNotBlank();
        // Round trip: proves the text is a proper UUID, not just something parseable.
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void generatesFreshUuidWhenHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "   ");   // present, but only spaces
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        // The blank value must be replaced by a new UUID.
        String id = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(id).isNotBlank();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void reusesIdSentByCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-123");
    }

    @Test
    void mdcIsSetDuringRequestAndClearedAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Fake "rest of the app": reads the MDC while the request is running.
        AtomicReference<String> mdcInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        // The id was in the MDC during the request...
        assertThat(mdcInsideChain.get()).isEqualTo("abc-123");
        // ...and was removed afterwards (the finally block works).
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
