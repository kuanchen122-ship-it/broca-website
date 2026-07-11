package com.example.brocawebsite.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PayrollEntryRow(
        Long id,
        Long teacherId,
        String teacherName,
        LocalDate workDate,
        String classLabel,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal amount,
        String note,
        String status
) {
}
