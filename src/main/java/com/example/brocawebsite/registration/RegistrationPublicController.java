package com.example.brocawebsite.registration;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/registration-requests")
class RegistrationPublicController {

    private final RegistrationRequestService registrationRequestService;

    RegistrationPublicController(RegistrationRequestService registrationRequestService) {
        this.registrationRequestService = registrationRequestService;
    }

    @PostMapping
    RegistrationRequestRow create(@RequestBody RegistrationRequestCreateRequest request) {
        return registrationRequestService.create(request);
    }
}
