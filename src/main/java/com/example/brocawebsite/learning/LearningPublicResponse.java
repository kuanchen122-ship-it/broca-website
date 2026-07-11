package com.example.brocawebsite.learning;

import java.util.List;

public record LearningPublicResponse(
        String requestedDate,
        String displayDate,
        boolean usingLatestFallback,
        boolean includesApprovedSchedule,
        List<String> classLabels,
        List<LearningPublicPost> posts
) {
}
