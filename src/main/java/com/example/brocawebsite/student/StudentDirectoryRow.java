package com.example.brocawebsite.student;

import java.util.List;

public record StudentDirectoryRow(
        Long id,
        String studentNo,
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
        int completionScore,
        String completionLabel,
        List<String> missingFields,
        String importSource,
        boolean active,
        List<StudentClassMembership> classes
) {
}
