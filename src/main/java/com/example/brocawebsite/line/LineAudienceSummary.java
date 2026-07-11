package com.example.brocawebsite.line;

public record LineAudienceSummary(
        int activeStudents,
        int linkedStudents,
        int missingLineStudents,
        int readyForInviteStudents,
        int distinctParentLineIds,
        int sharedParentLineIds,
        int coveragePercent
) {
}
