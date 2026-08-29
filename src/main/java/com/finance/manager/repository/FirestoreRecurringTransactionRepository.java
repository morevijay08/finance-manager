package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.RecurringTransaction;
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

public class FirestoreRecurringTransactionRepository {

    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String COLLECTION_URL =
            "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID
                    + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<RecurringTransaction> add(AuthSession session, RecurringTransaction item) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            HttpRequest request = authorizedRequest(collectionUrl(session), session.getIdToken())
                    .POST(HttpRequest.BodyPublishers.ofString(toDocument(item).toString()))
                    .build();
            HttpResponse<String> response = sendAndReturn(request);
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String name = json.has("name") ? json.get("name").getAsString() : "";
            String id = name.isBlank() ? "" : name.substring(name.lastIndexOf('/') + 1);
            return new RecurringTransaction(id, item.getType(), item.getAmount(), item.getCategory(),
                    item.getDescription(), item.getFrequency(), item.getNextDate(), item.isActive());
        });
    }

    public CompletableFuture<List<RecurringTransaction>> getAll(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            HttpRequest request = authorizedRequest(collectionUrl(session), session.getIdToken()).GET().build();
            HttpResponse<String> response = sendAndReturn(request);
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            List<RecurringTransaction> result = new ArrayList<>();
            if (!root.has("documents")) return result;
            JsonArray documents = root.getAsJsonArray("documents");
            for (JsonElement element : documents) {
                JsonObject document = element.getAsJsonObject();
                JsonObject fields = document.getAsJsonObject("fields");
                String name = document.has("name") ? document.get("name").getAsString() : "";
                String id = name.isBlank() ? "" : name.substring(name.lastIndexOf('/') + 1);
                result.add(new RecurringTransaction(
                        id,
                        Transaction.Type.valueOf(stringValue(fields, "type", "EXPENSE")),
                        doubleValue(fields, "amount"),
                        stringValue(fields, "category", "Other"),
                        stringValue(fields, "description", ""),
                        RecurringTransaction.Frequency.valueOf(stringValue(fields, "frequency", "MONTHLY")),
                        LocalDate.parse(stringValue(fields, "nextDate", LocalDate.now().toString())),
                        booleanValue(fields, "active", true)
                ));
            }
            result.sort((a, b) -> a.getNextDate().compareTo(b.getNextDate()));
            return result;
        });
    }

    public CompletableFuture<Void> update(AuthSession session, RecurringTransaction item) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            validateId(item);
            HttpRequest request = authorizedRequest(documentUrl(session, item.getId()), session.getIdToken())
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(toDocument(item).toString()))
                    .build();
            sendAndReturn(request);
        });
    }

    public CompletableFuture<Void> delete(AuthSession session, String id) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            if (id == null || id.isBlank()) throw new RuntimeException("Recurring transaction ID is missing.");
            HttpRequest request = authorizedRequest(documentUrl(session, id), session.getIdToken()).DELETE().build();
            sendAndReturn(request);
        });
    }

    private JsonObject toDocument(RecurringTransaction item) {
        JsonObject fields = new JsonObject();
        fields.add("type", stringField(item.getType().name()));
        fields.add("amount", doubleField(item.getAmount()));
        fields.add("category", stringField(item.getCategory()));
        fields.add("description", stringField(item.getDescription()));
        fields.add("frequency", stringField(item.getFrequency().name()));
        fields.add("nextDate", stringField(item.getNextDate().toString()));
        fields.add("active", booleanField(item.isActive()));
        JsonObject document = new JsonObject();
        document.add("fields", fields);
        return document;
    }

    private String collectionUrl(AuthSession session) {
        return COLLECTION_URL + URLEncoder.encode(session.getLocalId(), StandardCharsets.UTF_8)
                + "/recurringTransactions?key=" + FirebaseConfig.getWebApiKey();
    }

    private String documentUrl(AuthSession session, String id) {
        String base = collectionUrl(session);
        return base.substring(0, base.indexOf("?key=")) + "/"
                + URLEncoder.encode(id, StandardCharsets.UTF_8)
                + "?key=" + FirebaseConfig.getWebApiKey();
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

    private JsonObject booleanField(boolean value) {
        JsonObject field = new JsonObject();
        field.addProperty("booleanValue", value);
        return field;
    }

    private String stringValue(JsonObject fields, String name, String fallback) {
        if (!fields.has(name) || !fields.getAsJsonObject(name).has("stringValue")) return fallback;
        return fields.getAsJsonObject(name).get("stringValue").getAsString();
    }

    private double doubleValue(JsonObject fields, String name) {
        if (!fields.has(name)) return 0.0;
        JsonObject field = fields.getAsJsonObject(name);
        if (field.has("doubleValue")) return field.get("doubleValue").getAsDouble();
        if (field.has("integerValue")) return field.get("integerValue").getAsDouble();
        return 0.0;
    }

    private boolean booleanValue(JsonObject fields, String name, boolean fallback) {
        if (!fields.has(name) || !fields.getAsJsonObject(name).has("booleanValue")) return fallback;
        return fields.getAsJsonObject(name).get("booleanValue").getAsBoolean();
    }

    private HttpResponse<String> sendAndReturn(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
            }
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to Firestore.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore request was interrupted.", e);
        }
    }

    private HttpRequest.Builder authorizedRequest(String url, String token) {
        return HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }

    private void validateSession(AuthSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()
                || session.getLocalId() == null || session.getLocalId().isBlank()) {
            throw new RuntimeException("Authenticated session is missing.");
        }
    }

    private void validateId(RecurringTransaction item) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            throw new RuntimeException("Recurring transaction ID is missing.");
        }
    }
}
