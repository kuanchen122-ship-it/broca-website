package com.example.brocawebsite.student;

import java.util.List;

import com.example.brocawebsite.attendance.ClassOption;

public record StudentDirectoryResponse(
        StudentDirectorySummary summary,
        List<ClassOption> classes,
        List<StudentDirectoryRow> students
) {
}
