package com.example.brocawebsite.registration;

public record RegistrationEnrollmentRequest(
        Long classId,
        Boolean force,
        String reviewNote
) {
}
