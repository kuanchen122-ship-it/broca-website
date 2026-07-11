package com.example.brocawebsite.registration;

import java.util.List;

public record RegistrationRequestRow(
        Long id,
        String requestCode,
        String status,
        String statusLabel,
        String inquiryType,
        String inquiryTypeLabel,
        String currentNeed,
        List<String> programInterests,
        String studentChineseName,
        String studentEnglishName,
        String gradeLevel,
        String school,
        String englishLevel,
        String parentName,
        String parentPhone,
        String parentLineId,
        String preferredContactTime,
        String contactPreference,
        String needHomeworkCare,
        String needPickupSupport,
        List<String> availableTimes,
        String notes,
        String referralSource,
        String photoConsent,
        String photoConsentLabel,
        String source,
        String reviewedBy,
        String reviewedAt,
        String reviewNote,
        Long enrolledStudentId,
        Long enrolledClassId,
        String enrolledAt,
        String createdAt
) {
}
