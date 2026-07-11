package com.example.brocawebsite.syllabus;

import java.time.LocalDate;

record ParsedLessonPlan(
        LocalDate lessonDate,
        String classLabel,
        String teacherNames,
        String content,
        String approvalStatus,
        String approvalSourceValue,
        String sourceSheet,
        int sourceRowNumber,
        String sourceColumnLabel
) {
}
