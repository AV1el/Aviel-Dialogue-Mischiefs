package net.aviel.dialogue.npc.storage;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetHttpServerTest {
    @Test
    void servesOnlyPublishedSnapshotsAndHandlesUpdates() throws Exception {
        try (AssetHttpServer server = new AssetHttpServer("127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            byte[] original = {1, 2, 3};
            server.publish("first", original);
            String base = "http://127.0.0.1:" + server.port();
            var first = request(client, base + "/first.zip", "GET");
            assertEquals(200, first.statusCode());
            assertArrayEquals(original, first.body());
            assertEquals("application/zip", first.headers().firstValue("Content-Type").orElseThrow());
            var head = request(client, base + "/first.zip", "HEAD");
            assertEquals(200, head.statusCode());
            assertEquals(0, head.body().length);
            assertEquals(405, request(client, base + "/first.zip", "POST").statusCode());
            assertEquals(404, request(client, base + "/../server.properties", "GET").statusCode());
            assertEquals(404, request(client, base + "/", "GET").statusCode());
            server.publish("second", new byte[]{4});
            assertArrayEquals(original, request(client, base + "/first.zip", "GET").body());
            server.publish("third", new byte[]{5});
            assertEquals(404, request(client, base + "/first.zip", "GET").statusCode());
            assertArrayEquals(new byte[]{5}, request(client, base + "/third.zip", "GET").body());
        }
    }

    private static HttpResponse<byte[]> request(HttpClient client, String url, String method) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
