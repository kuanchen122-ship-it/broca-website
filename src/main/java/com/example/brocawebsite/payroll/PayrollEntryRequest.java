package com.example.brocawebsite.payroll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record PayrollEntryRequest(
        Long teacherId,
        LocalDate workDate,
        String classLabel,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal hours,
        BigDecimal hourlyRate,
        String note
) {
}
