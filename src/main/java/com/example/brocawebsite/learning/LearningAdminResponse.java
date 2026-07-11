package com.example.brocawebsite.learning;

import java.util.List;

public record LearningAdminResponse(
        LearningPostSummary summary,
        List<String> classLabels,
        List<LearningAdminPost> posts
) {
}
