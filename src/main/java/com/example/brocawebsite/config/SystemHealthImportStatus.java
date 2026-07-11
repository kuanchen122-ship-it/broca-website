package com.example.brocawebsite.config;

public record SystemHealthImportStatus(
        boolean hasImport,
        Long batchId,
        String fileName,
        String sheetName,
        String importedAt,
        String dateRange,
        int totalLessons,
        int warningCount,
        boolean rosterSyncRequested,
        int rosterStudentCount,
        int rosterClassCount,
        int rosterEnrollmentCount
) {
    public static SystemHealthImportStatus empty() {
        return new SystemHealthImportStatus(false, null, "", "", "", "尚未建立資料範圍", 0, 0,
                false, 0, 0, 0);
    }
}
