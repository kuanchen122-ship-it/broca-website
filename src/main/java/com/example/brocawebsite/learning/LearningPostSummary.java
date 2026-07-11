package com.example.brocawebsite.learning;

public record LearningPostSummary(
        int draftCount,
        int publishedCount,
        int archivedCount,
        int todayPublishedCount,
        String latestPublishedDate
) {
}
