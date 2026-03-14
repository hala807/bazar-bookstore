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
    private static final String CATALOG_URL = "http://localhost:5001";   // بنغيّره لـ "http://catalog:5001" لما نعمل Docker
    private static final String ORDERS_FILE = "orders.log";

    public static void main(String[] args) {
        port(5002);

        // ====================== PURCHASE ENDPOINT ======================
        post("/purchase/:id", (req, res) -> {
            int id = Integer.parseInt(req.params(":id"));
            System.out.println("[Order] Received purchase request for book ID: " + id);

            try {
                // 1. جلب معلومات الكتاب من Catalog
                String infoUrl = CATALOG_URL + "/info/" + id;
                String bookJson = sendGetRequest(infoUrl);
                JsonObject book = gson.fromJson(bookJson, JsonObject.class);

                String title = book.get("title").getAsString();
                int quantity = book.get("quantity").getAsInt();

                // 2. التحقق من الكمية
                if (quantity <= 0) {
                    res.status(400);
                    System.out.println("[Order] ❌ Book out of stock: " + title);
                    return gson.toJson(Map.of("error", "Book out of stock"));
                }

                // 3. خصم الكمية (Update Catalog)
                int newQuantity = quantity - 1;
                String updateUrl = CATALOG_URL + "/update/" + id;

                JsonObject updateBody = new JsonObject();
                updateBody.addProperty("quantity", newQuantity);

                sendPutRequest(updateUrl, updateBody.toString());

                // 4. حفظ الطلب + طباعة الرسالة المطلوبة في الـ Lab
                String logMessage = "bought book " + title;
                System.out.println("[Order] ✅ " + logMessage);
                try (FileWriter writer = new FileWriter(ORDERS_FILE, true)) {
                    writer.write(logMessage + " (ID: " + id + ") at " + java.time.LocalDateTime.now() + "\n");
                }

                // 5. الرد النهائي (JSON)
                res.type("application/json");
                return gson.toJson(Map.of("status", "success", "message", logMessage));

            } catch (Exception e) {
                res.status(500);
                System.out.println("[Order] ❌ Error: " + e.getMessage());
                return gson.toJson(Map.of("error", "Internal server error"));
            }
        });

        System.out.println("=== Order Server running on port 5002 ===");
        awaitInitialization();
    }

    // ====================== HTTP Helper Methods ======================
    private static String sendGetRequest(String urlString) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Catalog returned status: " + response.statusCode());
        }
        return response.body();
    }

    private static void sendPutRequest(String urlString, String jsonBody) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlString))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Update failed with status: " + response.statusCode());
        }
    }
}