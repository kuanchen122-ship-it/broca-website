package com.example.brocawebsite.registration;

import java.util.List;

public record RegistrationRequestResponse(
        RegistrationRequestSummary summary,
        List<RegistrationRequestRow> requests
) {
}
