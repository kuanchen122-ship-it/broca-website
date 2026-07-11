package com.example.brocawebsite.student;

public record StudentDirectorySummary(
        int activeStudents,
        int activeClasses,
        int activeEnrollments,
        int missingLineCount,
        int missingDataCount,
        int unassignedStudents,
        int importedStudents
) {
}
