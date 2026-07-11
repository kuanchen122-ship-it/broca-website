package com.example.brocawebsite.student;

import java.util.List;

import com.example.brocawebsite.attendance.ClassOption;

public record ParentContactResponse(
        ParentContactSummary summary,
        List<ClassOption> classes,
        List<ParentContactRow> contacts
) {
}
