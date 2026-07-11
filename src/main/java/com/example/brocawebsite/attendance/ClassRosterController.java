package com.example.brocawebsite.attendance;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/classes")
class ClassRosterController {

    private final ClassRosterService rosterService;

    ClassRosterController(ClassRosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping
    List<ClassOption> listClasses() {
        return rosterService.listClasses();
    }

    @GetMapping("/{classId}/roster")
    ClassRosterResponse roster(@PathVariable Long classId) {
        return rosterService.roster(classId);
    }
}
