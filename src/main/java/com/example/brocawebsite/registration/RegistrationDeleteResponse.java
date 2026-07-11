package com.example.brocawebsite.registration;

public record RegistrationDeleteResponse(
        Long requestId,
        String requestCode,
        boolean deleted,
        String message
) {
}
