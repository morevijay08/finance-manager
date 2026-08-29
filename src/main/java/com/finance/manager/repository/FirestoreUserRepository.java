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
import java.util.concurrent.CompletableFuture;

public class FirestoreUserRepository {

    private static final String PROJECT_ID = "khatabook-finance-manager";

    private static final String FIRESTORE_BASE_URL =
            "https://firestore.googleapis.com/v1/projects/"
                    + PROJECT_ID
                    + "/databases/(default)/documents/users/";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public CompletableFuture<Void> createUserProfile(
            AuthSession session,
            String name
    ) {
        return CompletableFuture.runAsync(() -> {

            validateSession(session);

            JsonObject fields = new JsonObject();

            JsonObject nameValue = new JsonObject();
            nameValue.addProperty("stringValue", name);
            fields.add("name", nameValue);

            JsonObject emailValue = new JsonObject();
            emailValue.addProperty("stringValue", session.getEmail());
            fields.add("email", emailValue);

            JsonObject document = new JsonObject();
            document.add("fields", fields);

            String url = userDocumentUrl(session);

            HttpRequest request = authorizedRequest(url)
                    .method(
                            "PATCH",
                            HttpRequest.BodyPublishers.ofString(document.toString())
                    )
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                ensureSuccess(response);

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Unable to connect to Firestore.", e);
            }
        });
    }

    /**
     * Reads the user's profile from users/{Firebase UID}.
     * The UID comes from Firebase Authentication, so each user can only
     * retrieve their own profile when Firestore rules are configured correctly.
     */
    public CompletableFuture<String> getUserName(AuthSession session) {
        return CompletableFuture.supplyAsync(() -> {

            validateSession(session);

            HttpRequest request = authorizedRequest(userDocumentUrl(session))
                    .GET()
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                ensureSuccess(response);

                JsonObject document = JsonParser.parseString(response.body())
                        .getAsJsonObject();

                if (!document.has("fields") ||
                        !document.getAsJsonObject("fields").has("name")) {
                    return "User";
                }

                JsonObject fields = document.getAsJsonObject("fields");
                JsonObject nameField = fields.getAsJsonObject("name");

                if (nameField == null ||
                        !nameField.has("stringValue")) {
                    return "User";
                }

                String name = nameField.get("stringValue").getAsString();
                return name == null || name.isBlank() ? "User" : name;

            } catch (IOException e) {
                throw new RuntimeException("Unable to connect to Firestore.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Firestore request was interrupted.", e);
            }
        });
    }

    private void validateSession(AuthSession session) {
        if (session == null) {
            throw new RuntimeException("No authenticated session.");
        }

        if (session.getIdToken() == null ||
                session.getIdToken().isBlank()) {
            throw new RuntimeException("Authentication token is missing.");
        }

        if (session.getLocalId() == null ||
                session.getLocalId().isBlank()) {
            throw new RuntimeException("Firebase user ID is missing.");
        }
    }

    private String userDocumentUrl(AuthSession session) {
        return FIRESTORE_BASE_URL
                + URLEncoder.encode(session.getLocalId(), StandardCharsets.UTF_8)
                + "?key="
                + FirebaseConfig.getWebApiKey();
    }

    private HttpRequest.Builder authorizedRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + AuthSessionHolder.currentToken())
                .header("Content-Type", "application/json");
    }

    private void ensureSuccess(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Firestore error "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }
    }

    private static final class AuthSessionHolder {
        private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

        private AuthSessionHolder() {
        }

        static String currentToken() {
            String token = TOKEN.get();
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Authentication token is missing.");
            }
            return token;
        }
    }
}
