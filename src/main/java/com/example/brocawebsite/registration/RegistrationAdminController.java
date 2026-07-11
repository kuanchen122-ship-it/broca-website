package com.example.brocawebsite.registration;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/registration-requests")
class RegistrationAdminController {

    private final RegistrationRequestService registrationRequestService;

    RegistrationAdminController(RegistrationRequestService registrationRequestService) {
        this.registrationRequestService = registrationRequestService;
    }

    @GetMapping
    RegistrationRequestResponse requests(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String q) {
        return registrationRequestService.requests(status, q);
    }

    @PostMapping("/{requestId}/status")
    RegistrationRequestRow updateStatus(@PathVariable Long requestId,
                                        @RequestBody RegistrationRequestStatusUpdateRequest request,
                                        Authentication authentication) {
        return registrationRequestService.updateStatus(requestId, request, authentication.getName());
    }

    @PostMapping("/{requestId}/enroll")
    RegistrationEnrollmentResult enroll(@PathVariable Long requestId,
                                        @RequestBody RegistrationEnrollmentRequest request,
                                        Authentication authentication) {
        return registrationRequestService.enroll(requestId, request, authentication.getName());
    }

    @DeleteMapping("/{requestId}")
    RegistrationDeleteResponse deleteRequest(@PathVariable Long requestId) {
        return registrationRequestService.deleteRequest(requestId);
    }
}
