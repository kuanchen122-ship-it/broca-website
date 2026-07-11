package com.example.brocawebsite.registration;

public record RegistrationDuplicateCandidate(
        Long id,
        String studentNo,
        String chineseName,
        String englishName,
        String gradeLevel,
        String parentPhone,
        String classes
) {
}
