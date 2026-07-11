package com.example.brocawebsite.leave;

import java.util.List;

public record LeaveRequestResponse(
        LeaveRequestSummary summary,
        List<LeaveRequestRow> requests
) {
}
