package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.finance.manager.model.FinancialGoal;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class FirestoreGoalRepository {
    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String USERS_URL = "https://firestore.googleapis.com/v1/projects/"
            + PROJECT_ID + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<List<FinancialGoal>> getGoals(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            try {
                HttpRequest request = authorized(goalsUrl(session), session.getIdToken()).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) return new ArrayList<>();
                ensureSuccess(response);

                List<FinancialGoal> goals = new ArrayList<>();
                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!root.has("documents")) return goals;
                root.getAsJsonArray("documents").forEach(element -> {
                    JsonObject document = element.getAsJsonObject();
                    String name = document.get("name").getAsString();
                    String id = name.substring(name.lastIndexOf('/') + 1);
                    JsonObject fields = document.getAsJsonObject("fields");
                    goals.add(new FinancialGoal(id,
                            stringField(fields, "name"),
                            numberField(fields, "targetAmount"),
                            numberField(fields, "savedAmount")));
                });
                return goals;
            } catch (IOException e) {
                throw new RuntimeException("Unable to load financial goals.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Goal request was interrupted.", e);
            }
        });
    }

    public CompletableFuture<FinancialGoal> addGoal(AuthSession session, FinancialGoal goal) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            if (goal == null || goal.getName() == null || goal.getName().isBlank())
                throw new RuntimeException("Goal name is required.");
            if (goal.getTargetAmount() <= 0 || goal.getSavedAmount() < 0)
                throw new RuntimeException("Enter valid goal amounts.");

            String id = UUID.randomUUID().toString();
            try {
                HttpRequest request = authorized(goalsUrl(session) + "?documentId=" + encode(id)
                                + "&key=" + FirebaseConfig.getWebApiKey(), session.getIdToken())
                        .POST(HttpRequest.BodyPublishers.ofString(goalDocument(goal).toString())).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                ensureSuccess(response);
                return new FinancialGoal(id, goal.getName().trim(), goal.getTargetAmount(), goal.getSavedAmount());
            } catch (IOException e) {
                throw new RuntimeException("Unable to save financial goal.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Goal request was interrupted.", e);
            }
        });
    }

    public CompletableFuture<Void> deleteGoal(AuthSession session, String goalId) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            if (goalId == null || goalId.isBlank()) throw new RuntimeException("Goal ID is missing.");
            try {
                HttpRequest request = authorized(goalsUrl(session) + "/" + encode(goalId)
                                + "?key=" + FirebaseConfig.getWebApiKey(), session.getIdToken())
                        .DELETE().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                ensureSuccess(response);
            } catch (IOException e) {
                throw new RuntimeException("Unable to delete financial goal.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Goal request was interrupted.", e);
            }
        });
    }

    private JsonObject goalDocument(FinancialGoal goal) {
        JsonObject fields = new JsonObject();
        JsonObject name = new JsonObject(); name.addProperty("stringValue", goal.getName().trim()); fields.add("name", name);
        JsonObject target = new JsonObject(); target.addProperty("doubleValue", goal.getTargetAmount()); fields.add("targetAmount", target);
        JsonObject saved = new JsonObject(); saved.addProperty("doubleValue", goal.getSavedAmount()); fields.add("savedAmount", saved);
        JsonObject document = new JsonObject(); document.add("fields", fields);
        return document;
    }

    private String goalsUrl(AuthSession session) { return USERS_URL + encode(session.getLocalId()) + "/goals"; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private HttpRequest.Builder authorized(String url, String token) {
        return HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
    }

    private void validateSession(AuthSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()
                || session.getLocalId() == null || session.getLocalId().isBlank())
            throw new RuntimeException("Authenticated Firebase session is missing.");
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
    }

    private String stringField(JsonObject fields, String key) {
        return fields.has(key) ? fields.getAsJsonObject(key).get("stringValue").getAsString() : "";
    }

    private double numberField(JsonObject fields, String key) {
        if (!fields.has(key)) return 0;
        JsonObject value = fields.getAsJsonObject(key);
        if (value.has("doubleValue")) return value.get("doubleValue").getAsDouble();
        if (value.has("integerValue")) return value.get("integerValue").getAsDouble();
        return 0;
    }
}
