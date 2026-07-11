package com.example.brocawebsite.student;

public record ParentContactUpdateRequest(
        String parentName,
        String parentPhone,
        String parentLineId,
        String pickupNote,
        String emergencyContactName,
        String emergencyContactPhone
) {
}
