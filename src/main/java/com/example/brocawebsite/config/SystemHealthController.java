package com.example.brocawebsite.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-health")
class SystemHealthController {

    private final SystemHealthService systemHealthService;

    SystemHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping
    SystemHealthResponse health() {
        return systemHealthService.health();
    }
}
