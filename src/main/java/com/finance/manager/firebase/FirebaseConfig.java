package com.finance.manager.firebase;

import io.github.cdimascio.dotenv.Dotenv;

public final class FirebaseConfig {

    private static final String API_KEY_PROPERTY = "FIREBASE_WEB_API_KEY";
    private static final String API_KEY_ENV = "FIREBASE_WEB_API_KEY";
    private static final String EMAIL_VERIFICATION_PROPERTY = "REQUIRE_EMAIL_VERIFICATION";
    private static final String EMAIL_VERIFICATION_ENV = "REQUIRE_EMAIL_VERIFICATION";

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private FirebaseConfig() {
    }

    public static String getWebApiKey() {
        String propertyValue = System.getProperty(API_KEY_PROPERTY);
        String apiKey = propertyValue != null && !propertyValue.isBlank()
                ? propertyValue.trim()
                : DOTENV.get(API_KEY_ENV);

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Firebase Web API key is not configured. Create a .env file with "
                            + "FIREBASE_WEB_API_KEY=your_key or set FIREBASE_WEB_API_KEY "
                            + "as a Windows environment variable or JVM system property."
            );
        }

        return apiKey.trim();
    }

    /**
     * Controls whether users must verify their email before logging in.
     * Default is false so email verification can be enabled later without code changes.
     *
     * Set REQUIRE_EMAIL_VERIFICATION=true in .env or as a JVM system property when ready.
     */
    public static boolean isEmailVerificationRequired() {
        String propertyValue = System.getProperty(EMAIL_VERIFICATION_PROPERTY);
        String configuredValue = propertyValue != null && !propertyValue.isBlank()
                ? propertyValue.trim()
                : DOTENV.get(EMAIL_VERIFICATION_ENV);

        return configuredValue != null && Boolean.parseBoolean(configuredValue.trim());
    }
}
