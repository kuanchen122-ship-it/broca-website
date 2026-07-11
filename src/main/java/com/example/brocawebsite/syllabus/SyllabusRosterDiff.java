package com.example.brocawebsite.syllabus;

import java.util.List;

public record SyllabusRosterDiff(
        boolean skipped,
        int newStudents,
        int existingStudents,
        int changedStudents,
        int movedStudents,
        int studentsLosingAssignments,
        int newClasses,
        int existingClasses,
        int newEnrollments,
        int reactivatedEnrollments,
        int unchangedEnrollments,
        int deactivatedEnrollments,
        List<String> newStudentSamples,
        List<String> changedStudentSamples,
        List<String> movedStudentSamples,
        List<String> newClassSamples,
        List<String> newEnrollmentSamples,
        List<String> deactivatedEnrollmentSamples,
        List<String> studentsLosingAssignmentSamples,
        String message
) {
    public static SyllabusRosterDiff noSync() {
        return new SyllabusRosterDiff(true, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "本次未同步學生與分班。");
    }
}
