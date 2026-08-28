package com.finance.manager.firebase;

public class FirebaseAuthException extends Exception {

    private final String errorCode;

    public FirebaseAuthException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
