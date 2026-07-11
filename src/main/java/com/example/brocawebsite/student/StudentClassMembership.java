package com.example.brocawebsite.student;

public record StudentClassMembership(
        Long id,
        String code,
        String name,
        String category,
        int activeStudentCount
) {
}
