package com.example.brocawebsite.student;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/students")
class StudentDirectoryController {

    private final StudentDirectoryService studentDirectoryService;

    StudentDirectoryController(StudentDirectoryService studentDirectoryService) {
        this.studentDirectoryService = studentDirectoryService;
    }

    @GetMapping
    StudentDirectoryResponse directory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String contactStatus,
            @RequestParam(required = false) Boolean includeInactive
    ) {
        return studentDirectoryService.directory(q, classId, contactStatus, includeInactive);
    }

    @GetMapping("/{studentId}")
    StudentDirectoryRow student(@PathVariable Long studentId) {
        return studentDirectoryService.student(studentId);
    }

    @RequestMapping(value = "/{studentId}/profile", method = {RequestMethod.POST, RequestMethod.PUT})
    StudentDirectoryRow updateProfile(@PathVariable Long studentId,
                                      @RequestBody StudentProfileUpdateRequest request) {
        return studentDirectoryService.updateProfile(studentId, request);
    }

    @PostMapping("/{studentId}/enrollments")
    StudentDirectoryRow addEnrollment(@PathVariable Long studentId,
                                      @RequestBody StudentEnrollmentUpdateRequest request) {
        return studentDirectoryService.addEnrollment(studentId, request);
    }

    @PostMapping("/{studentId}/enrollments/{classId}/remove")
    StudentDirectoryRow removeEnrollment(@PathVariable Long studentId, @PathVariable Long classId) {
        return studentDirectoryService.removeEnrollment(studentId, classId);
    }
}
