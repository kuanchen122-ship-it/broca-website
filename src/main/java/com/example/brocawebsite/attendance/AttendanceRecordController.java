package com.example.brocawebsite.attendance;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/attendance")
class AttendanceRecordController {

    private final AttendanceRecordService attendanceRecordService;

    AttendanceRecordController(AttendanceRecordService attendanceRecordService) {
        this.attendanceRecordService = attendanceRecordService;
    }

    @GetMapping("/sessions")
    AttendanceSessionResponse loadSession(
            @RequestParam Long classId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sessionDate
    ) {
        return attendanceRecordService.loadSession(classId, sessionDate);
    }

    @PostMapping("/sessions")
    AttendanceSaveResponse saveSession(@RequestBody AttendanceSaveRequest request, Authentication authentication) {
        return attendanceRecordService.saveSession(request, authentication.getName());
    }
}
