package com.example.brocawebsite.student;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.example.brocawebsite.attendance.ClassOption;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ParentContactService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    ParentContactService(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    ParentContactResponse contacts(String query, Long classId, String contactStatus, Boolean includeInactive) {
        List<ClassOption> classes = listClasses();
        List<ContactBase> contacts = listContactBases(query, classId, contactStatus, Boolean.TRUE.equals(includeInactive));
        Map<Long, List<StudentClassMembership>> memberships = membershipsFor(contacts.stream()
                .map(ContactBase::id)
                .toList());

        List<ParentContactRow> rows = contacts.stream()
                .map(contact -> toRow(contact, memberships.getOrDefault(contact.id(), List.of())))
                .toList();

        return new ParentContactResponse(summary(), classes, rows);
    }

    ParentContactRow updateContact(Long studentId, ParentContactUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請提供要更新的家長資料。");
        }

        int updated = jdbcTemplate.update("""
                        update students
                        set parent_name = ?,
                            parent_phone = ?,
                            parent_line_id = ?,
                            pickup_note = ?,
                            emergency_contact_name = ?,
                            emergency_contact_phone = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                blankToNull(request.parentName()),
                normalizePhoneOrNull(request.parentPhone()),
                blankToNull(request.parentLineId()),
                blankToNull(request.pickupNote()),
                blankToNull(request.emergencyContactName()),
                normalizePhoneOrNull(request.emergencyContactPhone()),
                studentId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這位學生。");
        }

        ContactBase contact = findContact(studentId);
        return toRow(contact, membershipsFor(List.of(studentId)).getOrDefault(studentId, List.of()));
    }

    private ParentContactSummary summary() {
        return new ParentContactSummary(
                count("""
                        select count(*)
                        from students
                        where active = true
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and parent_name is not null and trim(parent_name) <> ''
                          and parent_phone is not null and trim(parent_phone) <> ''
                          and parent_line_id is not null and trim(parent_line_id) <> ''
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and (parent_name is null or trim(parent_name) = '')
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and (parent_phone is null or trim(parent_phone) = '')
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and (parent_line_id is null or trim(parent_line_id) = '')
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and parent_name is not null and trim(parent_name) <> ''
                          and parent_phone is not null and trim(parent_phone) <> ''
                          and (parent_line_id is null or trim(parent_line_id) = '')
                        """)
        );
    }

    private List<ClassOption> listClasses() {
        return jdbcTemplate.query("""
                        select c.id, c.code, c.name, c.category, count(ce.student_id) as active_student_count
                        from classes c
                        left join class_enrollments ce
                          on ce.class_id = c.id
                         and ce.active = true
                        where c.status = 'ACTIVE'
                        group by c.id, c.code, c.name, c.category
                        order by c.code
                        """,
                (rs, rowNum) -> new ClassOption(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getInt("active_student_count")
                ));
    }

    private List<ContactBase> listContactBases(String query, Long classId, String contactStatus, boolean includeInactive) {
        StringBuilder sql = new StringBuilder("""
                        select distinct s.id, s.student_no, s.chinese_name, s.english_name, s.school, s.grade_level,
                               s.parent_name, s.parent_phone, s.parent_line_id,
                               s.pickup_note, s.emergency_contact_name, s.emergency_contact_phone,
                               s.active
                        from students s
                        """);
        List<Object> args = new ArrayList<>();

        if (classId != null) {
            sql.append("""
                            join class_enrollments filter_ce
                              on filter_ce.student_id = s.id
                             and filter_ce.active = true
                             and filter_ce.class_id = ?
                            """);
            args.add(classId);
        }

        sql.append(" where 1 = 1 ");
        if (!includeInactive) {
            sql.append(" and s.active = true ");
        }

        if (!isBlank(query)) {
            sql.append("""
                         and (
                             lower(coalesce(s.chinese_name, '')) like ?
                          or lower(coalesce(s.english_name, '')) like ?
                          or lower(coalesce(s.student_no, '')) like ?
                          or lower(coalesce(s.school, '')) like ?
                          or lower(coalesce(s.parent_name, '')) like ?
                          or lower(coalesce(s.parent_phone, '')) like ?
                          or lower(coalesce(s.parent_line_id, '')) like ?
                          or lower(coalesce(s.pickup_note, '')) like ?
                          or lower(coalesce(s.emergency_contact_name, '')) like ?
                          or lower(coalesce(s.emergency_contact_phone, '')) like ?
                         )
                        """);
            String keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            for (int index = 0; index < 10; index++) {
                args.add(keyword);
            }
        }

        appendContactStatusFilter(sql, contactStatus);
        sql.append(" order by s.grade_level, s.chinese_name, s.english_name ");

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new ContactBase(
                        rs.getLong("id"),
                        rs.getString("student_no"),
                        rs.getString("chinese_name"),
                        rs.getString("english_name"),
                        rs.getString("school"),
                        rs.getString("grade_level"),
                        rs.getString("parent_name"),
                        rs.getString("parent_phone"),
                        rs.getString("parent_line_id"),
                        rs.getString("pickup_note"),
                        rs.getString("emergency_contact_name"),
                        rs.getString("emergency_contact_phone"),
                        rs.getBoolean("active")
                ),
                args.toArray());
    }

    private void appendContactStatusFilter(StringBuilder sql, String contactStatus) {
        if ("incomplete".equals(contactStatus)) {
            sql.append("""
                     and (
                         parent_name is null or trim(parent_name) = ''
                      or parent_phone is null or trim(parent_phone) = ''
                      or parent_line_id is null or trim(parent_line_id) = ''
                     )
                    """);
        } else if ("missing-parent".equals(contactStatus)) {
            sql.append(" and (parent_name is null or trim(parent_name) = '') ");
        } else if ("missing-phone".equals(contactStatus)) {
            sql.append(" and (parent_phone is null or trim(parent_phone) = '') ");
        } else if ("missing-line".equals(contactStatus)) {
            sql.append(" and (parent_line_id is null or trim(parent_line_id) = '') ");
        } else if ("ready-invite".equals(contactStatus)) {
            sql.append("""
                     and parent_name is not null and trim(parent_name) <> ''
                     and parent_phone is not null and trim(parent_phone) <> ''
                     and (parent_line_id is null or trim(parent_line_id) = '')
                    """);
        } else if ("complete".equals(contactStatus)) {
            sql.append("""
                     and parent_name is not null and trim(parent_name) <> ''
                     and parent_phone is not null and trim(parent_phone) <> ''
                     and parent_line_id is not null and trim(parent_line_id) <> ''
                    """);
        }
    }

    private ContactBase findContact(Long studentId) {
        List<ContactBase> contacts = jdbcTemplate.query("""
                        select id, student_no, chinese_name, english_name, school, grade_level,
                               parent_name, parent_phone, parent_line_id,
                               pickup_note, emergency_contact_name, emergency_contact_phone,
                               active
                        from students
                        where id = ?
                        """,
                (rs, rowNum) -> new ContactBase(
                        rs.getLong("id"),
                        rs.getString("student_no"),
                        rs.getString("chinese_name"),
                        rs.getString("english_name"),
                        rs.getString("school"),
                        rs.getString("grade_level"),
                        rs.getString("parent_name"),
                        rs.getString("parent_phone"),
                        rs.getString("parent_line_id"),
                        rs.getString("pickup_note"),
                        rs.getString("emergency_contact_name"),
                        rs.getString("emergency_contact_phone"),
                        rs.getBoolean("active")
                ),
                studentId);

        if (contacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這位學生。");
        }
        return contacts.get(0);
    }

    private Map<Long, List<StudentClassMembership>> membershipsFor(List<Long> studentIds) {
        Map<Long, List<StudentClassMembership>> result = new LinkedHashMap<>();
        if (studentIds.isEmpty()) {
            return result;
        }

        MapSqlParameterSource params = new MapSqlParameterSource("studentIds", studentIds);
        namedParameterJdbcTemplate.query("""
                        select ce.student_id, c.id, c.code, c.name, c.category,
                               count(active_ce.student_id) as active_student_count
                        from class_enrollments ce
                        join classes c on c.id = ce.class_id
                        left join class_enrollments active_ce
                          on active_ce.class_id = c.id
                         and active_ce.active = true
                        where ce.active = true
                          and c.status = 'ACTIVE'
                          and ce.student_id in (:studentIds)
                        group by ce.student_id, c.id, c.code, c.name, c.category
                        order by c.code
                        """,
                params,
                rs -> {
                    Long studentId = rs.getLong("student_id");
                    result.computeIfAbsent(studentId, ignored -> new ArrayList<>())
                            .add(new StudentClassMembership(
                                    rs.getLong("id"),
                                    rs.getString("code"),
                                    rs.getString("name"),
                                    rs.getString("category"),
                                    rs.getInt("active_student_count")
                            ));
                });

        return result;
    }

    private ParentContactRow toRow(ContactBase contact, List<StudentClassMembership> classes) {
        return new ParentContactRow(
                contact.id(),
                contact.studentNo(),
                contact.chineseName(),
                contact.englishName(),
                contact.school(),
                contact.gradeLevel(),
                contact.parentName(),
                contact.parentPhone(),
                contact.parentLineId(),
                contact.pickupNote(),
                contact.emergencyContactName(),
                contact.emergencyContactPhone(),
                contact.active(),
                completionScore(contact),
                contactStatus(contact),
                nextAction(contact),
                classes
        );
    }

    private int completionScore(ContactBase contact) {
        int score = 0;
        if (!isBlank(contact.parentName())) score++;
        if (!isBlank(contact.parentPhone())) score++;
        if (!isBlank(contact.parentLineId())) score++;
        return score;
    }

    private String contactStatus(ContactBase contact) {
        int score = completionScore(contact);
        if (score == 3) return "COMPLETE";
        if (isBlank(contact.parentName())) return "MISSING_PARENT";
        if (isBlank(contact.parentPhone())) return "MISSING_PHONE";
        if (isBlank(contact.parentLineId()) && !isBlank(contact.parentPhone())) return "READY_FOR_LINE_INVITE";
        return "MISSING_LINE";
    }

    private String nextAction(ContactBase contact) {
        if (completionScore(contact) == 3) return "資料完整";
        if (isBlank(contact.parentName())) return "補家長姓名";
        if (isBlank(contact.parentPhone())) return "補家長電話";
        if (isBlank(contact.parentLineId())) return "可邀請家長綁定 LINE";
        return "待確認";
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String normalizePhoneOrNull(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", "");
    }

    private record ContactBase(
            Long id,
            String studentNo,
            String chineseName,
            String englishName,
            String school,
            String gradeLevel,
            String parentName,
            String parentPhone,
            String parentLineId,
            String pickupNote,
            String emergencyContactName,
            String emergencyContactPhone,
            boolean active
    ) {
    }
}
