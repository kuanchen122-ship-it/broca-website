package com.example.brocawebsite.payroll;

import java.math.BigDecimal;
import java.util.List;

public record PayrollResponse(
        String month,
        List<PayrollTeacherOption> teachers,
        List<PayrollEntryRow> entries,
        List<PayrollSummaryRow> summaries,
        BigDecimal totalHours,
        BigDecimal totalAmount
) {
}
