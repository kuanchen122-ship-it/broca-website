package com.example.brocawebsite.line;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/line")
class LineIntegrationController {

    private final LineIntegrationService lineIntegrationService;

    LineIntegrationController(LineIntegrationService lineIntegrationService) {
        this.lineIntegrationService = lineIntegrationService;
    }

    @GetMapping("/overview")
    LineIntegrationOverview overview() {
        return lineIntegrationService.overview();
    }
}
