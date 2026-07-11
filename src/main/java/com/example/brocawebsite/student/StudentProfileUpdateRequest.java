package com.example.brocawebsite.student;

public record StudentProfileUpdateRequest(
        String chineseName,
        String englishName,
        String school,
        String gradeLevel,
        String parentName,
        String parentPhone,
        String parentLineId,
        String pickupNote,
        String emergencyContactName,
        String emergencyContactPhone,
        Boolean active
) {
}
