package com.example.brocawebsite.config;

public record SystemHealthRuntimeStatus(
        String applicationName,
        String generatedAt,
        String activeProfiles,
        String databaseProduct,
        String databaseMode,
        String databaseUrl,
        String javaVersion,
        String osName,
        String timeZone
) {
}
