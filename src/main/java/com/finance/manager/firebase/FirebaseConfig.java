package com.finance.manager.firebase;

import io.github.cdimascio.dotenv.Dotenv;

public final class FirebaseConfig {

    private static final String API_KEY_PROPERTY = "FIREBASE_WEB_API_KEY";
    private static final String API_KEY_ENV = "FIREBASE_WEB_API_KEY";

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
}
