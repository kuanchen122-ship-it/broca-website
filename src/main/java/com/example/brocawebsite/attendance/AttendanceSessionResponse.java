package com.example.brocawebsite.attendance;

import java.time.LocalDate;
import java.util.List;

public record AttendanceSessionResponse(
        Long classId,
        LocalDate sessionDate,
        List<AttendanceRowResponse> rows
) {
}
