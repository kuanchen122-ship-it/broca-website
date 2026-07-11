package com.example.brocawebsite.line;

import java.util.List;

public record LineIntegrationOverview(
        LineConfigStatus config,
        LineAudienceSummary audience,
        LineAttendanceQueueSummary attendanceQueue,
        List<LineWorkflowStep> workflow,
        List<LineMessageTemplate> templates
) {
}
