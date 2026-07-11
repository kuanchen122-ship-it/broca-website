package com.example.brocawebsite.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AttendanceRecordServiceTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttendanceRecordService attendanceRecordService;

    @Test
    void savesAndReloadsAttendanceSession() {
        jdbcTemplate.update("""
                insert into classes (code, name, category, status)
                values ('ATT_TEST', 'Attendance Test', 'ENGLISH', 'ACTIVE')
                """);
        Long classId = jdbcTemplate.queryForObject(
                "select id from classes where code = 'ATT_TEST'",
                Long.class);

        jdbcTemplate.update("""
                insert into students (student_no, chinese_name, english_name, active)
                values ('ATT001', 'Test Student', 'Amy', true)
                """);
        Long studentId = jdbcTemplate.queryForObject(
                "select id from students where student_no = 'ATT001'",
                Long.class);

        jdbcTemplate.update("""
                insert into class_enrollments (class_id, student_id, active)
                values (?, ?, true)
                """,
                classId,
                studentId);

        LocalDate sessionDate = LocalDate.of(2026, 6, 30);
        AttendanceSaveRequest request = new AttendanceSaveRequest(
                classId,
                sessionDate,
                false,
                List.of(new AttendanceStudentStatusRequest(studentId, "LATE", "16:45", "called parent")));

        AttendanceSaveResponse response = attendanceRecordService.saveSession(request, "admin");
        AttendanceSessionResponse savedSession = attendanceRecordService.loadSession(classId, sessionDate);

        assertThat(response.savedCount()).isEqualTo(1);
        assertThat(savedSession.rows()).hasSize(1);
        AttendanceRowResponse savedRow = savedSession.rows().get(0);
        assertThat(savedRow.studentId()).isEqualTo(studentId);
        assertThat(savedRow.status()).isEqualTo("LATE");
        assertThat(savedRow.arrivalTime()).startsWith("16:45");
        assertThat(savedRow.note()).isEqualTo("called parent");
    }
}
