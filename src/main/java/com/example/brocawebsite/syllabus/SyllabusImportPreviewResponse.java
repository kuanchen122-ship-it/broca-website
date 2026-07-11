package com.example.brocawebsite.syllabus;

import java.util.List;

public record SyllabusImportPreviewResponse(
        boolean previewOnly,
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
        SyllabusRosterDiff rosterDiff,
        int warningCount,
        List<String> warnings,
        List<LessonPlanPreview> preview
) {
}
