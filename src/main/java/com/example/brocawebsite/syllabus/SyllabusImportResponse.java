package com.example.brocawebsite.syllabus;

import java.util.List;

public record SyllabusImportResponse(
        boolean hasImport,
        Long batchId,
        String fileName,
        String sheetName,
        int totalLessons,
        int approvedCount,
        int draftCount,
        int reviewCount,
        int rosterStudentCount,
        int rosterClassCount,
        int rosterEnrollmentCount,
        boolean rosterSyncRequested,
        String rosterSyncMessage,
        int warningCount,
        List<String> warnings,
        List<LessonPlanPreview> preview
) {
    public static SyllabusImportResponse empty() {
        return new SyllabusImportResponse(false, null, "", "", 0, 0, 0, 0, 0, 0, 0,
                false, "尚未執行學生與分班同步。", 0, List.of(), List.of());
    }
}
