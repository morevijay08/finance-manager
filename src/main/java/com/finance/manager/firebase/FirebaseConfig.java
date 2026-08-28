package com.finance.manager.firebase;

public final class FirebaseConfig {

    private static final String API_KEY_PROPERTY = "FIREBASE_WEB_API_KEY";
    private static final String API_KEY_ENV = "FIREBASE_WEB_API_KEY";

    private FirebaseConfig() {
    }

    public static String getWebApiKey() {
        String propertyValue = System.getProperty(API_KEY_PROPERTY);
        String environmentValue = System.getenv(API_KEY_ENV);
        String apiKey = propertyValue != null && !propertyValue.isBlank()
                ? propertyValue.trim()
                : environmentValue;

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Firebase Web API key is not configured. Set FIREBASE_WEB_API_KEY "
                            + "as a Windows environment variable or JVM system property."
            );
        }

        return apiKey.trim();
    }
}
