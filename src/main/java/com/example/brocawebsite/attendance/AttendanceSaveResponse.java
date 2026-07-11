package com.example.brocawebsite.attendance;

import java.time.LocalDate;

public record AttendanceSaveResponse(
        Long classId,
        LocalDate sessionDate,
        int savedCount,
        String lineNotificationStatus,
        String message
) {
}
