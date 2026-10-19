package com.paypulse.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a correlation id, so one search finds all its log lines.
 * {@link OncePerRequestFilter} makes sure it runs once per request.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** Header used for the id, in the request and in the response. */
    public static final String HEADER_NAME = "X-Correlation-Id";

    /** MDC key. Print it in logs with %X{correlationId}. */
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Reuse the caller's id, or create one if it is missing or blank.
        //    We trust the caller's value for now. On a public edge, check its
        //    length and characters first (very long or odd values pollute logs).
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.strip().isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // 2. MDC = a per-thread map. Logs written by this thread can print the id.
        MDC.put(MDC_KEY, correlationId);

        // 3. Send the id back, so the caller can quote it in a bug report.
        response.setHeader(HEADER_NAME, correlationId);

        try {
            // 4. Run the rest of the request.
            filterChain.doFilter(request, response);
        } finally {
            // 5. Always clean up. Threads are reused, so a leftover id
            //    would show up in the next request's logs.
            MDC.remove(MDC_KEY);
        }
    }
}
