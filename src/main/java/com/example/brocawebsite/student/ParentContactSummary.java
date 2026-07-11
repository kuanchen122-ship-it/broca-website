package com.example.brocawebsite.student;

public record ParentContactSummary(
        int activeStudents,
        int completedContacts,
        int missingParentNameCount,
        int missingPhoneCount,
        int missingLineCount,
        int readyForLineInvite
) {
}
