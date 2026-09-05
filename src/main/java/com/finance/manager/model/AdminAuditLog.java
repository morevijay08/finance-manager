package com.finance.manager.model;

public record AdminAuditLog(
        String id,
        String adminEmail,
        String action,
        String targetUserEmail,
        String timestamp
) {
}
