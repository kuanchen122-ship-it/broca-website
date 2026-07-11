package com.example.brocawebsite.leave;

public record LeaveRequestSummary(
        int pendingToday,
        int approvedThisWeek,
        int totalThisWeek,
        int pendingTotal,
        int approvedTotal,
        int rejectedTotal
) {
}
