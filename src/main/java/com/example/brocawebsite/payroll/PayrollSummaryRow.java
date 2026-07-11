package com.example.brocawebsite.payroll;

import java.math.BigDecimal;

public record PayrollSummaryRow(
        Long teacherId,
        String teacherName,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        int entryCount
) {
}
