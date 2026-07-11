package com.example.brocawebsite.registration;

import java.util.List;

record RegistrationErrorResponse(
        String message,
        List<RegistrationDuplicateCandidate> duplicates
) {
}
