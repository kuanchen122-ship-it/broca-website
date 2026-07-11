package com.example.brocawebsite.syllabus;

import java.util.List;

public record ScheduleDayResponse(
        boolean hasImport,
        Long batchId,
        String fileName,
        String selectedDate,
        String displayDate,
        String firstLessonDate,
        String lastLessonDate,
        String previousLessonDate,
        String nextLessonDate,
        List<ScheduleLessonCard> lessons
) {
}
