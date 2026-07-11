package com.example.brocawebsite.attendance;

public record StudentRosterRow(
        Long id,
        String studentNo,
        String chineseName,
        String englishName,
        String school,
        String gradeLevel
) {
}
