package com.example.brocawebsite.line;

public record LineAttendanceQueueSummary(
        int totalAttendanceRecords,
        int requestedButNotConfigured,
        int pending,
        int sent,
        int failed,
        int notRequested,
        String latestRecordedAt
) {
}
