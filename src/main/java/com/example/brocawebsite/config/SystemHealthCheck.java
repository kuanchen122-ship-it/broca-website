package com.example.brocawebsite.config;

public record SystemHealthCheck(
        String key,
        String label,
        String status,
        String value,
        String detail,
        String actionLabel,
        String actionHref
) {
}
