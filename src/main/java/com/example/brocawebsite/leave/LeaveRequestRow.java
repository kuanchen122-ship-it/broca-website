package com.example.brocawebsite.leave;

public record LeaveRequestRow(
        Long id,
        Long studentId,
        String studentNo,
        String studentName,
        String englishName,
        Long classId,
        String classCode,
        String className,
        String leaveDate,
        String reasonType,
        String reasonLabel,
        String reasonText,
        String status,
        String statusLabel,
        String source,
        String reviewedBy,
        String reviewedAt,
        String reviewNote,
        String createdAt
) {
}
