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
    private static final String COLLECTION_URL = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/users/";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Transaction> addTransaction(AuthSession session, Transaction transaction) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            HttpRequest request = authorizedRequest(collectionUrl(session), session.getIdToken())
                    .POST(HttpRequest.BodyPublishers.ofString(transactionDocument(transaction).toString())).build();
            JsonObject responseJson = JsonParser.parseString(sendAndReturn(request).body()).getAsJsonObject();
            String documentName = responseJson.has("name") ? responseJson.get("name").getAsString() : "";
            String id = documentName.isBlank() ? "" : documentName.substring(documentName.lastIndexOf('/') + 1);
            return new Transaction(id, transaction.getType(), transaction.getAmount(), transaction.getCategory(), transaction.getDescription(), transaction.getDate(), transaction.getPersonName());
        });
    }

    public CompletableFuture<List<Transaction>> getTransactions(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            JsonObject root = JsonParser.parseString(sendAndReturn(authorizedRequest(collectionUrl(session), session.getIdToken()).GET().build()).body()).getAsJsonObject();
            List<Transaction> transactions = new ArrayList<>();
            if (!root.has("documents")) return transactions;
            for (JsonElement element : root.getAsJsonArray("documents")) {
                JsonObject document = element.getAsJsonObject();
                JsonObject fields = document.getAsJsonObject("fields");
                String name = document.has("name") ? document.get("name").getAsString() : "";
                String id = name.isBlank() ? "" : name.substring(name.lastIndexOf('/') + 1);
                transactions.add(new Transaction(id,
                        Transaction.Type.valueOf(stringValue(fields, "type", "EXPENSE")),
                        doubleValue(fields, "amount"),
                        stringValue(fields, "category", "Other"),
                        stringValue(fields, "description", ""),
                        LocalDate.parse(stringValue(fields, "date", LocalDate.now().toString())),
                        stringValue(fields, "personName", "")));
            }
            transactions.sort((a, b) -> b.getDate().compareTo(a.getDate()));
            return transactions;
        });
    }

    public CompletableFuture<Transaction> updateTransaction(AuthSession session, Transaction transaction) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session); validateTransactionId(transaction);
            sendAndReturn(authorizedRequest(documentUrl(session, transaction.getId()), session.getIdToken())
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(transactionDocument(transaction).toString())).build());
            return transaction;
        });
    }

    public CompletableFuture<Void> deleteTransaction(AuthSession session, String transactionId) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            if (transactionId == null || transactionId.isBlank()) throw new RuntimeException("Transaction ID is missing.");
            sendAndReturn(authorizedRequest(documentUrl(session, transactionId), session.getIdToken()).DELETE().build());
        });
    }

    private JsonObject transactionDocument(Transaction transaction) {
        JsonObject fields = new JsonObject();
        fields.add("type", stringField(transaction.getType().name()));
        fields.add("amount", doubleField(transaction.getAmount()));
        fields.add("category", stringField(transaction.getCategory()));
        fields.add("description", stringField(transaction.getDescription()));
        fields.add("date", stringField(transaction.getDate().toString()));
        fields.add("personName", stringField(transaction.getPersonName()));
        JsonObject document = new JsonObject(); document.add("fields", fields); return document;
    }

    private String collectionUrl(AuthSession session) { return COLLECTION_URL + URLEncoder.encode(session.getLocalId(), StandardCharsets.UTF_8) + "/transactions?key=" + FirebaseConfig.getWebApiKey(); }
    private String documentUrl(AuthSession session, String transactionId) { return collectionUrl(session).substring(0, collectionUrl(session).indexOf("?key=")) + "/" + URLEncoder.encode(transactionId, StandardCharsets.UTF_8) + "?key=" + FirebaseConfig.getWebApiKey(); }
    private JsonObject stringField(String value) { JsonObject field = new JsonObject(); field.addProperty("stringValue", value == null ? "" : value); return field; }
    private JsonObject doubleField(double value) { JsonObject field = new JsonObject(); field.addProperty("doubleValue", value); return field; }
    private String stringValue(JsonObject fields, String name, String fallback) { if (!fields.has(name) || !fields.getAsJsonObject(name).has("stringValue")) return fallback; return fields.getAsJsonObject(name).get("stringValue").getAsString(); }
    private double doubleValue(JsonObject fields, String name) { if (!fields.has(name)) return 0.0; JsonObject field = fields.getAsJsonObject(name); if (field.has("doubleValue")) return field.get("doubleValue").getAsDouble(); if (field.has("integerValue")) return field.get("integerValue").getAsDouble(); return 0.0; }
    private HttpResponse<String> sendAndReturn(HttpRequest request) { try { HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); ensureSuccess(response); return response; } catch (IOException e) { throw new RuntimeException("Unable to connect to Firestore.", e); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException("Firestore request was interrupted.", e); } }
    private HttpRequest.Builder authorizedRequest(String url, String idToken) { return HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + idToken).header("Content-Type", "application/json"); }
    private void validateSession(AuthSession session) { if (session == null) throw new RuntimeException("No authenticated session."); if (session.getIdToken() == null || session.getIdToken().isBlank()) throw new RuntimeException("Authentication token is missing."); if (session.getLocalId() == null || session.getLocalId().isBlank()) throw new RuntimeException("Firebase user ID is missing."); }
    private void validateTransactionId(Transaction transaction) { if (transaction == null || transaction.getId() == null || transaction.getId().isBlank()) throw new RuntimeException("Transaction ID is missing."); }
    private void ensureSuccess(HttpResponse<String> response) { if (response.statusCode() < 200 || response.statusCode() >= 300) throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body()); }
}
