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
class StudentDirectoryService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    StudentDirectoryService(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    StudentDirectoryResponse directory(String query, Long classId, String contactStatus, Boolean includeInactive) {
        List<ClassOption> classes = listClasses();
        List<StudentBase> students = listStudentBases(query, classId, contactStatus, Boolean.TRUE.equals(includeInactive));
        Map<Long, List<StudentClassMembership>> memberships = membershipsFor(students.stream()
                .map(StudentBase::id)
                .toList());

        List<StudentDirectoryRow> rows = students.stream()
                .map(student -> toRow(student, memberships.getOrDefault(student.id(), List.of())))
                .toList();

        return new StudentDirectoryResponse(summary(), classes, rows);
    }

    StudentDirectoryRow student(Long studentId) {
        StudentBase student = findStudent(studentId);
        return toRow(student, membershipsFor(List.of(student.id())).getOrDefault(student.id(), List.of()));
    }

    StudentDirectoryRow updateProfile(Long studentId, StudentProfileUpdateRequest request) {
        if (request == null || isBlank(request.chineseName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "學生中文姓名不可空白");
        }

        int updated = jdbcTemplate.update("""
                        update students
                        set chinese_name = ?, english_name = ?, school = ?, grade_level = ?,
                            parent_name = ?, parent_phone = ?, parent_line_id = ?,
                            pickup_note = ?, emergency_contact_name = ?, emergency_contact_phone = ?,
                            active = ?, updated_at = current_timestamp
                        where id = ?
                        """,
                request.chineseName().trim(),
                blankToNull(request.englishName()),
                blankToNull(request.school()),
                blankToNull(request.gradeLevel()),
                blankToNull(request.parentName()),
                blankToNull(request.parentPhone()),
                blankToNull(request.parentLineId()),
                blankToNull(request.pickupNote()),
                blankToNull(request.emergencyContactName()),
                blankToNull(request.emergencyContactPhone()),
                request.active() == null || request.active(),
                studentId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到學生資料");
        }
        return student(studentId);
    }

    StudentDirectoryRow addEnrollment(Long studentId, StudentEnrollmentUpdateRequest request) {
        if (request == null || request.classId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請選擇要加入的班級");
        }
        ensureStudentExists(studentId);
        ensureClassExists(request.classId());

        List<Long> enrollmentIds = jdbcTemplate.query("""
                        select id
                        from class_enrollments
                        where class_id = ?
                          and student_id = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                request.classId(),
                studentId);

        if (enrollmentIds.isEmpty()) {
            jdbcTemplate.update("""
                            insert into class_enrollments (class_id, student_id, active)
                            values (?, ?, true)
                            """,
                    request.classId(),
                    studentId);
        } else {
            jdbcTemplate.update("""
                            update class_enrollments
                            set active = true
                            where id = ?
                            """,
                    enrollmentIds.get(0));
        }

        return student(studentId);
    }

    StudentDirectoryRow removeEnrollment(Long studentId, Long classId) {
        ensureStudentExists(studentId);
        ensureClassExists(classId);
        jdbcTemplate.update("""
                        update class_enrollments
                        set active = false
                        where student_id = ?
                          and class_id = ?
                        """,
                studentId,
                classId);
        return student(studentId);
    }

    private StudentDirectorySummary summary() {
        return new StudentDirectorySummary(
                count("""
                        select count(*)
                        from students
                        where active = true
                        """),
                count("""
                        select count(*)
                        from classes
                        where status = 'ACTIVE'
                        """),
                count("""
                        select count(*)
                        from class_enrollments ce
                        join students s on s.id = ce.student_id
                        join classes c on c.id = ce.class_id
                        where ce.active = true
                          and s.active = true
                          and c.status = 'ACTIVE'
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and (parent_line_id is null or trim(parent_line_id) = '')
                        """),
                count("""
                        select count(*)
                        from students s
                        where s.active = true
                          and (
                              s.parent_name is null or trim(s.parent_name) = ''
                              or s.parent_phone is null or trim(s.parent_phone) = ''
                              or s.parent_line_id is null or trim(s.parent_line_id) = ''
                              or s.school is null or trim(s.school) = ''
                              or s.grade_level is null or trim(s.grade_level) = ''
                              or s.pickup_note is null or trim(s.pickup_note) = ''
                              or s.emergency_contact_name is null or trim(s.emergency_contact_name) = ''
                              or s.emergency_contact_phone is null or trim(s.emergency_contact_phone) = ''
                              or not exists (
                                  select 1
                                  from class_enrollments ce
                                  join classes c on c.id = ce.class_id
                                  where ce.student_id = s.id
                                    and ce.active = true
                                    and c.status = 'ACTIVE'
                              )
                          )
                        """),
                count("""
                        select count(*)
                        from students s
                        where s.active = true
                          and not exists (
                              select 1
                              from class_enrollments ce
                              join classes c on c.id = ce.class_id
                              where ce.student_id = s.id
                                and ce.active = true
                                and c.status = 'ACTIVE'
                          )
                        """),
                count("""
                        select count(*)
                        from students
                        where active = true
                          and import_source = 'READING_ASSIGNMENT'
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

    private List<StudentBase> listStudentBases(String query, Long classId, String contactStatus, boolean includeInactive) {
        StringBuilder sql = new StringBuilder("""
                        select distinct s.id, s.student_no, s.chinese_name, s.english_name, s.school, s.grade_level,
                               s.parent_name, s.parent_phone, s.parent_line_id,
                               s.pickup_note, s.emergency_contact_name, s.emergency_contact_phone,
                               s.import_source, s.active
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
                         )
                        """);
            String keyword = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }

        if ("missing-line".equals(contactStatus)) {
            sql.append(" and (s.parent_line_id is null or trim(s.parent_line_id) = '') ");
        } else if ("ready-line".equals(contactStatus)) {
            sql.append(" and s.parent_line_id is not null and trim(s.parent_line_id) <> '' ");
        } else if ("incomplete".equals(contactStatus)) {
            sql.append("""
                     and (
                         s.parent_name is null or trim(s.parent_name) = ''
                         or s.parent_phone is null or trim(s.parent_phone) = ''
                         or s.parent_line_id is null or trim(s.parent_line_id) = ''
                         or s.school is null or trim(s.school) = ''
                         or s.grade_level is null or trim(s.grade_level) = ''
                         or s.pickup_note is null or trim(s.pickup_note) = ''
                         or s.emergency_contact_name is null or trim(s.emergency_contact_name) = ''
                         or s.emergency_contact_phone is null or trim(s.emergency_contact_phone) = ''
                         or not exists (
                             select 1
                             from class_enrollments ce
                             join classes c on c.id = ce.class_id
                             where ce.student_id = s.id
                               and ce.active = true
                               and c.status = 'ACTIVE'
                         )
                     )
                    """);
        } else if ("unassigned".equals(contactStatus)) {
            sql.append("""
                     and not exists (
                         select 1
                         from class_enrollments ce
                         join classes c on c.id = ce.class_id
                         where ce.student_id = s.id
                           and ce.active = true
                           and c.status = 'ACTIVE'
                     )
                    """);
        }

        sql.append(" order by s.grade_level, s.chinese_name, s.english_name ");

        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new StudentBase(
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
                        rs.getString("import_source"),
                        rs.getBoolean("active")
                ),
                args.toArray());
    }

    private StudentBase findStudent(Long studentId) {
        List<StudentBase> students = jdbcTemplate.query("""
                        select id, student_no, chinese_name, english_name, school, grade_level,
                               parent_name, parent_phone, parent_line_id,
                               pickup_note, emergency_contact_name, emergency_contact_phone,
                               import_source, active
                        from students
                        where id = ?
                        """,
                (rs, rowNum) -> new StudentBase(
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
                        rs.getString("import_source"),
                        rs.getBoolean("active")
                ),
                studentId);

        if (students.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到學生資料");
        }
        return students.get(0);
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

    private StudentDirectoryRow toRow(StudentBase student, List<StudentClassMembership> classes) {
        List<String> missingFields = missingFields(student, classes);
        return new StudentDirectoryRow(
                student.id(),
                student.studentNo(),
                student.chineseName(),
                student.englishName(),
                student.school(),
                student.gradeLevel(),
                student.parentName(),
                student.parentPhone(),
                student.parentLineId(),
                student.pickupNote(),
                student.emergencyContactName(),
                student.emergencyContactPhone(),
                completionScore(missingFields),
                completionLabel(missingFields),
                missingFields,
                student.importSource(),
                student.active(),
                classes
        );
    }

    private List<String> missingFields(StudentBase student, List<StudentClassMembership> classes) {
        List<String> missing = new ArrayList<>();
        if (isBlank(student.school())) missing.add("學校");
        if (isBlank(student.gradeLevel())) missing.add("年級");
        if (isBlank(student.parentName())) missing.add("家長姓名");
        if (isBlank(student.parentPhone())) missing.add("家長電話");
        if (isBlank(student.parentLineId())) missing.add("LINE ID");
        if (isBlank(student.pickupNote())) missing.add("接送備註");
        if (isBlank(student.emergencyContactName())) missing.add("緊急聯絡人");
        if (isBlank(student.emergencyContactPhone())) missing.add("緊急電話");
        if (classes.isEmpty()) missing.add("分班");
        return List.copyOf(missing);
    }

    private int completionScore(List<String> missingFields) {
        int total = 9;
        int completed = Math.max(total - missingFields.size(), 0);
        return Math.round((completed * 100f) / total);
    }

    private String completionLabel(List<String> missingFields) {
        if (missingFields.isEmpty()) {
            return "資料完整";
        }
        return missingFields.size() <= 3 ? "可補齊" : "需整理";
    }

    private void ensureStudentExists(Long studentId) {
        if (count("select count(*) from students where id = ?", studentId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到學生資料");
        }
    }

    private void ensureClassExists(Long classId) {
        if (classId == null || count("select count(*) from classes where id = ? and status = 'ACTIVE'", classId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到班級資料");
        }
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record StudentBase(
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
            String importSource,
            boolean active
    ) {
    }
}
