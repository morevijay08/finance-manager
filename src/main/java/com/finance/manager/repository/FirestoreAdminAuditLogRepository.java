package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.AdminAuditLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FirestoreAdminAuditLogRepository {
    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/"
            + PROJECT_ID + "/databases/(default)/documents/adminAuditLogs/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Void> createLog(AuthSession adminSession, String action, String targetUserEmail) {
        return CompletableFuture.runAsync(() -> {
            validateSession(adminSession);
            String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
            String url = BASE_URL + URLEncoder.encode(id, StandardCharsets.UTF_8)
                    + "?key=" + FirebaseConfig.getWebApiKey();

            JsonObject fields = new JsonObject();
            addString(fields, "adminEmail", adminSession.getEmail());
            addString(fields, "action", action);
            addString(fields, "targetUserEmail", targetUserEmail);
            addString(fields, "timestamp", Instant.now().toString());

            JsonObject document = new JsonObject();
            document.add("fields", fields);
            send("PATCH", url, adminSession.getIdToken(), document);
        });
    }

    public CompletableFuture<List<AdminAuditLog>> listLogs(AuthSession adminSession) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(adminSession);
            String url = BASE_URL + "?pageSize=200&key=" + FirebaseConfig.getWebApiKey();
            JsonObject response = send("GET", url, adminSession.getIdToken(), null);
            List<AdminAuditLog> logs = new ArrayList<>();
            if (!response.has("documents")) return logs;

            JsonArray documents = response.getAsJsonArray("documents");
            documents.forEach(element -> {
                JsonObject document = element.getAsJsonObject();
                JsonObject fields = document.has("fields")
                        ? document.getAsJsonObject("fields") : new JsonObject();
                String name = document.has("name") ? document.get("name").getAsString() : "";
                String id = name.substring(name.lastIndexOf('/') + 1);
                logs.add(new AdminAuditLog(
                        id,
                        stringField(fields, "adminEmail", ""),
                        stringField(fields, "action", ""),
                        stringField(fields, "targetUserEmail", ""),
                        stringField(fields, "timestamp", "")
                ));
            });

            logs.sort(Comparator.comparing(AdminAuditLog::timestamp, Comparator.nullsLast(String::compareTo)).reversed());
            return logs;
        });
    }

    private JsonObject send(String method, String url, String idToken, JsonObject body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + idToken)
                    .header("Content-Type", "application/json");
            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "PATCH" -> builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString())).build();
                default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
            };
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccess(response);
            return response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Unable to connect to Firestore.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore request was interrupted.", e);
        }
    }

    private void validateSession(AuthSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()) {
            throw new RuntimeException("No authenticated session.");
        }
        if (session.getLocalId() == null || session.getLocalId().isBlank()) {
            throw new RuntimeException("Firebase user ID is missing.");
        }
    }

    private String stringField(JsonObject fields, String name, String fallback) {
        if (!fields.has(name)) return fallback;
        JsonObject value = fields.getAsJsonObject(name);
        if (value == null || !value.has("stringValue")) return fallback;
        return value.get("stringValue").getAsString();
    }

    private void addString(JsonObject fields, String name, String value) {
        JsonObject field = new JsonObject();
        field.addProperty("stringValue", value == null ? "" : value);
        fields.add(name, field);
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
        }
    }
}
