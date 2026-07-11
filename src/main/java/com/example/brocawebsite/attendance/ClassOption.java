package com.example.brocawebsite.attendance;

public record ClassOption(
        Long id,
        String code,
        String name,
        String category,
        int activeStudentCount
) {
}
