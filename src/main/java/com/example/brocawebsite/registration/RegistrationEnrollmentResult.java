package com.example.brocawebsite.registration;

import java.util.List;

public record RegistrationEnrollmentResult(
        Long studentId,
        String studentNo,
        Long classId,
        boolean created,
        boolean enrolled,
        String message,
        List<RegistrationDuplicateCandidate> duplicates
) {
}
