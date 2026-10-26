package com.paypulse.gateway.forwarded;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;

/**
 * Tells the services which address the CLIENT used, so links they build (Location headers)
 * point to the gateway and not to the service's private port.
 *
 * <p>Why an HttpHeadersFilter and not a GlobalFilter: right before a request is sent to the
 * service, the gateway runs a list of header filters. One of them (RemoveXForwardedHeadersFilter,
 * order 0) deletes EVERY X-Forwarded-* header, because the gateway trusts no proxy in front of it.
 * A GlobalFilter runs earlier, so its headers were deleted too. Ours has order 100, so it runs
 * AFTER the remover, and the headers survive.
 *
 * <p>The gateway is the public edge, so whatever the client sent in X-Forwarded-* is untrusted
 * (S8.3). The remover drops it, and we set the values from the request the gateway itself received.
 */
@Component
public class AddForwardedHeadersFilter implements HttpHeadersFilter, Ordered {

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        // exchange.getRequest() is the request the CLIENT sent to the gateway (not the outgoing one).
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();
        String proto = uri.getScheme();
        // The Host header is exactly what the client typed ("localhost:8080", "api.example.com").
        String host = request.getHeaders().getFirst(HttpHeaders.HOST);
        int port = uri.getPort() != -1 ? uri.getPort() : ("https".equals(proto) ? 443 : 80);

        // Work on a copy: the incoming headers object must not be changed in place.
        HttpHeaders updated = new HttpHeaders();
        updated.putAll(input);
        updated.set("X-Forwarded-Proto", proto);
        updated.set("X-Forwarded-Port", String.valueOf(port));
        if (host != null) {
            // If Host is missing we send nothing rather than a guess.
            updated.set("X-Forwarded-Host", host);
        }
        return updated;
    }

    // These filters run for requests going OUT to the service and for responses coming back.
    // We only want the request side.
    @Override
    public boolean supports(Type type) {
        return type == Type.REQUEST;
    }

    /** Must be bigger than 0 (RemoveXForwardedHeadersFilter), so it runs after it. */
    @Override
    public int getOrder() {
        return 100;
    }
}
