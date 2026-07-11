package com.example.brocawebsite.registration;

public record RegistrationRequestSummary(
        int newRequests,
        int contactedRequests,
        int followUpRequests,
        int waitlistRequests,
        int teacherReviewRequests,
        int directorReviewRequests,
        int enrolledRequests,
        int archivedRequests,
        int todayRequests,
        int weekRequests
) {
}
