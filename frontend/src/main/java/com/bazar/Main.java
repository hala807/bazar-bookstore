package com.bazar;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Main {

    private static final Gson gson = new Gson();

    private static final String CATALOG_URL = System.getenv().getOrDefault("CATALOG_URL", "http://localhost:5001");    private static final String ORDER_URL   = System.getenv().getOrDefault("ORDER_URL",   "http://localhost:5002");

    public static void main(String[] args) {
        port(5000);

        // ====================== ROUTES ======================

        get("/search/:topic", (req, res) -> {
            String topic = req.params(":topic");
            String encodedTopic = java.net.URLEncoder.encode(topic, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");

            String url = CATALOG_URL + "/search/" + encodedTopic;

            System.out.println("[Frontend] 🔎 Forwarding search for topic: " + topic);
            String result = sendGetRequest(url);

            res.type("application/json");
            System.out.println("[Frontend] ✅ Search result: " + result);
            return result;
        });
        // 2. INFO
        get("/info/:id", (req, res) -> {
            String url = CATALOG_URL + "/info/" + req.params(":id");

            System.out.println("[Frontend] 📘 Forwarding info request for ID: " + req.params(":id"));
            String result = sendGetRequest(url);

            res.type("application/json");
            System.out.println("[Frontend] ✅ Info result: " + result);
            return result;
        });


        // ====================== PURCHASE ROUTE ======================
        post("/purchase/:id", (req, res) -> {
            String url = ORDER_URL + "/purchase/" + req.params(":id");

            System.out.println("[Frontend] 🛒 Forwarding purchase for ID: " + req.params(":id"));
            String result = sendPostRequest(url);

            if (result.contains("error")) {
                res.status(400);
                System.out.println("[Frontend] ❌ Purchase failed: Book out of stock");
                return result;
            }

            res.type("application/json");
            return result;
        });

        System.out.println("=== Frontend Server running on http://localhost:5000 ===");
    }

    // ====================== HTTP Helpers ======================
    private static String sendGetRequest(String urlString) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new Exception("Catalog error: " + response.statusCode());
        return response.body();
    }

    // ====================== HTTP Helpers ======================
    private static String sendPostRequest(String urlString) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200 && response.statusCode() != 400) {
            throw new Exception("Order error: " + response.statusCode());
        }
        return response.body();
    }

}