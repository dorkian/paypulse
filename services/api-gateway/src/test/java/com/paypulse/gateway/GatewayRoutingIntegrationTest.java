package com.paypulse.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the whole gateway (route + filter + forwarding) against a tiny fake customer-service.
 * The fake is the JDK's built-in HttpServer, so we need no extra dependency and no real service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingIntegrationTest {

    private static final String HEADER = "X-Correlation-Id";

    /** What the fake service saw in the last request it received. */
    record Received(String path, Map<String, List<String>> headers) {
        String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }
    }

    private static final AtomicReference<Received> LAST = new AtomicReference<>();

    // Started when the class loads, BEFORE Spring starts, because we need its port to
    // configure the route (see @DynamicPropertySource below).
    private static final HttpServer STUB = startStub();

    private static HttpServer startStub() {
        try {
            // port 0 = "pick any free port", so tests never clash with your running services
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", exchange -> {
                // Case-insensitive map: header names can arrive as X-Correlation-Id or x-correlation-id.
                Map<String, List<String>> seen = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                exchange.getRequestHeaders().forEach(seen::put);
                LAST.set(new Received(exchange.getRequestURI().getPath(), seen));

                byte[] body = "{\"stub\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                // Behave like the real customer-service: it returns the id it received.
                String id = exchange.getRequestHeaders().getFirst(HEADER);
                if (id != null) {
                    exchange.getResponseHeaders().add(HEADER, id);
                }
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Points the route (CUSTOMER_SERVICE_URL in application.yml) at the fake service.
    @DynamicPropertySource
    static void route(DynamicPropertyRegistry registry) {
        registry.add("CUSTOMER_SERVICE_URL", () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    void setUp() {
        LAST.set(null);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private EntityExchangeResult<byte[]> get(String path, String correlationId) {
        WebTestClient.RequestHeadersSpec<?> request = client.get().uri(path);
        if (correlationId != null) {
            request = request.header(HEADER, correlationId);
        }
        return request.exchange().expectBody().returnResult();
    }

    private static List<String> responseIds(EntityExchangeResult<byte[]> result) {
        List<String> ids = result.getResponseHeaders().get(HEADER);
        return ids == null ? List.of() : ids;
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Test
    void forwardsThePathUnchangedAndPassesTheBodyThrough() {
        client.get().uri("/api/v1/customers/abc")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.stub").isEqualTo("ok");

        assertThat(LAST.get().path()).isEqualTo("/api/v1/customers/abc");
    }

    @Test
    void createsAnIdWhenNoneIsSentAndReturnsTheSameOne() {
        EntityExchangeResult<byte[]> result = get("/api/v1/customers", null);

        String seenByService = LAST.get().header(HEADER);
        assertThat(isUuid(seenByService)).isTrue();
        // exactly ONE value in the response (guards against the duplicate-header bug)
        assertThat(responseIds(result)).containsExactly(seenByService);
    }

    @Test
    void keepsAGoodIncomingIdEndToEnd() {
        EntityExchangeResult<byte[]> result = get("/api/v1/customers", "abc-123");

        assertThat(LAST.get().header(HEADER)).isEqualTo("abc-123");
        assertThat(responseIds(result)).containsExactly("abc-123");
    }

    @Test
    void replacesABadIncomingId() {
        String bad = "bad id " + "x".repeat(100); // has a space and is over 64 characters

        EntityExchangeResult<byte[]> result = get("/api/v1/customers", bad);

        String seenByService = LAST.get().header(HEADER);
        assertThat(seenByService).isNotEqualTo(bad);
        assertThat(isUuid(seenByService)).isTrue();
        assertThat(responseIds(result)).containsExactly(seenByService);
    }

    @Test
    void unknownPathHasNoRouteAndNeverReachesTheService() {
        EntityExchangeResult<byte[]> result = get("/api/v1/nothing", null);

        assertThat(result.getStatus().value()).isEqualTo(404);
        assertThat(LAST.get()).isNull();
    }

    @Test
    void forwardsTheAddressTheClientUsedAndOverwritesFakeOnes() {
        client.get().uri("/api/v1/customers")
                .header("X-Forwarded-Host", "evil.example.com")
                .header("X-Forwarded-Proto", "https")
                .exchange()
                .expectBody().returnResult();

        Received seen = LAST.get();
        assertThat(seen.header("X-Forwarded-Host")).isEqualTo("localhost:" + port);
        assertThat(seen.header("X-Forwarded-Proto")).isEqualTo("http");
        assertThat(seen.header("X-Forwarded-Port")).isEqualTo(String.valueOf(port));
    }

}
