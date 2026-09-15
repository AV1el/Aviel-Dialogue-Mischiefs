package net.aviel.dialogue.npc.storage;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class AssetHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ThreadPoolExecutor executor;
    private final Map<String, byte[]> packs = new LinkedHashMap<>();

    AssetHttpServer(String bind, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bind, port), 16);
        executor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16), runnable -> {
                    Thread thread = new Thread(runnable, "ADM asset HTTP");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    synchronized void publish(String hash, byte[] bytes) {
        packs.put("/" + hash + ".zip", bytes);
        while (packs.size() > 2) {
            packs.remove(packs.keySet().iterator().next());
        }
    }

    int port() {
        return server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bytes;
            synchronized (this) {
                bytes = packs.get(exchange.getRequestURI().getRawPath());
            }
            if (bytes == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length", Integer.toString(bytes.length));
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
            exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : bytes.length);
            if (method.equals("GET")) {
                exchange.getResponseBody().write(bytes);
            }
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        synchronized (this) {
            packs.clear();
        }
    }
}
