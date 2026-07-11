package com.example.brocawebsite.attendance;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class ClassRosterService {

    private final JdbcTemplate jdbcTemplate;

    ClassRosterService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ClassOption> listClasses() {
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

    ClassRosterResponse roster(Long classId) {
        List<ClassOption> classes = jdbcTemplate.query("""
                        select c.id, c.code, c.name, c.category, count(ce.student_id) as active_student_count
                        from classes c
                        left join class_enrollments ce
                          on ce.class_id = c.id
                         and ce.active = true
                        where c.id = ?
                        group by c.id, c.code, c.name, c.category
                        """,
                (rs, rowNum) -> new ClassOption(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getInt("active_student_count")
                ),
                classId);

        if (classes.isEmpty()) {
            return new ClassRosterResponse(classId, "", "", "", 0, List.of());
        }

        ClassOption classOption = classes.get(0);
        List<StudentRosterRow> students = jdbcTemplate.query("""
                        select s.id, s.student_no, s.chinese_name, s.english_name, s.school, s.grade_level
                        from class_enrollments ce
                        join students s on s.id = ce.student_id
                        where ce.class_id = ?
                          and ce.active = true
                          and s.active = true
                        order by s.grade_level, s.chinese_name, s.english_name
                        """,
                (rs, rowNum) -> new StudentRosterRow(
                        rs.getLong("id"),
                        rs.getString("student_no"),
                        rs.getString("chinese_name"),
                        rs.getString("english_name"),
                        rs.getString("school"),
                        rs.getString("grade_level")
                ),
                classId);

        return new ClassRosterResponse(
                classOption.id(),
                classOption.code(),
                classOption.name(),
                classOption.category(),
                classOption.activeStudentCount(),
                students
        );
    }
}
