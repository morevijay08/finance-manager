package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.AdminUser;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FirestoreUserRepository {
    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String FIRESTORE_BASE_URL = "https://firestore.googleapis.com/v1/projects/" + PROJECT_ID + "/databases/(default)/documents/users/";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Void> createUserProfile(AuthSession session, String name) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            JsonObject fields = new JsonObject();
            addString(fields, "name", name);
            addString(fields, "email", session.getEmail());
            addString(fields, "role", "USER");
            addString(fields, "status", "ACTIVE");
            JsonObject document = new JsonObject();
            document.add("fields", fields);
            send("PATCH", userDocumentUrl(session), session.getIdToken(), document);
        });
    }

    public CompletableFuture<String> getUserName(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> stringField(getUserDocument(session), "name", "User"));
    }

    public CompletableFuture<String> getUserRole(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> stringField(getUserDocument(session), "role", "USER").toUpperCase());
    }

    public CompletableFuture<List<AdminUser>> listUsers(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            String url = FIRESTORE_BASE_URL + "?pageSize=1000&key=" + FirebaseConfig.getWebApiKey();
            JsonObject response = send("GET", url, session.getIdToken(), null);
            List<AdminUser> users = new ArrayList<>();
            if (!response.has("documents")) return users;
            JsonArray documents = response.getAsJsonArray("documents");
            documents.forEach(element -> {
                JsonObject document = element.getAsJsonObject();
                JsonObject fields = document.has("fields") ? document.getAsJsonObject("fields") : new JsonObject();
                String fullName = document.has("name") ? document.get("name").getAsString() : "";
                String id = fullName.substring(fullName.lastIndexOf('/') + 1);
                users.add(new AdminUser(id,
                        stringField(fields, "name", "User"),
                        stringField(fields, "email", ""),
                        stringField(fields, "role", "USER"),
                        stringField(fields, "status", "ACTIVE")));
            });
            return users;
        });
    }

    public CompletableFuture<Void> updateUserStatus(AuthSession adminSession, String userId, String status) {
        return CompletableFuture.runAsync(() -> {
            validateSession(adminSession);
            if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) throw new IllegalArgumentException("Invalid user status.");
            String url = FIRESTORE_BASE_URL + URLEncoder.encode(userId, StandardCharsets.UTF_8)
                    + "?updateMask.fieldPaths=status&key=" + FirebaseConfig.getWebApiKey();
            JsonObject fields = new JsonObject();
            addString(fields, "status", status);
            JsonObject document = new JsonObject();
            document.add("fields", fields);
            send("PATCH", url, adminSession.getIdToken(), document);
        });
    }

    private JsonObject getUserDocument(AuthSession session) {
        validateSession(session);
        JsonObject response = send("GET", userDocumentUrl(session), session.getIdToken(), null);
        return response.has("fields") ? response.getAsJsonObject("fields") : new JsonObject();
    }

    private JsonObject send(String method, String url, String idToken, JsonObject body) {
        try {
            HttpRequest.Builder builder = authorizedRequest(url, idToken);
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
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()) throw new RuntimeException("No authenticated session.");
        if (session.getLocalId() == null || session.getLocalId().isBlank()) throw new RuntimeException("Firebase user ID is missing.");
    }

    private String userDocumentUrl(AuthSession session) {
        return FIRESTORE_BASE_URL + URLEncoder.encode(session.getLocalId(), StandardCharsets.UTF_8) + "?key=" + FirebaseConfig.getWebApiKey();
    }

    private HttpRequest.Builder authorizedRequest(String url, String idToken) {
        return HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer " + idToken).header("Content-Type", "application/json");
    }

    private String stringField(JsonObject fields, String name, String fallback) {
        if (!fields.has(name)) return fallback;
        JsonObject value = fields.getAsJsonObject(name);
        if (value == null || !value.has("stringValue")) return fallback;
        String result = value.get("stringValue").getAsString();
        return result.isBlank() ? fallback : result;
    }

    private void addString(JsonObject fields, String name, String value) {
        JsonObject field = new JsonObject();
        field.addProperty("stringValue", value == null ? "" : value);
        fields.add(name, field);
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
    }
}
