package com.example.brocawebsite.syllabus;

public record ScheduleLessonCard(
        Long id,
        String lessonDate,
        String classLabel,
        String teacherNames,
        String contentSummary,
        String content,
        String approvalStatus,
        String approvalLabel,
        String sourceCell
) {
}
