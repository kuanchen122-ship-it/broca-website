package com.example.brocawebsite.student;

import java.util.List;

public record ParentContactRow(
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
        boolean active,
        int completionScore,
        String contactStatus,
        String nextAction,
        List<StudentClassMembership> classes
) {
}
