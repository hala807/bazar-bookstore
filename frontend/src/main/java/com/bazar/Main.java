package com.bazar;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final Gson gson = new Gson();

    private static final String[] CATALOG_SERVERS = {
        System.getenv().getOrDefault("CATALOG_URL",         "http://catalog:5001"),
        System.getenv().getOrDefault("CATALOG_REPLICA_URL", "http://catalog-replica:5001")
    };

    private static final String[] ORDER_SERVERS = {
        System.getenv().getOrDefault("ORDER_URL",         "http://order:5002"),
        System.getenv().getOrDefault("ORDER_REPLICA_URL", "http://order-replica:5002")
    };

    private static final AtomicInteger catalogCounter = new AtomicInteger(0);
    private static final AtomicInteger orderCounter   = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        port(5000);

        get("/search/:topic", (req, res) -> {
            String topic    = req.params(":topic");
            String cacheKey = "search:" + topic;
            if (cache.containsKey(cacheKey)) {
                System.out.println("[Frontend] CACHE HIT  - search: " + topic);
                res.type("application/json");
                return cache.get(cacheKey);
            }
            System.out.println("[Frontend] CACHE MISS - search: " + topic);
            String url = nextCatalog() + "/search/" +
                         java.net.URLEncoder.encode(topic, java.nio.charset.StandardCharsets.UTF_8)
                                           .replace("+", "%20");
            String result = sendGetRequest(url);
            cache.put(cacheKey, result);
            System.out.println("[Frontend] Search result: " + result);
            res.type("application/json");
            return result;
        });

        get("/info/:id", (req, res) -> {
            String id       = req.params(":id");
            String cacheKey = "info:" + id;
            if (cache.containsKey(cacheKey)) {
                System.out.println("[Frontend] CACHE HIT  - info id: " + id);
                res.type("application/json");
                return cache.get(cacheKey);
            }
            System.out.println("[Frontend] CACHE MISS - info id: " + id);
            String url    = nextCatalog() + "/info/" + id;
            String result = sendGetRequest(url);
            cache.put(cacheKey, result);
            System.out.println("[Frontend] Info result: " + result);
            res.type("application/json");
            return result;
        });

        post("/purchase/:id", (req, res) -> {
            String id  = req.params(":id");
            System.out.println("[Frontend] Purchase request for ID: " + id);
            String url    = nextOrder() + "/purchase/" + id;
            String result = sendPostRequest(url);
            if (result.contains("error")) {
                res.status(400);
                System.out.println("[Frontend] Purchase failed: out of stock");
                return result;
            }
            invalidateCache(id);
            res.type("application/json");
            return result;
        });

        delete("/cache/invalidate/:id", (req, res) -> {
            String id = req.params(":id");
            invalidateCache(id);
            System.out.println("[Frontend] Cache invalidated for book ID: " + id);
            res.type("application/json");
            return gson.toJson(Map.of("status", "invalidated", "id", id));
        });

        System.out.println("=== Frontend Server running on http://localhost:5000 ===");
    }

    private static String nextCatalog() {
        int idx = Math.abs(catalogCounter.getAndIncrement() % CATALOG_SERVERS.length);
        System.out.println("[Frontend] Load balance → catalog server: " + CATALOG_SERVERS[idx]);
        return CATALOG_SERVERS[idx];
    }

    private static String nextOrder() {
        int idx = Math.abs(orderCounter.getAndIncrement() % ORDER_SERVERS.length);
        System.out.println("[Frontend] Load balance → order server: " + ORDER_SERVERS[idx]);
        return ORDER_SERVERS[idx];
    }

    private static void invalidateCache(String bookId) {
        cache.remove("info:" + bookId);
        cache.entrySet().removeIf(e -> e.getKey().startsWith("search:"));
        System.out.println("[Frontend] Cache cleared for book ID: " + bookId);
    }

    private static String sendGetRequest(String urlString) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("Error: " + response.statusCode());
        return response.body();
    }

    private static String sendPostRequest(String urlString) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 400)
            throw new Exception("Order error: " + response.statusCode());
        return response.body();
    }
}
