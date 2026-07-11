package com.example.brocawebsite.attendance;

import java.time.LocalDate;
import java.util.List;

public record AttendanceSaveRequest(
        Long classId,
        LocalDate sessionDate,
        Boolean lineNotificationRequested,
        List<AttendanceStudentStatusRequest> students
) {
}
