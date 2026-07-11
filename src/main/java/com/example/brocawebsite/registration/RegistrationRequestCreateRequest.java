package com.example.brocawebsite.registration;

import java.util.List;

public record RegistrationRequestCreateRequest(
        String inquiryType,
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
        Boolean privacyAccepted
) {
}
