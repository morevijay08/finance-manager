package com.finance.manager.service;

import com.finance.manager.firebase.AuthSession;
import com.finance.manager.firebase.FirebaseAuthException;
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

public class FirebaseAuthService {

    private static final String AUTH_BASE_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:";
    private static final String TOKEN_URL =
            "https://securetoken.googleapis.com/v1/token?key=";
    private static final long EXPIRY_SAFETY_WINDOW_MS = 60_000L;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private volatile AuthSession currentSession;

    public CompletableFuture<AuthSession> register(String name, String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("email", email);
                request.addProperty("password", password);
                request.addProperty("returnSecureToken", true);

                AuthSession session = authenticate("signUp", request);

                // Firebase Auth supports a displayName update through the same REST API.
                JsonObject profile = new JsonObject();
                profile.addProperty("idToken", session.getIdToken());
                profile.addProperty("displayName", name);
                profile.addProperty("returnSecureToken", true);

                try {
                    JsonObject response = postJson("update", profile);
                    session = sessionFromResponse(response, session.getEmail());
                } catch (FirebaseAuthException ignored) {
                    // Account creation succeeded. The display name can be stored in Firestore later.
                }

                currentSession = session;
                return session;
            } catch (FirebaseAuthException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<AuthSession> signIn(String email, String password) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("email", email);
                request.addProperty("password", password);
                request.addProperty("returnSecureToken", true);

                AuthSession session = authenticate("signInWithPassword", request);
                currentSession = session;
                return session;
            } catch (FirebaseAuthException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<Void> sendPasswordResetEmail(String email) {
        return CompletableFuture.runAsync(() -> {
            try {
                JsonObject request = new JsonObject();
                request.addProperty("requestType", "PASSWORD_RESET");
                request.addProperty("email", email);
                postJson("sendOobCode", request);
            } catch (FirebaseAuthException | IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<AuthSession> refreshSession() {
        return CompletableFuture.supplyAsync(() -> {
            AuthSession session = currentSession;
            if (session == null || session.getRefreshToken() == null || session.getRefreshToken().isBlank()) {
                throw new RuntimeException(new FirebaseAuthException(
                        "NO_SESSION", "No active authentication session is available."
                ));
            }

            try {
                String body = "grant_type=refresh_token&refresh_token="
                        + URLEncoder.encode(session.getRefreshToken(), StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(TOKEN_URL + FirebaseConfig.getWebApiKey()))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw parseError(response.body());
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                long expiresIn = json.get("expires_in").getAsLong();
                AuthSession refreshed = new AuthSession(
                        json.get("id_token").getAsString(),
                        json.get("refresh_token").getAsString(),
                        json.get("user_id").getAsString(),
                        session.getEmail(),
                        System.currentTimeMillis() + expiresIn * 1000L
                );

                currentSession = refreshed;
                return refreshed;
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (FirebaseAuthException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public AuthSession getCurrentSession() {
        return currentSession;
    }

    public boolean isSignedIn() {
        return currentSession != null && !currentSession.isExpired();
    }

    public boolean needsRefresh() {
        return currentSession != null && currentSession.expiresWithin(EXPIRY_SAFETY_WINDOW_MS);
    }

    public void logout() {
        // Firebase ID tokens expire server-side; logout clears this application's session.
        currentSession = null;
    }

    private AuthSession authenticate(String endpoint, JsonObject request)
            throws IOException, InterruptedException, FirebaseAuthException {
        JsonObject response = postJson(endpoint, request);
        return sessionFromResponse(response, request.get("email").getAsString());
    }

    private AuthSession sessionFromResponse(JsonObject json, String email) {
        long expiresIn = json.get("expiresIn").getAsLong();
        return new AuthSession(
                json.get("idToken").getAsString(),
                json.get("refreshToken").getAsString(),
                json.get("localId").getAsString(),
                email,
                System.currentTimeMillis() + expiresIn * 1000L
        );
    }

    private JsonObject postJson(String endpoint, JsonObject body)
            throws IOException, InterruptedException, FirebaseAuthException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_BASE_URL + endpoint + "?key=" + FirebaseConfig.getWebApiKey()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw parseError(response.body());
        }

        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private FirebaseAuthException parseError(String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String code = root.getAsJsonObject("error").get("message").getAsString();
            return new FirebaseAuthException(code, userMessage(code));
        } catch (Exception ignored) {
            return new FirebaseAuthException("UNKNOWN", "Authentication request failed.");
        }
    }

    private String userMessage(String code) {
        return switch (code) {
            case "EMAIL_EXISTS" -> "An account with this email already exists.";
            case "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" ->
                    "Incorrect email or password.";
            case "USER_DISABLED" -> "This account has been disabled.";
            case "OPERATION_NOT_ALLOWED" -> "Email/password authentication is disabled in Firebase.";
            case "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Please try again later.";
            case "INVALID_EMAIL" -> "Please enter a valid email address.";
            case "WEAK_PASSWORD" -> "The password does not meet Firebase's password policy.";
            default -> "Authentication failed. Please try again.";
        };
    }
}
