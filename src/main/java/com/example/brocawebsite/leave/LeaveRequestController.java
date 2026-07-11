package com.example.brocawebsite.leave;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/leave-requests")
class LeaveRequestController {

    private final LeaveRequestService leaveRequestService;

    LeaveRequestController(LeaveRequestService leaveRequestService) {
        this.leaveRequestService = leaveRequestService;
    }

    @GetMapping
    LeaveRequestResponse requests(@RequestParam(required = false) String status) {
        return leaveRequestService.requests(status);
    }

    @PostMapping
    LeaveRequestRow create(@RequestBody LeaveRequestCreateRequest request) {
        return leaveRequestService.create(request);
    }

    @PostMapping("/{requestId}/review")
    LeaveRequestRow review(@PathVariable Long requestId,
                           @RequestBody LeaveRequestReviewRequest request,
                           Authentication authentication) {
        return leaveRequestService.review(requestId, request, authentication.getName());
    }
}
