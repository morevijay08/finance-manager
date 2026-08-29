package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.concurrent.CompletableFuture;

public class FirestoreBudgetRepository {

    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String USERS_URL =
            "https://firestore.googleapis.com/v1/projects/"
                    + PROJECT_ID + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Double> getMonthlyBudget(AuthSession session, YearMonth month) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            HttpRequest request = authorizedRequest(documentUrl(session, month), session.getIdToken())
                    .GET().build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) return 0.0;
                ensureSuccess(response);
                JsonObject fields = JsonParser.parseString(response.body()).getAsJsonObject()
                        .getAsJsonObject("fields");
                if (fields == null || !fields.has("amount")) return 0.0;
                JsonObject amount = fields.getAsJsonObject("amount");
                if (amount.has("doubleValue")) return amount.get("doubleValue").getAsDouble();
                if (amount.has("integerValue")) return amount.get("integerValue").getAsDouble();
                return 0.0;
            } catch (IOException e) {
                throw new RuntimeException("Unable to load monthly budget.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Budget request was interrupted.", e);
            }
        });
    }

    public CompletableFuture<Void> saveMonthlyBudget(AuthSession session, YearMonth month, double amount) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            if (amount < 0) throw new RuntimeException("Budget cannot be negative.");

            JsonObject fields = new JsonObject();
            JsonObject amountField = new JsonObject();
            amountField.addProperty("doubleValue", amount);
            fields.add("amount", amountField);

            JsonObject monthField = new JsonObject();
            monthField.addProperty("stringValue", month.toString());
            fields.add("month", monthField);

            JsonObject document = new JsonObject();
            document.add("fields", fields);

            HttpRequest request = authorizedRequest(documentUrl(session, month), session.getIdToken())
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(document.toString()))
                    .build();
            sendAndReturn(request);
        });
    }

    private String documentUrl(AuthSession session, YearMonth month) {
        return USERS_URL + encode(session.getLocalId()) + "/budgets/"
                + encode(month.toString()) + "?key=" + FirebaseConfig.getWebApiKey();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpRequest.Builder authorizedRequest(String url, String idToken) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + idToken)
                .header("Content-Type", "application/json");
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
            throw new RuntimeException("Budget request was interrupted.", e);
        }
    }

    private void validateSession(AuthSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()
                || session.getLocalId() == null || session.getLocalId().isBlank()) {
            throw new RuntimeException("Authenticated Firebase session is missing.");
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Firestore error " + response.statusCode() + ": " + response.body());
        }
    }
}
