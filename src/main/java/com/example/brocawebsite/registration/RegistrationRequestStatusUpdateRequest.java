package com.example.brocawebsite.registration;

public record RegistrationRequestStatusUpdateRequest(
        String status,
        String reviewNote
) {
}
