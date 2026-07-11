package com.example.brocawebsite.attendance;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AttendanceRecordService {

    private static final Set<String> VALID_STATUSES = Set.of("PRESENT", "LATE", "LEAVE", "ABSENT");

    private final JdbcTemplate jdbcTemplate;
    private final boolean lineSendingEnabled;
    private final String lineChannelAccessToken;
    private final String lineChannelSecret;

    AttendanceRecordService(
            JdbcTemplate jdbcTemplate,
            @Value("${line.messaging.sending-enabled:false}") boolean lineSendingEnabled,
            @Value("${line.messaging.channel-access-token:}") String lineChannelAccessToken,
            @Value("${line.messaging.channel-secret:}") String lineChannelSecret
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.lineSendingEnabled = lineSendingEnabled;
        this.lineChannelAccessToken = lineChannelAccessToken;
        this.lineChannelSecret = lineChannelSecret;
    }

    AttendanceSessionResponse loadSession(Long classId, LocalDate sessionDate) {
        List<AttendanceRowResponse> rows = jdbcTemplate.query("""
                        select student_id, status, arrival_time, note
                        from attendance
                        where class_id = ?
                          and session_date = ?
                        union all
                        select lr.student_id,
                               'LEAVE' as status,
                               null as arrival_time,
                               concat('已核准請假：', coalesce(lr.reason_text, lr.reason_type)) as note
                        from leave_requests lr
                        where lr.class_id = ?
                          and lr.leave_date = ?
                          and lr.status = 'APPROVED'
                          and not exists (
                              select 1
                              from attendance a
                              where a.class_id = lr.class_id
                                and a.student_id = lr.student_id
                                and a.session_date = lr.leave_date
                          )
                        order by student_id
                        """,
                (rs, rowNum) -> new AttendanceRowResponse(
                        rs.getLong("student_id"),
                        rs.getString("status"),
                        rs.getString("arrival_time"),
                        rs.getString("note")
                ),
                classId,
                sessionDate,
                classId,
                sessionDate);

        return new AttendanceSessionResponse(classId, sessionDate, rows);
    }

    @Transactional
    AttendanceSaveResponse saveSession(AttendanceSaveRequest request, String username) {
        if (request.classId() == null) {
            throw new IllegalArgumentException("缺少班級 ID");
        }
        if (request.sessionDate() == null) {
            throw new IllegalArgumentException("缺少點名日期");
        }
        if (request.students() == null || request.students().isEmpty()) {
            throw new IllegalArgumentException("沒有可儲存的點名資料");
        }

        Long recordedBy = findUserId(username);
        int savedCount = 0;
        String lineStatus = resolveLineStatus(request.lineNotificationRequested());

        for (AttendanceStudentStatusRequest row : request.students()) {
            if (row.studentId() == null) {
                continue;
            }
            String status = normalizeStatus(row.status());
            LocalTime arrivalTime = "LATE".equals(status) ? parseArrivalTime(row.arrivalTime()) : null;
            String note = trimToLength(row.note(), 500);

            List<Long> ids = jdbcTemplate.query("""
                            select id
                            from attendance
                            where class_id = ?
                              and student_id = ?
                              and session_date = ?
                            """,
                    (rs, rowNum) -> rs.getLong("id"),
                    request.classId(),
                    row.studentId(),
                    request.sessionDate());

            if (ids.isEmpty()) {
                jdbcTemplate.update("""
                                insert into attendance (
                                    class_id, student_id, session_date, status,
                                    arrival_time, note, recorded_by, line_notification_status
                                )
                                values (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        request.classId(),
                        row.studentId(),
                        request.sessionDate(),
                        status,
                        arrivalTime,
                        note,
                        recordedBy,
                        lineStatus);
            } else {
                jdbcTemplate.update("""
                                update attendance
                                set status = ?,
                                    arrival_time = ?,
                                    note = ?,
                                    recorded_by = ?,
                                    recorded_at = current_timestamp,
                                    line_notification_status = ?
                                where id = ?
                                """,
                        status,
                        arrivalTime,
                        note,
                        recordedBy,
                        lineStatus,
                        ids.get(0));
            }
            savedCount++;
        }

        return new AttendanceSaveResponse(
                request.classId(),
                request.sessionDate(),
                savedCount,
                lineStatus,
                "點名已儲存；LINE Messaging API 尚未串接，所以目前不會真的發送家長通知。");
    }

    private Long findUserId(String username) {
        List<Long> ids = jdbcTemplate.query("""
                        select id
                        from users
                        where username = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                username);

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("找不到登入使用者");
        }
        return ids.get(0);
    }

    private String resolveLineStatus(Boolean requested) {
        if (!Boolean.TRUE.equals(requested)) {
            return "NOT_REQUESTED";
        }
        if (lineSendingEnabled && hasText(lineChannelAccessToken) && hasText(lineChannelSecret)) {
            return "PENDING";
        }
        return "NOT_CONFIGURED";
    }

    private String normalizeStatus(String rawStatus) {
        String status = rawStatus == null ? "PRESENT" : rawStatus.trim().toUpperCase(Locale.ROOT);
        status = switch (status) {
            case "準時", "實到" -> "PRESENT";
            case "遲到" -> "LATE";
            case "請假", "事/病假" -> "LEAVE";
            case "曠課", "缺席" -> "ABSENT";
            default -> status;
        };
        if (!VALID_STATUSES.contains(status)) {
            return "PRESENT";
        }
        return status;
    }

    private LocalTime parseArrivalTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(rawTime.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
