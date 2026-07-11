package com.example.brocawebsite.student;

import java.util.List;

public record ParentContactImportPreviewResponse(
        boolean applied,
        int totalRows,
        int matchedRows,
        int readyToUpdateRows,
        int unchangedRows,
        int notFoundRows,
        int duplicateRows,
        int invalidRows,
        int appliedRows,
        ParentContactSummary summary,
        List<ParentContactImportPreviewRow> rows
) {
}
