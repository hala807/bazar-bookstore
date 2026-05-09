package com.bazar;

import static spark.Spark.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class Main {

    private static final Gson gson = new Gson();
    private static final String CATALOG_URL         = System.getenv().getOrDefault("CATALOG_URL",         "http://catalog:5001");
    private static final String CATALOG_REPLICA_URL = System.getenv().getOrDefault("CATALOG_REPLICA_URL", "http://catalog-replica:5001");
    private static final String FRONTEND_CACHE_URL  = System.getenv().getOrDefault("FRONTEND_CACHE_URL",  "http://frontend:5000");
    private static final String ORDERS_FILE         = "/app/data/orders.log";

    public static void main(String[] args) {
        port(5002);

        post("/purchase/:id", (req, res) -> {
            int id = Integer.parseInt(req.params(":id"));
            System.out.println("[Order] Purchase request for book ID: " + id);

            try {
                String bookJson = sendGetRequest(CATALOG_URL + "/info/" + id);
                JsonObject book = gson.fromJson(bookJson, JsonObject.class);
                String title    = book.get("title").getAsString();
                int quantity    = book.get("quantity").getAsInt();

                if (quantity <= 0) {
                    res.status(400);
                    System.out.println("[Order] Out of stock: " + title);
                    return gson.toJson(Map.of("error", "Book out of stock"));
                }

                JsonObject updateBody = new JsonObject();
                updateBody.addProperty("quantity", quantity - 1);

                try {
                    sendDeleteRequest(FRONTEND_CACHE_URL + "/cache/invalidate/" + id);
                    System.out.println("[Order] Cache invalidated before update");
                } catch (Exception e) {
                    System.out.println("[Order] Cache invalidation warning: " + e.getMessage());
                }

                sendPutRequest(CATALOG_URL + "/update/" + id, updateBody.toString());
                System.out.println("[Order] Primary catalog updated");

                try {
                    sendPutRequest(CATALOG_REPLICA_URL + "/update/" + id, updateBody.toString());
                    System.out.println("[Order] Replica synced");
                } catch (Exception e) {
                    System.out.println("[Order] Replica sync warning: " + e.getMessage());
                }

                String logMessage = "bought book " + title;
                System.out.println("[Order] SUCCESS: " + logMessage);
                new File("/app/data").mkdirs();
                try (FileWriter writer = new FileWriter(ORDERS_FILE, true)) {
                    writer.write(logMessage + " (ID: " + id + ") at " + java.time.LocalDateTime.now() + "\n");
                }

                res.type("application/json");
                return gson.toJson(Map.of("status", "success", "message", logMessage));

            } catch (Exception e) {
                res.status(500);
                System.out.println("[Order] ERROR: " + e.getMessage());
                return gson.toJson(Map.of("error", "Internal server error"));
            }
        });

        System.out.println("=== Order Server running on port 5002 ===");
        awaitInitialization();
    }

    private static String sendGetRequest(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private static void sendPutRequest(String url, String body) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder().uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("PUT failed: " + response.statusCode());
    }

    private static void sendDeleteRequest(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        client.send(
            HttpRequest.newBuilder().uri(URI.create(url)).DELETE().build(),
            HttpResponse.BodyHandlers.ofString());
    }
}
