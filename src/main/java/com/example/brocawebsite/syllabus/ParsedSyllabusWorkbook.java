package com.example.brocawebsite.syllabus;

import java.util.List;

record ParsedSyllabusWorkbook(
        String sheetName,
        List<ParsedLessonPlan> lessons,
        List<ParsedStudentEnrollment> studentEnrollments,
        int warningCount,
        List<String> warnings
) {
    long approvedCount() {
        return lessons.stream().filter(lesson -> "DIRECTOR_APPROVED".equals(lesson.approvalStatus())).count();
    }

    long draftCount() {
        return lessons.stream().filter(lesson -> "TEACHER_DRAFT".equals(lesson.approvalStatus())).count();
    }

    long reviewCount() {
        return lessons.stream().filter(lesson -> "NEEDS_REVIEW".equals(lesson.approvalStatus())).count();
    }

    long rosterStudentCount() {
        return studentEnrollments.stream().map(ParsedStudentEnrollment::studentNo).distinct().count();
    }

    long rosterClassCount() {
        return studentEnrollments.stream().map(ParsedStudentEnrollment::classCode).distinct().count();
    }

    long rosterEnrollmentCount() {
        return studentEnrollments.stream()
                .map(enrollment -> enrollment.studentNo() + "|" + enrollment.classCode())
                .distinct()
                .count();
    }
}
