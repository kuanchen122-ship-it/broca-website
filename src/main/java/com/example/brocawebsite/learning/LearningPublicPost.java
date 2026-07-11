package com.example.brocawebsite.learning;

import java.util.List;

public record LearningPublicPost(
        Long id,
        String lessonDate,
        String classLabel,
        String category,
        String categoryLabel,
        String title,
        List<LearningVocabularyItem> vocabulary,
        String sentencePattern,
        String homeworkNote,
        boolean pinned,
        String publishedAt,
        boolean scheduleDerived
) {
}
