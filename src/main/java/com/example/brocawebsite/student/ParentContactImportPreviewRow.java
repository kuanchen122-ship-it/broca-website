package com.example.brocawebsite.student;

import java.util.List;

public record ParentContactImportPreviewRow(
        int rowNumber,
        String status,
        String message,
        boolean willUpdate,
        Long matchedStudentId,
        String matchedStudentName,
        String matchMethod,
        String studentNo,
        String chineseName,
        String englishName,
        String school,
        String gradeLevel,
        String classCodes,
        String parentName,
        String parentPhone,
        String parentLineId,
        String pickupNote,
        String emergencyContactName,
        String emergencyContactPhone,
        List<String> changes
) {
}
