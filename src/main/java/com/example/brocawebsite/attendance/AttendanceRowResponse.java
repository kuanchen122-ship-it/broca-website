package com.example.brocawebsite.attendance;

public record AttendanceRowResponse(
        Long studentId,
        String status,
        String arrivalTime,
        String note
) {
}
