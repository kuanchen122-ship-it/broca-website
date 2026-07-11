package com.example.brocawebsite.learning;

import java.util.List;

public record LearningAdminPost(
        Long id,
        String lessonDate,
        String classLabel,
        String category,
        String categoryLabel,
        String title,
        String vocabularyText,
        List<LearningVocabularyItem> vocabulary,
        String sentencePattern,
        String homeworkNote,
        String teacherNote,
        String status,
        String statusLabel,
        boolean pinned,
        String publishedAt,
        String createdBy,
        String updatedBy,
        String createdAt,
        String updatedAt
) {
}
