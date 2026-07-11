package com.example.brocawebsite.syllabus;

public record LessonPlanPreview(
        String lessonDate,
        String classLabel,
        String teacherNames,
        String contentSummary,
        String approvalStatus,
        String approvalLabel,
        String sourceCell,
        String actionLabel
) {
}
