package com.finance.manager.repository;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseConfig;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

            JsonObject fields = new JsonObject();

            JsonObject nameValue = new JsonObject();
            nameValue.addProperty("stringValue", name);
            fields.add("name", nameValue);

            JsonObject emailValue = new JsonObject();
            emailValue.addProperty("stringValue", session.getEmail());
            fields.add("email", emailValue);

            JsonObject document = new JsonObject();
            document.add("fields", fields);

            String url = FIRESTORE_BASE_URL
                    + session.getLocalId()
                    + "?key="
                    + FirebaseConfig.getWebApiKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(
                            "Authorization",
                            "Bearer " + session.getIdToken()
                    )
                    .header(
                            "Content-Type",
                            "application/json"
                    )
                    .method(
        "PATCH",
        HttpRequest.BodyPublishers.ofString(
                document.toString()
        )
)
                    .build();

            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() < 200 ||
                        response.statusCode() >= 300) {

                    throw new RuntimeException(
                            "Firestore error "
                                    + response.statusCode()
                                    + ": "
                                    + response.body()
                    );
                }

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "Unable to connect to Firestore.",
                        e
                );
            }
        });
    }
}