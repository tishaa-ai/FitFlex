package com.fitflex.service;

import com.fitflex.config.RestClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the Overpass reliability logic against a fake local HTTP server that imitates
 * misbehaving mirrors, so no internet connection is needed:
 *   /busy    always "502 at capacity"       /ok     a normal answer with one gym
 *   /remark  "200 OK" but a runtime error   /flaky  503 the first time, then OK
 *   /slow    answers after 3 seconds
 */
class OverpassClientTest {

    private static final String GYM_JSON = """
            {"elements":[{"type":"node","id":1,"lat":21.15,"lon":79.09,
              "tags":{"leisure":"fitness_centre","name":"Test Gym","addr:city":"Nagpur"}}]}""";
    private static final String REMARK_JSON = """
            {"elements":[],"remark":"runtime error: Query timed out in \\"query\\" at line 3 after 25 seconds."}""";

    private HttpServer server;
    private String base;
    private final AtomicInteger flakyCalls = new AtomicInteger();

    @BeforeEach
    void startFakeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/busy", ex -> reply(ex, 502, "Gateway Timeout: all query workers are busy"));
        server.createContext("/ok", ex -> reply(ex, 200, GYM_JSON));
        server.createContext("/remark", ex -> reply(ex, 200, REMARK_JSON));
        server.createContext("/flaky", ex -> {
            if (flakyCalls.incrementAndGet() == 1) reply(ex, 503, "busy");
            else reply(ex, 200, GYM_JSON);
        });
        server.createContext("/slow", ex -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            reply(ex, 200, GYM_JSON);
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopFakeServer() {
        server.stop(0);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/json" : "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private OverpassClient client(long deadlineSeconds, String... paths) {
        String urls = String.join(",", java.util.Arrays.stream(paths).map(p -> base + p).toList());
        return new OverpassClient(RestClientConfig.build(Duration.ofSeconds(2), Duration.ofSeconds(10)),
                JsonMapper.builder().build(), urls, deadlineSeconds);
    }

    @Test
    void usesAnotherMirrorWhenOneIsBusy() {
        List<GooglePlacesClient.Place> places = client(10, "/busy", "/ok").searchGyms(21.1458, 79.0882, 3000);
        assertThat(places).extracting(GooglePlacesClient.Place::name).containsExactly("Test Gym");
        assertThat(places.get(0).address()).isEqualTo("Nagpur");
    }

    @Test
    void retriesAServerThatWasBusyOnce() {
        List<GooglePlacesClient.Place> places = client(15, "/flaky").searchGyms(21.1458, 79.0882, 3000);
        assertThat(places).hasSize(1);
        assertThat(flakyCalls.get()).isEqualTo(2); // failed once, succeeded on the retry
    }

    @Test
    void treatsRuntimeErrorRemarkAsFailure() {
        assertThat(client(10, "/remark", "/ok").searchGyms(21.1458, 79.0882, 3000)).hasSize(1);
        assertThatThrownBy(() -> client(10, "/remark").searchGyms(21.1458, 79.0882, 3000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Overpass servers failed")
                .hasMessageContaining("runtime error");
    }

    @Test
    void failsWhenEveryMirrorFails() {
        assertThatThrownBy(() -> client(15, "/busy", "/remark").searchGyms(21.1458, 79.0882, 3000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("All 2 Overpass servers failed");
    }

    @Test
    void givesUpAfterTheDeadline() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> client(1, "/slow").searchGyms(21.1458, 79.0882, 3000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("within 1 s");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }

    @Test
    void fastMirrorWinsOverSlowOne() {
        long start = System.nanoTime();
        assertThat(client(10, "/slow", "/ok").searchGyms(21.1458, 79.0882, 3000)).hasSize(1);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(2500));
    }
}
