package com.example.brocawebsite.leave;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LeaveRequestService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final Set<String> VALID_REASON_TYPES = Set.of("SICK", "PERSONAL", "FAMILY", "SCHOOL", "OTHER");

    private final JdbcTemplate jdbcTemplate;

    LeaveRequestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    LeaveRequestResponse requests(String status) {
        String normalizedStatus = normalizeStatusFilter(status);
        List<LeaveRequestRow> rows = jdbcTemplate.query("""
                        select lr.id, lr.student_id, s.student_no, s.chinese_name, s.english_name,
                               lr.class_id, c.code as class_code, c.name as class_name,
                               lr.leave_date, lr.reason_type, lr.reason_text, lr.status,
                               lr.source, reviewer.display_name as reviewed_by,
                               lr.reviewed_at, lr.review_note, lr.created_at
                        from leave_requests lr
                        join students s on s.id = lr.student_id
                        left join classes c on c.id = lr.class_id
                        left join users reviewer on reviewer.id = lr.reviewed_by
                        where (? is null or lr.status = ?)
                        order by
                            case lr.status
                                when 'PENDING' then 0
                                when 'APPROVED' then 1
                                else 2
                            end,
                            lr.leave_date desc,
                            lr.created_at desc,
                            lr.id desc
                        """,
                (rs, rowNum) -> row(rs),
                normalizedStatus,
                normalizedStatus);

        return new LeaveRequestResponse(summary(), rows);
    }

    @Transactional
    LeaveRequestRow create(LeaveRequestCreateRequest request) {
        if (request.studentId() == null) {
            throw new IllegalArgumentException("請先選擇學生");
        }
        if (request.classId() == null) {
            throw new IllegalArgumentException("請先選擇請假的班級");
        }
        if (request.leaveDate() == null) {
            throw new IllegalArgumentException("請先選擇請假日期");
        }

        String reasonType = normalizeReasonType(request.reasonType());
        String reasonText = trimToLength(request.reasonText(), 500);

        ensureStudentAndClassExist(request.studentId(), request.classId());
        ensureNoOpenDuplicate(request.studentId(), request.classId(), request.leaveDate());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                            insert into leave_requests (
                                student_id, class_id, leave_date, reason_type, reason_text,
                                status, source, created_at, updated_at
                            )
                            values (?, ?, ?, ?, ?, 'PENDING', 'MANUAL', current_timestamp, current_timestamp)
                            """,
                    new String[]{"id"});
            statement.setLong(1, request.studentId());
            statement.setLong(2, request.classId());
            statement.setObject(3, request.leaveDate());
            statement.setString(4, reasonType);
            statement.setString(5, reasonText);
            return statement;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new IllegalArgumentException("請假單建立失敗，未取得資料庫編號");
        }
        Long id = key.longValue();
        return rowById(id);
    }

    @Transactional
    LeaveRequestRow review(Long requestId, LeaveRequestReviewRequest request, String username) {
        String status = normalizeReviewStatus(request.status());
        Long reviewerId = findUserId(username);
        String reviewNote = trimToLength(request.reviewNote(), 500);

        jdbcTemplate.update("""
                        update leave_requests
                        set status = ?,
                            reviewed_by = ?,
                            reviewed_at = current_timestamp,
                            review_note = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                status,
                reviewerId,
                reviewNote,
                requestId);

        LeaveRequestRow row = rowById(requestId);
        if ("APPROVED".equals(status)) {
            syncApprovedLeaveToAttendance(row, reviewerId);
        } else {
            removeGeneratedAttendance(row);
        }
        return rowById(requestId);
    }

    private LeaveRequestSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(7);

        return new LeaveRequestSummary(
                count("""
                        select count(*)
                        from leave_requests
                        where status = 'PENDING'
                          and leave_date = ?
                        """, today),
                count("""
                        select count(*)
                        from leave_requests
                        where status = 'APPROVED'
                          and reviewed_at >= ?
                          and reviewed_at < ?
                        """, weekStart.atStartOfDay(), weekEnd.atStartOfDay()),
                count("""
                        select count(*)
                        from leave_requests
                        where created_at >= ?
                          and created_at < ?
                        """, weekStart.atStartOfDay(), weekEnd.atStartOfDay()),
                count("select count(*) from leave_requests where status = 'PENDING'"),
                count("select count(*) from leave_requests where status = 'APPROVED'"),
                count("select count(*) from leave_requests where status = 'REJECTED'")
        );
    }

    private void syncApprovedLeaveToAttendance(LeaveRequestRow row, Long reviewerId) {
        if (row.classId() == null || row.studentId() == null || row.leaveDate() == null) {
            return;
        }

        LocalDate leaveDate = LocalDate.parse(row.leaveDate());
        String note = trimToLength("請假單 #%d：%s".formatted(row.id(), blankToDash(row.reasonText())), 500);
        List<Long> existingIds = jdbcTemplate.query("""
                        select id
                        from attendance
                        where class_id = ?
                          and student_id = ?
                          and session_date = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                row.classId(),
                row.studentId(),
                leaveDate);

        if (existingIds.isEmpty()) {
            jdbcTemplate.update("""
                            insert into attendance (
                                class_id, student_id, session_date, status,
                                arrival_time, note, recorded_by, line_notification_status
                            )
                            values (?, ?, ?, 'LEAVE', null, ?, ?, 'NOT_REQUESTED')
                            """,
                    row.classId(),
                    row.studentId(),
                    leaveDate,
                    note,
                    reviewerId);
            return;
        }

        jdbcTemplate.update("""
                        update attendance
                        set status = 'LEAVE',
                            arrival_time = null,
                            note = ?,
                            recorded_by = ?,
                            recorded_at = current_timestamp,
                            line_notification_status = 'NOT_REQUESTED'
                        where id = ?
                        """,
                note,
                reviewerId,
                existingIds.get(0));
    }

    private void removeGeneratedAttendance(LeaveRequestRow row) {
        if (row.classId() == null || row.studentId() == null || row.leaveDate() == null) {
            return;
        }

        jdbcTemplate.update("""
                        delete from attendance
                        where class_id = ?
                          and student_id = ?
                          and session_date = ?
                          and status = 'LEAVE'
                          and note like ?
                        """,
                row.classId(),
                row.studentId(),
                LocalDate.parse(row.leaveDate()),
                "請假單 #" + row.id() + ":%");
    }

    private void ensureStudentAndClassExist(Long studentId, Long classId) {
        int count = count("""
                        select count(*)
                        from class_enrollments ce
                        join students s on s.id = ce.student_id
                        join classes c on c.id = ce.class_id
                        where ce.student_id = ?
                          and ce.class_id = ?
                          and ce.active = true
                          and s.active = true
                          and c.status = 'ACTIVE'
                        """,
                studentId,
                classId);
        if (count == 0) {
            throw new IllegalArgumentException("這位學生目前不在所選班級的啟用名單中");
        }
    }

    private void ensureNoOpenDuplicate(Long studentId, Long classId, LocalDate leaveDate) {
        int count = count("""
                        select count(*)
                        from leave_requests
                        where student_id = ?
                          and class_id = ?
                          and leave_date = ?
                          and status in ('PENDING', 'APPROVED')
                        """,
                studentId,
                classId,
                leaveDate);
        if (count > 0) {
            throw new IllegalArgumentException("同一學生、班級與日期已有待審或已核准假單");
        }
    }

    private LeaveRequestRow rowById(Long id) {
        List<LeaveRequestRow> rows = jdbcTemplate.query("""
                        select lr.id, lr.student_id, s.student_no, s.chinese_name, s.english_name,
                               lr.class_id, c.code as class_code, c.name as class_name,
                               lr.leave_date, lr.reason_type, lr.reason_text, lr.status,
                               lr.source, reviewer.display_name as reviewed_by,
                               lr.reviewed_at, lr.review_note, lr.created_at
                        from leave_requests lr
                        join students s on s.id = lr.student_id
                        left join classes c on c.id = lr.class_id
                        left join users reviewer on reviewer.id = lr.reviewed_by
                        where lr.id = ?
                        """,
                (rs, rowNum) -> row(rs),
                id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("找不到請假單");
        }
        return rows.get(0);
    }

    private LeaveRequestRow row(ResultSet rs) throws SQLException {
        String status = rs.getString("status");
        String reasonType = rs.getString("reason_type");
        return new LeaveRequestRow(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("student_no"),
                rs.getString("chinese_name"),
                rs.getString("english_name"),
                nullableLong(rs, "class_id"),
                rs.getString("class_code"),
                rs.getString("class_name"),
                String.valueOf(rs.getDate("leave_date").toLocalDate()),
                reasonType,
                reasonLabel(reasonType),
                rs.getString("reason_text"),
                status,
                statusLabel(status),
                rs.getString("source"),
                rs.getString("reviewed_by"),
                formatTimestamp(rs.getTimestamp("reviewed_at")),
                rs.getString("review_note"),
                formatTimestamp(rs.getTimestamp("created_at"))
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Number generatedId(KeyHolder keyHolder) {
        try {
            return keyHolder.getKey();
        } catch (RuntimeException ignored) {
            if (keyHolder.getKeyList().isEmpty()) {
                return null;
            }
            Object value = keyHolder.getKeyList().get(0).get("ID");
            if (value == null) {
                value = keyHolder.getKeyList().get(0).get("id");
            }
            return value instanceof Number number ? number : null;
        }
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

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING", "APPROVED", "REJECTED" -> normalized;
            default -> null;
        };
    }

    private String normalizeReviewStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("APPROVED".equals(normalized) || "REJECTED".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("審核狀態只能是核准或退回");
    }

    private String normalizeReasonType(String reasonType) {
        String normalized = reasonType == null ? "OTHER" : reasonType.trim().toUpperCase(Locale.ROOT);
        return VALID_REASON_TYPES.contains(normalized) ? normalized : "OTHER";
    }

    private String reasonLabel(String reasonType) {
        return switch (String.valueOf(reasonType)) {
            case "SICK" -> "病假";
            case "PERSONAL" -> "事假";
            case "FAMILY" -> "家庭因素";
            case "SCHOOL" -> "學校活動";
            default -> "其他";
        };
    }

    private String statusLabel(String status) {
        return switch (String.valueOf(status)) {
            case "PENDING" -> "待審核";
            case "APPROVED" -> "已核准";
            case "REJECTED" -> "已退回";
            default -> "未知";
        };
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
