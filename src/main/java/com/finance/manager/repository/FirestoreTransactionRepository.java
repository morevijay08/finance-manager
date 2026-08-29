package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.Transaction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.concurrent.CompletableFuture;

public class FirestoreTransactionRepository {

    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String COLLECTION_URL =
            "https://firestore.googleapis.com/v1/projects/"
                    + PROJECT_ID + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Void> addTransaction(AuthSession session, Transaction transaction) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            JsonObject fields = new JsonObject();
            fields.add("type", stringField(transaction.getType().name()));
            fields.add("amount", doubleField(transaction.getAmount()));
            fields.add("category", stringField(transaction.getCategory()));
            fields.add("description", stringField(transaction.getDescription()));
            fields.add("date", stringField(transaction.getDate().toString()));

            JsonObject document = new JsonObject();
            document.add("fields", fields);

            HttpRequest request = authorizedRequest(collectionUrl(session), session.getIdToken())
                    .POST(HttpRequest.BodyPublishers.ofString(document.toString()))
                    .build();
            send(request);
        });
    }

    public CompletableFuture<List<Transaction>> getTransactions(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            HttpRequest request = authorizedRequest(collectionUrl(session), session.getIdToken())
                    .GET().build();
            HttpResponse<String> response = sendAndReturn(request);
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            List<Transaction> transactions = new ArrayList<>();
            if (!root.has("documents")) {
                return transactions;
            }

            JsonArray documents = root.getAsJsonArray("documents");
            for (JsonElement element : documents) {
                JsonObject document = element.getAsJsonObject();
                JsonObject fields = document.getAsJsonObject("fields");
                String name = document.has("name") ? document.get("name").getAsString() : "";
                String id = name.isBlank() ? "" : name.substring(name.lastIndexOf('/') + 1);
                transactions.add(new Transaction(
                        id,
                        Transaction.Type.valueOf(stringValue(fields, "type", "EXPENSE")),
                        doubleValue(fields, "amount"),
                        stringValue(fields, "category", "Other"),
                        stringValue(fields, "description", ""),
                        LocalDate.parse(stringValue(fields, "date", LocalDate.now().toString()))
                ));
            }
            transactions.sort((a, b) -> b.getDate().compareTo(a.getDate()));
            return transactions;
        });
    }

    private String collectionUrl(AuthSession session) {
        return COLLECTION_URL + URLEncoder.encode(session.getLocalId(), StandardCharsets.UTF_8)
                + "/transactions?key=" + FirebaseConfig.getWebApiKey();
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

    private String stringValue(JsonObject fields, String name, String fallback) {
        if (!fields.has(name) || !fields.getAsJsonObject(name).has("stringValue")) {
            return fallback;
        }
        return fields.getAsJsonObject(name).get("stringValue").getAsString();
    }

    private double doubleValue(JsonObject fields, String name) {
        if (!fields.has(name)) return 0.0;
        JsonObject field = fields.getAsJsonObject(name);
        if (field.has("doubleValue")) return field.get("doubleValue").getAsDouble();
        if (field.has("integerValue")) return field.get("integerValue").getAsDouble();
        return 0.0;
    }

    private HttpResponse<String> sendAndReturn(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to Firestore.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore request was interrupted.", e);
        }
    }

    private void send(HttpRequest request) {
        sendAndReturn(request);
    }

    private HttpRequest.Builder authorizedRequest(String url, String idToken) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
    }

    private void validateSession(AuthSession session) {
        if (session == null) throw new RuntimeException("No authenticated session.");
        if (session.getIdToken() == null || session.getIdToken().isBlank()) {
            throw new RuntimeException("Authentication token is missing.");
        }
        if (session.getLocalId() == null || session.getLocalId().isBlank()) {
            throw new RuntimeException("Firebase user ID is missing.");
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
        }
    }
}
