package com.example.brocawebsite.config;

public record SystemHealthLineStatus(
        boolean sendingEnabled,
        boolean channelAccessTokenConfigured,
        boolean channelSecretConfigured,
        boolean liffConfigured,
        String mode,
        int activeStudents,
        int linkedStudents,
        int missingLineStudents,
        int coveragePercent
) {
}
