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

/** Firestore persistence for the authenticated user's monthly budgets. */
public class FirestoreBudgetRepository {

    private static final String PROJECT_ID = "khatabook-finance-manager";
    private static final String USERS_URL =
            "https://firestore.googleapis.com/v1/projects/"
                    + PROJECT_ID + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Double> getMonthlyBudget(AuthSession session, YearMonth month) {
        return CompletableFuture.supplyAsync(() -> {
            validateSession(session);
            try {
                HttpRequest request = authorizedRequest(
                        documentUrl(session, month), session.getIdToken()).GET().build();
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 404) return 0.0;
                ensureSuccess(response);
                return readAmount(response.body());
            } catch (IOException e) {
                throw new RuntimeException("Unable to load monthly budget from Firestore.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Budget load was interrupted.", e);
            }
        });
    }

    /**
     * Upserts users/{uid}/budgets/{yyyy-MM} and then verifies the saved value.
     * The verification makes sure the UI never reports success when Firestore
     * rejected or failed to persist the write.
     */
    public CompletableFuture<Void> saveMonthlyBudget(AuthSession session, YearMonth month, double amount) {
        return CompletableFuture.runAsync(() -> {
            validateSession(session);
            if (amount < 0) throw new RuntimeException("Budget cannot be negative.");

            String monthId = month.toString();
            JsonObject document = budgetDocument(month, amount);

            try {
                String documentUrl = documentUrl(session, month);
                HttpRequest getRequest = authorizedRequest(documentUrl, session.getIdToken())
                        .GET().build();
                HttpResponse<String> getResponse = httpClient.send(
                        getRequest, HttpResponse.BodyHandlers.ofString());

                if (getResponse.statusCode() == 200) {
                    HttpRequest patchRequest = authorizedRequest(documentUrl, session.getIdToken())
                            .method("PATCH", HttpRequest.BodyPublishers.ofString(document.toString()))
                            .build();
                    sendAndEnsureSuccess(patchRequest);
                } else if (getResponse.statusCode() == 404) {
                    HttpRequest createRequest = authorizedRequest(
                            budgetsCollectionUrl(session)
                                    + "?documentId=" + encode(monthId)
                                    + "&key=" + FirebaseConfig.getWebApiKey(),
                            session.getIdToken())
                            .POST(HttpRequest.BodyPublishers.ofString(document.toString()))
                            .build();
                    sendAndEnsureSuccess(createRequest);
                } else {
                    ensureSuccess(getResponse);
                }

                double persistedAmount = getMonthlyBudget(session, month).join();
                if (Math.abs(persistedAmount - amount) > 0.000001) {
                    throw new RuntimeException(
                            "Firestore verification failed. Expected " + amount
                                    + " but found " + persistedAmount + ".");
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to connect to Firestore while saving budget.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Budget save was interrupted.", e);
            }
        });
    }

    private double readAmount(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject fields = root.getAsJsonObject("fields");
        if (fields == null || !fields.has("amount")) return 0.0;

        JsonObject amount = fields.getAsJsonObject("amount");
        if (amount.has("doubleValue")) return amount.get("doubleValue").getAsDouble();
        if (amount.has("integerValue")) return amount.get("integerValue").getAsDouble();
        return 0.0;
    }

    private JsonObject budgetDocument(YearMonth month, double amount) {
        JsonObject fields = new JsonObject();

        JsonObject amountField = new JsonObject();
        amountField.addProperty("doubleValue", amount);
        fields.add("amount", amountField);

        JsonObject monthField = new JsonObject();
        monthField.addProperty("stringValue", month.toString());
        fields.add("month", monthField);

        JsonObject document = new JsonObject();
        document.add("fields", fields);
        return document;
    }

    private String budgetsCollectionUrl(AuthSession session) {
        return USERS_URL + encode(session.getLocalId()) + "/budgets";
    }

    private String documentUrl(AuthSession session, YearMonth month) {
        return budgetsCollectionUrl(session) + "/" + encode(month.toString())
                + "?key=" + FirebaseConfig.getWebApiKey();
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

    private void sendAndEnsureSuccess(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response);
    }

    private void validateSession(AuthSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()
                || session.getLocalId() == null || session.getLocalId().isBlank()) {
            throw new RuntimeException("Authenticated Firebase session is missing.");
        }
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Firestore error " + response.statusCode() + ": " + response.body());
        }
    }
}
