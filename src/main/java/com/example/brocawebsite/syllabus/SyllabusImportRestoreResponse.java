package com.example.brocawebsite.syllabus;

public record SyllabusImportRestoreResponse(
        Long restoredBatchId,
        String message,
        SyllabusImportResponse currentImport
) {
}
