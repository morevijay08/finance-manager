package com.finance.manager.firebase;

public final class AuthSession {

    private final String idToken;
    private final String refreshToken;
    private final String localId;
    private final String email;
    private final long expiresAtMillis;

    public AuthSession(String idToken, String refreshToken, String localId,
                       String email, long expiresAtMillis) {
        this.idToken = idToken;
        this.refreshToken = refreshToken;
        this.localId = localId;
        this.email = email;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getIdToken() {
        return idToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getLocalId() {
        return localId;
    }

    public String getEmail() {
        return email;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMillis;
    }

    public boolean expiresWithin(long milliseconds) {
        return System.currentTimeMillis() + milliseconds >= expiresAtMillis;
    }
}
