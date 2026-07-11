package com.example.brocawebsite.leave;

import java.time.LocalDate;

public record LeaveRequestCreateRequest(
        Long studentId,
        Long classId,
        LocalDate leaveDate,
        String reasonType,
        String reasonText
) {
}
