package com.example.brocawebsite.attendance;

public record AttendanceStudentStatusRequest(
        Long studentId,
        String status,
        String arrivalTime,
        String note
) {
}
