package com.example.brocawebsite.learning;

public record LearningPostRequest(
        String lessonDate,
        String classLabel,
        String category,
        String title,
        String vocabularyText,
        String sentencePattern,
        String homeworkNote,
        String teacherNote,
        String status,
        Boolean pinned
) {
}
