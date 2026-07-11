package com.example.brocawebsite.syllabus;

public record SyllabusImportDeleteResponse(
        Long batchId,
        boolean deleted,
        String message
) {
}
