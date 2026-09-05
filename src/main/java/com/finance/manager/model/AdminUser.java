package com.finance.manager.model;

public record AdminUser(
        String id,
        String name,
        String email,
        String role,
        String status
) {
    public String displayName() {
        return name == null || name.isBlank() ? "User" : name;
    }

    public String displayRole() {
        return role == null || role.isBlank() ? "USER" : role;
    }

    public String displayStatus() {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }
}
