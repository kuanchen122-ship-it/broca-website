package com.example.brocawebsite.config;

import java.util.List;

public record SystemHealthResponse(
        String overallStatus,
        String overallLabel,
        int readyCount,
        int attentionCount,
        SystemHealthRuntimeStatus runtime,
        SystemHealthSummary summary,
        SystemHealthLineStatus line,
        SystemHealthImportStatus currentImport,
        List<SystemHealthCheck> checks
) {
}
