package com.example.brocawebsite.syllabus;

public record SyllabusImportBatchHistoryRow(
        Long id,
        String fileName,
        String sheetName,
        String importedAt,
        String importedByName,
        String activatedAt,
        boolean active,
        String status,
        int totalLessons,
        int approvedCount,
        int draftCount,
        int reviewCount,
        int warningCount,
        boolean syncRoster,
        int rosterStudentCount,
        int rosterClassCount,
        int rosterEnrollmentCount,
        String firstLessonDate,
        String lastLessonDate,
        String dateRange,
        boolean canRestore
) {
}
