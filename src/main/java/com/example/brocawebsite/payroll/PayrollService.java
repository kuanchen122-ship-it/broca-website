package com.example.brocawebsite.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PayrollService {

    private final JdbcTemplate jdbcTemplate;

    PayrollService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    PayrollResponse payroll(String rawMonth) {
        YearMonth month = parseMonth(rawMonth);
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        List<PayrollTeacherOption> teachers = listTeachers();
        List<PayrollEntryRow> entries = listEntries(start, end);
        List<PayrollSummaryRow> summaries = summarize(entries);
        BigDecimal totalHours = summaries.stream()
                .map(PayrollSummaryRow::totalHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = summaries.stream()
                .map(PayrollSummaryRow::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollResponse(month.toString(), teachers, entries, summaries, totalHours, totalAmount);
    }

    @Transactional
    PayrollEntryRow createEntry(PayrollEntryRequest request, String username) {
        if (request.teacherId() == null) {
            throw new IllegalArgumentException("請選擇老師");
        }
        if (request.workDate() == null) {
            throw new IllegalArgumentException("請選擇日期");
        }
        BigDecimal hours = resolveHours(request).setScale(2, RoundingMode.HALF_UP);
        BigDecimal hourlyRate = defaultDecimal(request.hourlyRate()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = hours.multiply(hourlyRate).setScale(2, RoundingMode.HALF_UP);
        Long createdBy = findUserId(username);

        jdbcTemplate.update("""
                        insert into payroll_entries (
                            teacher_id, work_date, class_label, start_time, end_time,
                            hours, hourly_rate, amount, note, created_by
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                request.teacherId(),
                request.workDate(),
                trimToLength(request.classLabel(), 120),
                request.startTime(),
                request.endTime(),
                hours,
                hourlyRate,
                amount,
                trimToLength(request.note(), 500),
                createdBy);

        Long id = jdbcTemplate.queryForObject("select max(id) from payroll_entries", Long.class);
        return findEntry(id);
    }

    @Transactional
    void deleteEntry(Long entryId) {
        jdbcTemplate.update("delete from payroll_entries where id = ?", entryId);
    }

    private List<PayrollTeacherOption> listTeachers() {
        return jdbcTemplate.query("""
                        select id, username, display_name
                        from users
                        where enabled = true
                          and upper(role) in ('ADMIN', 'TEACHER')
                        order by role, display_name, username
                        """,
                (rs, rowNum) -> new PayrollTeacherOption(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name")));
    }

    private List<PayrollEntryRow> listEntries(LocalDate start, LocalDate end) {
        return jdbcTemplate.query("""
                        select pe.id, pe.teacher_id, u.display_name as teacher_name,
                               pe.work_date, pe.class_label, pe.start_time, pe.end_time,
                               pe.hours, pe.hourly_rate, pe.amount, pe.note, pe.status
                        from payroll_entries pe
                        join users u on u.id = pe.teacher_id
                        where pe.work_date >= ?
                          and pe.work_date < ?
                        order by pe.work_date desc, u.display_name, pe.id desc
                        """,
                (rs, rowNum) -> new PayrollEntryRow(
                        rs.getLong("id"),
                        rs.getLong("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getObject("work_date", LocalDate.class),
                        rs.getString("class_label"),
                        rs.getObject("start_time", java.time.LocalTime.class),
                        rs.getObject("end_time", java.time.LocalTime.class),
                        rs.getBigDecimal("hours"),
                        rs.getBigDecimal("hourly_rate"),
                        rs.getBigDecimal("amount"),
                        rs.getString("note"),
                        rs.getString("status")),
                start,
                end);
    }

    private PayrollEntryRow findEntry(Long id) {
        return jdbcTemplate.queryForObject("""
                        select pe.id, pe.teacher_id, u.display_name as teacher_name,
                               pe.work_date, pe.class_label, pe.start_time, pe.end_time,
                               pe.hours, pe.hourly_rate, pe.amount, pe.note, pe.status
                        from payroll_entries pe
                        join users u on u.id = pe.teacher_id
                        where pe.id = ?
                        """,
                (rs, rowNum) -> new PayrollEntryRow(
                        rs.getLong("id"),
                        rs.getLong("teacher_id"),
                        rs.getString("teacher_name"),
                        rs.getObject("work_date", LocalDate.class),
                        rs.getString("class_label"),
                        rs.getObject("start_time", java.time.LocalTime.class),
                        rs.getObject("end_time", java.time.LocalTime.class),
                        rs.getBigDecimal("hours"),
                        rs.getBigDecimal("hourly_rate"),
                        rs.getBigDecimal("amount"),
                        rs.getString("note"),
                        rs.getString("status")),
                id);
    }

    private List<PayrollSummaryRow> summarize(List<PayrollEntryRow> entries) {
        Map<Long, PayrollSummaryRowBuilder> builders = new LinkedHashMap<>();
        for (PayrollEntryRow entry : entries) {
            PayrollSummaryRowBuilder builder = builders.computeIfAbsent(entry.teacherId(),
                    id -> new PayrollSummaryRowBuilder(entry.teacherId(), entry.teacherName()));
            builder.add(entry.hours(), entry.amount());
        }
        return builders.values().stream()
                .map(PayrollSummaryRowBuilder::build)
                .toList();
    }

    private YearMonth parseMonth(String rawMonth) {
        if (rawMonth == null || rawMonth.isBlank()) {
            return YearMonth.now();
        }
        return YearMonth.parse(rawMonth);
    }

    private BigDecimal resolveHours(PayrollEntryRequest request) {
        if (request.hours() != null && request.hours().compareTo(BigDecimal.ZERO) > 0) {
            return request.hours();
        }
        if (request.startTime() != null && request.endTime() != null && request.endTime().isAfter(request.startTime())) {
            long minutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
            return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long findUserId(String username) {
        return jdbcTemplate.queryForObject("select id from users where username = ?", Long.class, username);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private static class PayrollSummaryRowBuilder {
        private final Long teacherId;
        private final String teacherName;
        private BigDecimal totalHours = BigDecimal.ZERO;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private int entryCount;

        PayrollSummaryRowBuilder(Long teacherId, String teacherName) {
            this.teacherId = teacherId;
            this.teacherName = teacherName;
        }

        void add(BigDecimal hours, BigDecimal amount) {
            totalHours = totalHours.add(hours == null ? BigDecimal.ZERO : hours);
            totalAmount = totalAmount.add(amount == null ? BigDecimal.ZERO : amount);
            entryCount++;
        }

        PayrollSummaryRow build() {
            return new PayrollSummaryRow(teacherId, teacherName, totalHours, totalAmount, entryCount);
        }
    }
}
