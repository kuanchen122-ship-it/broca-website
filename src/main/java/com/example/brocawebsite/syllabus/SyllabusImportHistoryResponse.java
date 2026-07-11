package com.example.brocawebsite.syllabus;

import java.util.List;

public record SyllabusImportHistoryResponse(
        Long activeBatchId,
        int totalBatches,
        List<SyllabusImportBatchHistoryRow> batches
) {
}
