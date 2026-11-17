package com.sqlens.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP code path against a local fake of the Gemini endpoint. */
class LLMServiceTest {

    private HttpServer server;
    private final AtomicReference<String> seenKey = new AtomicReference<>();
    private final AtomicReference<String> seenPath = new AtomicReference<>();
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Add an index\"},{\"text\":\" on film(length).\"}]}}]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", ex -> {
            seenKey.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            seenPath.set(ex.getRequestURI().toString());
            seenBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] b = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LLMService service() {
        return new LLMService("http://127.0.0.1:" + server.getAddress().getPort() + "/models/", "test-model", "k-123");
    }

    @Test
    void sendsKeyInHeaderNotUrlAndJoinsParts() throws LLMException {
        String text = service().suggest("SELECT 1");
        assertEquals("Add an index on film(length).", text);
        assertEquals("k-123", seenKey.get());
        assertEquals("/models/test-model:generateContent", seenPath.get());
        assertFalse(seenPath.get().contains("k-123"));
        assertTrue(seenBody.get().contains("SELECT 1"));
        assertTrue(seenBody.get().contains("systemInstruction"));
    }

    @Test
    void mapsAuthErrorsWithoutLeakingBody() {
        status = 403;
        reply = "{\"error\":\"secret internal detail\"}";
        LLMException e = assertThrows(LLMException.class, () -> service().suggest("x"));
        assertTrue(e.getMessage().contains("403"));
        assertFalse(e.getMessage().contains("secret internal detail"));
    }

    @Test
    void rateLimitAndMalformedResponses() {
        status = 429;
        assertTrue(assertThrows(LLMException.class, () -> service().suggest("x")).getMessage().contains("rate limit"));
        status = 200;
        reply = "not json";
        assertThrows(LLMException.class, () -> service().suggest("x"));
        reply = "{\"candidates\":[]}";
        assertThrows(LLMException.class, () -> service().suggest("x"));
    }

    @Test
    void stripsTerminalEscapes() {
        assertEquals("red text\nok", LLMService.sanitize("\u001b[31mred text\u0007\nok"));
        assertEquals("title", LLMService.sanitize("\u001b]0;pwned\u0007title"));
        assertEquals("ab", LLMService.sanitize("a\u001b[2J\u001b[Hb"));
    }

    @Test
    void rejectsModelNamesThatCouldAlterTheUrl() {
        assertThrows(IllegalArgumentException.class, () -> new LLMService("http://x/", "../../evil?x=", "k"));
    }
}
