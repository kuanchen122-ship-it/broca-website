package com.example.brocawebsite.config;

import java.math.BigDecimal;

public record SystemHealthSummary(
        int activeStudents,
        int activeClasses,
        int activeEnrollments,
        int enabledUsers,
        int attendanceRecords,
        int pendingLineNotifications,
        BigDecimal currentMonthPayroll
) {
}
