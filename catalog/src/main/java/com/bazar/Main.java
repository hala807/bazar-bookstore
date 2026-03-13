package com.bazar;
import static spark.Spark.*;
import java.util.*;
import com.google.gson.Gson;
public class Main {
    static Gson gson = new Gson();
    static List<Map<String, Object>> books = new ArrayList<>();
    public static void main(String[] args) {
        port(5001);
        loadBooks();
        get("/search/:topic", (req, res) -> {
            res.type("application/json");
            String topic = req.params(":topic");
            List<Map<String, Object>> results = new ArrayList<>();

            for (Map<String, Object> book : books) {
                if (book.get("topic").toString().equalsIgnoreCase(topic)) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", book.get("id"));
                    item.put("title", book.get("title"));
                    results.add(item);
                }
            }
            return gson.toJson(results);
        });

        get("/info/:id", (req, res) -> {
            res.type("application/json");
            int id = Integer.parseInt(req.params(":id"));

            for (Map<String, Object> book : books) {
                if (((Number) book.get("id")).intValue() == id) {
                    return gson.toJson(book);
                }
            }
            res.status(404);
            return gson.toJson("Book not found");
        });

        put("/update/:id", (req, res) -> {
            res.type("application/json");
            int id = Integer.parseInt(req.params(":id"));
            Map<String, Object> body = gson.fromJson(req.body(), Map.class);

            for (Map<String, Object> book : books) {
                if (((Number) book.get("id")).intValue()== id) {
                    if (body.containsKey("quantity")) {
                        book.put("quantity", ((Double) body.get("quantity")).intValue());
                    }
                    if (body.containsKey("price")) {
                        book.put("price", body.get("price"));
                    }
                    return gson.toJson("Updated successfully");
                }
            }
            res.status(404);
            return gson.toJson("Book not found");
        });
        System.out.println("Catalog Server running on port 5001");
    }
    static void loadBooks() {
        books.add(createBook(1, "How to get a good grade in DOS in 40 minutes a day", "distributed systems", 50, 10));
        books.add(createBook(2, "RPCs for Noobs", "distributed systems", 40, 10));
        books.add(createBook(3, "Xen and the Art of Surviving Undergraduate School", "undergraduate school", 35, 10));
        books.add(createBook(4, "Cooking for the Impatient Undergrad", "undergraduate school", 30, 10));

    }


    static Map<String, Object> createBook(int id, String title, String topic, double price, int quantity) {
        Map<String, Object> book = new HashMap<>();
        book.put("id", id);
        book.put("title", title);
        book.put("topic", topic);
        book.put("price", price);
        book.put("quantity", quantity);
        return book;
    }
}