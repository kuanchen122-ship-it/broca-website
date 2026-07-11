package com.example.brocawebsite.payroll;

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
@RequestMapping("/api/admin/payroll")
class PayrollController {

    private final PayrollService payrollService;

    PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping
    PayrollResponse payroll(@RequestParam(required = false) String month) {
        return payrollService.payroll(month);
    }

    @PostMapping("/entries")
    PayrollEntryRow createEntry(@RequestBody PayrollEntryRequest request, Authentication authentication) {
        return payrollService.createEntry(request, authentication.getName());
    }

    @DeleteMapping("/entries/{entryId}")
    void deleteEntry(@PathVariable Long entryId) {
        payrollService.deleteEntry(entryId);
    }
}
