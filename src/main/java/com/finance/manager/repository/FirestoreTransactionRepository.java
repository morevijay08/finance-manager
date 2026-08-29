package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.Transaction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FirestoreTransactionRepository {

    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String FIRESTORE_BASE_URL =
            "https://firestore.googleapis.com/v1/projects/"
                    + PROJECT_ID
                    + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Transaction> addTransaction(AuthSession session, Transaction transaction) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            String id = UUID.randomUUID().toString();
            transaction.setId(id);

            JsonObject fields = new JsonObject();
            fields.add("type", stringField(transaction.getType().name()));
            fields.add("amount", doubleField(transaction.getAmount()));
            fields.add("category", stringField(transaction.getCategory()));
            fields.add("description", stringField(transaction.getDescription()));
            fields.add("date", stringField(transaction.getDate().toString()));

            JsonObject document = new JsonObject();
            document.add("fields", fields);

            HttpRequest request = authorizedRequest(documentUrl(session, id), session.getIdToken())
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(document.toString()))
                    .build();

            HttpResponse<String> response = send(request);
            ensureSuccess(response);
            return transaction;
        });
    }

    public CompletableFuture<List<Transaction>> getTransactions(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);

            HttpRequest request = authorizedRequest(collectionUrl(session), session.getIdToken())
                    .GET().build();
            HttpResponse<String> response = send(request);

            if (response.statusCode() == 404) {
                return new ArrayList<>();
            }
            ensureSuccess(response);

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            List<Transaction> transactions = new ArrayList<>();
            if (!root.has("documents")) {
                return transactions;
            }

            JsonArray documents = root.getAsJsonArray("documents");
            for (int i = 0; i < documents.size(); i++) {
                JsonObject document = documents.get(i).getAsJsonObject();
                JsonObject fields = document.getAsJsonObject("fields");
                String name = document.get("name").getAsString();
                String id = name.substring(name.lastIndexOf('/') + 1);

                transactions.add(new Transaction(
                        id,
                        Transaction.Type.valueOf(readString(fields, "type", "EXPENSE")),
                        readDouble(fields, "amount"),
                        readString(fields, "category", "Other"),
                        readString(fields, "description", ""),
                        LocalDate.parse(readString(fields, "date", LocalDate.now().toString()))
                ));
            }

            transactions.sort((a, b) -> b.getDate().compareTo(a.getDate()));
            return transactions;
        });
    }

    private String collectionUrl(AuthSession session) {
        return FIRESTORE_BASE_URL + encode(session.getLocalId()) + "/transactions?key="
                + FirebaseConfig.getWebApiKey();
    }

    private String documentUrl(AuthSession session, String id) {
        return FIRESTORE_BASE_URL + encode(session.getLocalId()) + "/transactions/" + encode(id)
                + "?key=" + FirebaseConfig.getWebApiKey();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonObject stringField(String value) {
        JsonObject field = new JsonObject();
        field.addProperty("stringValue", value == null ? "" : value);
        return field;
    }

    private JsonObject doubleField(double value) {
        JsonObject field = new JsonObject();
        field.addProperty("doubleValue", value);
        return field;
    }

    private String readString(JsonObject fields, String name, String fallback) {
        if (!fields.has(name)) return fallback;
        JsonObject field = fields.getAsJsonObject(name);
        return field.has("stringValue") ? field.get("stringValue").getAsString() : fallback;
    }

    private double readDouble(JsonObject fields, String name) {
        if (!fields.has(name)) return 0.0;
        JsonObject field = fields.getAsJsonObject(name);
        if (field.has("doubleValue")) return field.get("doubleValue").getAsDouble();
        if (field.has("integerValue")) return field.get("integerValue").getAsDouble();
        return 0.0;
    }

    private HttpRequest.Builder authorizedRequest(String url, String token) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to Firestore.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore request was interrupted.", e);
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
        }
    }

    private void validateSession(AuthSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()
                || session.getLocalId() == null || session.getLocalId().isBlank()) {
            throw new RuntimeException("No valid authenticated session.");
        }
    }
}
