package com.paypulse.gateway.correlation;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.http.server.reactive.ServerHttpResponse;


/**
 * Gives every request that passes through the gateway a safe correlation id, forwards it
 * to the service, and returns it to the caller.
 *
 * <p>Pattern: cross-cutting concern at the edge. A GlobalFilter runs for EVERY route, so
 * routes added later (invoices, payments...) get tracing without extra work.
 *
 * <p>No MDC here, on purpose. MDC is a thread-local map, and this gateway is reactive
 * (WebFlux): one request can hop between threads, so a thread-local would be wrong or
 * leak into other requests. Logging the id properly in the gateway needs the Reactor
 * context, planned for L6 (observability). Until then the id travels in headers.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    /** Same header name as the services use (CorrelationIdFilter in common-web). */
    public static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Read what the caller sent (null if absent) and decide: keep it or replace it.
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String correlationId = CorrelationIds.resolve(incoming);

        // 2. Requests are immutable, so build a NEW one carrying the decided id.
        //    header(...) REPLACES any value the caller sent, so a bad id never reaches the service.
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER_NAME, correlationId)
                .build();

        // 3. Put the id on the response in beforeCommit: that callback runs at the last
        //    moment before the headers are sent, AFTER the service's own response headers
        //    were copied in, so set() really replaces them. Setting it earlier gave a
        //    duplicate header (ours plus the service's copy).
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(HEADER_NAME, correlationId);
            return Mono.empty();
        });


        // 4. Continue the chain with the modified request.
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** Run before every other filter, so the id exists for everything that follows. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
