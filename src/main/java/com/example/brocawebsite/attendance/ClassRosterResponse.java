package com.example.brocawebsite.attendance;

import java.util.List;

public record ClassRosterResponse(
        Long classId,
        String classCode,
        String className,
        String category,
        int activeStudentCount,
        List<StudentRosterRow> students
) {
}
