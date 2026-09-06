package com.finance.manager.service;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseAuthException;
import com.finance.manager.firebase.FirebaseConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class FirebaseEmailVerificationService {
    private static final String AUTH_BASE_URL = "https://identitytoolkit.googleapis.com/v1/accounts:";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Void> sendVerificationEmail(AuthSession session) {
        return CompletableFuture.runAsync(() -> {
            try {
                requireSession(session);
                JsonObject request = new JsonObject();
                request.addProperty("requestType", "VERIFY_EMAIL");
                request.addProperty("idToken", session.getIdToken());
                post("sendOobCode", request);
            } catch (FirebaseAuthException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Boolean> isEmailVerified(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                requireSession(session);
                JsonObject request = new JsonObject();
                request.addProperty("idToken", session.getIdToken());
                JsonObject response = post("lookup", request);
                JsonArray users = response.getAsJsonArray("users");
                return users != null && !users.isEmpty()
                        && users.get(0).getAsJsonObject().has("emailVerified")
                        && users.get(0).getAsJsonObject().get("emailVerified").getAsBoolean();
            } catch (FirebaseAuthException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void requireSession(AuthSession session) throws FirebaseAuthException {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()) {
            throw new FirebaseAuthException("NO_SESSION", "No authenticated session is available.");
        }
    }

    private JsonObject post(String endpoint, JsonObject body)
            throws IOException, InterruptedException, FirebaseAuthException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_BASE_URL + endpoint + "?key=" + FirebaseConfig.getWebApiKey()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new FirebaseAuthException("VERIFICATION_ERROR", "Unable to process email verification.");
        }
        return response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
