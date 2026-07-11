package com.example.brocawebsite.registration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RegistrationRequestService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> VALID_STATUSES = Set.of(
            "NEW", "CONTACTED", "FOLLOW_UP", "WAITLIST", "TEACHER_REVIEW", "DIRECTOR_REVIEW", "ENROLLED", "ARCHIVED"
    );
    private static final Set<String> VALID_INQUIRY_TYPES = Set.of(
            "COURSE", "TRIAL", "AFTERSCHOOL", "SUMMER", "GEPT", "TRANSFER", "OTHER"
    );

    private final JdbcTemplate jdbcTemplate;

    RegistrationRequestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    RegistrationRequestRow create(RegistrationRequestCreateRequest request) {
        if (request == null) {
            throw badRequest("請填寫諮詢資料。");
        }
        if (!Boolean.TRUE.equals(request.privacyAccepted())) {
            throw badRequest("請先勾選個資使用同意。");
        }

        String inquiryType = normalizeInquiryType(request.inquiryType());
        String photoConsent = normalizePhotoConsent(request.photoConsent());
        String programInterests = joinList(request.programInterests(), 500);
        String availableTimes = joinList(request.availableTimes(), 500);
        String studentChineseName = required(request.studentChineseName(), "學生中文姓名");
        String gradeLevel = required(request.gradeLevel(), "學生年級");
        String parentName = required(request.parentName(), "家長姓名");
        String parentPhone = required(request.parentPhone(), "家長電話");

        if (programInterests.isBlank()) {
            throw badRequest("請至少選擇一個想詢問的課程。");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                            insert into registration_requests (
                                status, inquiry_type, current_need, program_interests,
                                student_chinese_name, student_english_name, grade_level, school, english_level,
                                parent_name, parent_phone, parent_line_id,
                                preferred_contact_time, contact_preference,
                                need_homework_care, need_pickup_support, available_times,
                                notes, referral_source, photo_consent, privacy_accepted, source,
                                created_at, updated_at
                            )
                            values (
                                'NEW', ?, ?, ?,
                                ?, ?, ?, ?, ?,
                                ?, ?, ?,
                                ?, ?,
                                ?, ?, ?,
                                ?, ?, ?, true, 'WEB',
                                current_timestamp, current_timestamp
                            )
                            """,
                    new String[]{"id"});
            statement.setString(1, inquiryType);
            statement.setString(2, trimToLength(request.currentNeed(), 80));
            statement.setString(3, programInterests);
            statement.setString(4, studentChineseName);
            statement.setString(5, trimToLength(request.studentEnglishName(), 80));
            statement.setString(6, gradeLevel);
            statement.setString(7, trimToLength(request.school(), 80));
            statement.setString(8, trimToLength(request.englishLevel(), 800));
            statement.setString(9, parentName);
            statement.setString(10, parentPhone);
            statement.setString(11, trimToLength(request.parentLineId(), 128));
            statement.setString(12, trimToLength(request.preferredContactTime(), 255));
            statement.setString(13, trimToLength(request.contactPreference(), 80));
            statement.setString(14, trimToLength(request.needHomeworkCare(), 30));
            statement.setString(15, trimToLength(request.needPickupSupport(), 30));
            statement.setString(16, availableTimes);
            statement.setString(17, trimToLength(request.notes(), 1000));
            statement.setString(18, trimToLength(request.referralSource(), 120));
            statement.setString(19, photoConsent);
            return statement;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "資料已送出，但系統無法取得流水號。");
        }
        return rowById(key.longValue());
    }

    RegistrationRequestResponse requests(String status, String query) {
        String normalizedStatus = normalizeStatusFilter(status);
        String keyword = isBlank(query) ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Long lookupId = parseRequestLookupId(query);

        List<RegistrationRequestRow> rows = jdbcTemplate.query("""
                        select rr.id, rr.status, rr.inquiry_type, rr.current_need, rr.program_interests,
                               rr.student_chinese_name, rr.student_english_name, rr.grade_level, rr.school, rr.english_level,
                               rr.parent_name, rr.parent_phone, rr.parent_line_id,
                               rr.preferred_contact_time, rr.contact_preference,
                               rr.need_homework_care, rr.need_pickup_support, rr.available_times,
                               rr.notes, rr.referral_source, rr.photo_consent, rr.source,
                               reviewer.display_name as reviewed_by, rr.reviewed_at, rr.review_note,
                               rr.enrolled_student_id, rr.enrolled_class_id, rr.enrolled_at, rr.created_at
                        from registration_requests rr
                        left join users reviewer on reviewer.id = rr.reviewed_by
                        where (? is null or rr.status = ?)
                          and (
                              ? is null
                              or lower(coalesce(rr.student_chinese_name, '')) like ?
                              or lower(coalesce(rr.student_english_name, '')) like ?
                              or lower(coalesce(rr.parent_name, '')) like ?
                              or lower(coalesce(rr.parent_phone, '')) like ?
                              or lower(coalesce(rr.parent_line_id, '')) like ?
                              or lower(coalesce(rr.program_interests, '')) like ?
                              or (? is not null and rr.id = ?)
                          )
                        order by
                            case rr.status
                                when 'NEW' then 0
                                when 'FOLLOW_UP' then 1
                                when 'WAITLIST' then 2
                                when 'TEACHER_REVIEW' then 3
                                when 'DIRECTOR_REVIEW' then 4
                                when 'CONTACTED' then 5
                                when 'ENROLLED' then 6
                                else 7
                            end,
                            rr.created_at desc,
                            rr.id desc
                        """,
                (rs, rowNum) -> row(rs),
                normalizedStatus,
                normalizedStatus,
                keyword,
                keyword,
                keyword,
                keyword,
                keyword,
                keyword,
                keyword,
                lookupId,
                lookupId);

        return new RegistrationRequestResponse(summary(), rows);
    }

    @Transactional
    RegistrationRequestRow updateStatus(Long requestId, RegistrationRequestStatusUpdateRequest request, String username) {
        String status = normalizeStatus(request == null ? null : request.status());
        Long reviewerId = findUserId(username);
        int updated = jdbcTemplate.update("""
                        update registration_requests
                        set status = ?,
                            review_note = ?,
                            reviewed_by = ?,
                            reviewed_at = current_timestamp,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                status,
                trimToLength(request == null ? null : request.reviewNote(), 500),
                reviewerId,
                requestId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆諮詢資料。");
        }
        return rowById(requestId);
    }

    @Transactional
    RegistrationEnrollmentResult enroll(Long requestId, RegistrationEnrollmentRequest request, String username) {
        RegistrationRequestRow registration = rowById(requestId);
        if (registration.enrolledStudentId() != null) {
            return new RegistrationEnrollmentResult(
                    registration.enrolledStudentId(),
                    studentNo(registration.enrolledStudentId()),
                    registration.enrolledClassId(),
                    false,
                    registration.enrolledClassId() != null,
                    "這筆諮詢已經轉入學生資料。",
                    List.of()
            );
        }

        Long classId = request == null ? null : request.classId();
        if (classId != null) {
            ensureActiveClassExists(classId);
        }

        List<RegistrationDuplicateCandidate> duplicates = duplicateCandidates(registration);
        if (!duplicates.isEmpty() && !Boolean.TRUE.equals(request == null ? null : request.force())) {
            throw new RegistrationDuplicateException(duplicateMessage(duplicates), duplicates);
        }

        String studentNo = nextRegistrationStudentNo(requestId);
        String pickupNote = trimToLength(
                "招生諮詢轉入；課輔：" + blankFallback(registration.needHomeworkCare()) +
                        "；接送：" + blankFallback(registration.needPickupSupport()) +
                        "；照片授權：" + registration.photoConsentLabel() +
                        "；原諮詢 #" + requestId,
                500);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                            insert into students (
                                student_no, chinese_name, english_name, school, grade_level,
                                parent_name, parent_phone, parent_line_id, pickup_note,
                                import_source, active, created_at, updated_at
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'REGISTRATION', true, current_timestamp, current_timestamp)
                            """,
                    new String[]{"id"});
            statement.setString(1, studentNo);
            statement.setString(2, registration.studentChineseName());
            statement.setString(3, trimToLength(registration.studentEnglishName(), 80));
            statement.setString(4, trimToLength(registration.school(), 80));
            statement.setString(5, trimToLength(registration.gradeLevel(), 40));
            statement.setString(6, trimToLength(registration.parentName(), 80));
            statement.setString(7, trimToLength(registration.parentPhone(), 40));
            statement.setString(8, trimToLength(registration.parentLineId(), 128));
            statement.setString(9, pickupNote);
            return statement;
        }, keyHolder);

        Number key = generatedId(keyHolder);
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "學生資料已建立，但系統無法取得學生編號。");
        }

        Long studentId = key.longValue();
        boolean enrolled = false;
        if (classId != null) {
            upsertEnrollment(studentId, classId);
            enrolled = true;
        }

        Long reviewerId = findUserId(username);
        String note = trimToLength(request == null ? null : request.reviewNote(), 500);
        if (note.isBlank()) {
            note = enrolled
                    ? "已由招生諮詢轉入學生資料，並完成分班。"
                    : "已由招生諮詢轉入學生資料，暫不分班。";
        }

        jdbcTemplate.update("""
                        update registration_requests
                        set status = 'ENROLLED',
                            review_note = ?,
                            reviewed_by = ?,
                            reviewed_at = current_timestamp,
                            enrolled_student_id = ?,
                            enrolled_class_id = ?,
                            enrolled_at = current_timestamp,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                note,
                reviewerId,
                studentId,
                classId,
                requestId);

        String message = enrolled
                ? "已建立學生資料並加入 " + classLabel(classId) + "。"
                : "已建立學生資料，暫不分班。";
        return new RegistrationEnrollmentResult(studentId, studentNo, classId, true, enrolled, message, List.of());
    }

    @Transactional
    RegistrationDeleteResponse deleteRequest(Long requestId) {
        RegistrationRequestRow registration = rowById(requestId);
        int deleted = jdbcTemplate.update("delete from registration_requests where id = ?", requestId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆招生諮詢資料。");
        }
        String message = registration.enrolledStudentId() == null
                ? "已刪除招生諮詢資料。"
                : "已刪除招生諮詢資料；已建立的學生資料仍會保留。";
        return new RegistrationDeleteResponse(requestId, registration.requestCode(), true, message);
    }

    private RegistrationRequestSummary summary() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(7);

        return new RegistrationRequestSummary(
                count("select count(*) from registration_requests where status = 'NEW'"),
                count("select count(*) from registration_requests where status = 'CONTACTED'"),
                count("select count(*) from registration_requests where status = 'FOLLOW_UP'"),
                count("select count(*) from registration_requests where status = 'WAITLIST'"),
                count("select count(*) from registration_requests where status = 'TEACHER_REVIEW'"),
                count("select count(*) from registration_requests where status = 'DIRECTOR_REVIEW'"),
                count("select count(*) from registration_requests where status = 'ENROLLED'"),
                count("select count(*) from registration_requests where status = 'ARCHIVED'"),
                count("""
                        select count(*)
                        from registration_requests
                        where created_at >= ?
                          and created_at < ?
                        """, today.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                count("""
                        select count(*)
                        from registration_requests
                        where created_at >= ?
                          and created_at < ?
                        """, weekStart.atStartOfDay(), weekEnd.atStartOfDay())
        );
    }

    private RegistrationRequestRow rowById(Long id) {
        List<RegistrationRequestRow> rows = jdbcTemplate.query("""
                        select rr.id, rr.status, rr.inquiry_type, rr.current_need, rr.program_interests,
                               rr.student_chinese_name, rr.student_english_name, rr.grade_level, rr.school, rr.english_level,
                               rr.parent_name, rr.parent_phone, rr.parent_line_id,
                               rr.preferred_contact_time, rr.contact_preference,
                               rr.need_homework_care, rr.need_pickup_support, rr.available_times,
                               rr.notes, rr.referral_source, rr.photo_consent, rr.source,
                               reviewer.display_name as reviewed_by, rr.reviewed_at, rr.review_note,
                               rr.enrolled_student_id, rr.enrolled_class_id, rr.enrolled_at, rr.created_at
                        from registration_requests rr
                        left join users reviewer on reviewer.id = rr.reviewed_by
                        where rr.id = ?
                        """,
                (rs, rowNum) -> row(rs),
                id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這筆諮詢資料。");
        }
        return rows.get(0);
    }

    private RegistrationRequestRow row(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String status = rs.getString("status");
        String inquiryType = rs.getString("inquiry_type");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new RegistrationRequestRow(
                id,
                requestCode(id, createdAt),
                status,
                statusLabel(status),
                inquiryType,
                inquiryTypeLabel(inquiryType),
                rs.getString("current_need"),
                splitList(rs.getString("program_interests")),
                rs.getString("student_chinese_name"),
                rs.getString("student_english_name"),
                rs.getString("grade_level"),
                rs.getString("school"),
                rs.getString("english_level"),
                rs.getString("parent_name"),
                rs.getString("parent_phone"),
                rs.getString("parent_line_id"),
                rs.getString("preferred_contact_time"),
                rs.getString("contact_preference"),
                rs.getString("need_homework_care"),
                rs.getString("need_pickup_support"),
                splitList(rs.getString("available_times")),
                rs.getString("notes"),
                rs.getString("referral_source"),
                rs.getString("photo_consent"),
                photoConsentLabel(rs.getString("photo_consent")),
                rs.getString("source"),
                rs.getString("reviewed_by"),
                formatTimestamp(rs.getTimestamp("reviewed_at")),
                rs.getString("review_note"),
                nullableLong(rs, "enrolled_student_id"),
                nullableLong(rs, "enrolled_class_id"),
                formatTimestamp(rs.getTimestamp("enrolled_at")),
                formatTimestamp(createdAt)
        );
    }

    private String requestCode(Long id, Timestamp createdAt) {
        LocalDate date = createdAt == null ? LocalDate.now() : createdAt.toLocalDateTime().toLocalDate();
        return "BRCA-" + date.format(REQUEST_DATE_FORMATTER) + "-" + String.format("%04d", id == null ? 0 : id);
    }

    private Long parseRequestLookupId(String query) {
        if (isBlank(query)) {
            return null;
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        int dashIndex = normalized.lastIndexOf('-');
        if (dashIndex >= 0 && dashIndex < normalized.length() - 1) {
            normalized = normalized.substring(dashIndex + 1);
        }
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<RegistrationDuplicateCandidate> duplicateCandidates(RegistrationRequestRow registration) {
        String chineseName = normalizeKey(registration.studentChineseName());
        String englishName = normalizeKey(registration.studentEnglishName());
        String gradeLevel = normalizeKey(registration.gradeLevel());
        String parentPhone = trimToLength(registration.parentPhone(), 40);

        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (!chineseName.isBlank() && !parentPhone.isBlank()) {
            clauses.add("(lower(trim(s.chinese_name)) = ? and trim(coalesce(s.parent_phone, '')) = ?)");
            args.add(chineseName);
            args.add(parentPhone);
        }
        if (!chineseName.isBlank() && !gradeLevel.isBlank()) {
            clauses.add("(lower(trim(s.chinese_name)) = ? and lower(trim(coalesce(s.grade_level, ''))) = ?)");
            args.add(chineseName);
            args.add(gradeLevel);
        }
        if (!englishName.isBlank() && !parentPhone.isBlank()) {
            clauses.add("(lower(trim(coalesce(s.english_name, ''))) = ? and trim(coalesce(s.parent_phone, '')) = ?)");
            args.add(englishName);
            args.add(parentPhone);
        }
        if (clauses.isEmpty()) {
            return List.of();
        }

        String sql = """
                        select s.id, s.student_no, s.chinese_name, s.english_name, s.grade_level, s.parent_phone
                        from students s
                        where s.active = true
                          and (
                        """ + String.join("\n                              or ", clauses) + """
                          )
                        order by s.updated_at desc, s.id desc
                        limit 5
                        """;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new RegistrationDuplicateCandidate(
                        rs.getLong("id"),
                        rs.getString("student_no"),
                        rs.getString("chinese_name"),
                        rs.getString("english_name"),
                        rs.getString("grade_level"),
                        rs.getString("parent_phone"),
                        classSummary(rs.getLong("id"))
                ),
                args.toArray());
    }

    private String duplicateMessage(List<RegistrationDuplicateCandidate> duplicates) {
        String first = duplicates.isEmpty()
                ? ""
                : duplicates.get(0).chineseName() + " / " + duplicates.get(0).studentNo() + " / " + duplicates.get(0).classes();
        return "可能已存在學生資料：" + first +
                "。請先確認後再轉入；若家長不確定，可請家長直接來電補習班詢問：(07) 697-8690。";
    }

    private String classSummary(Long studentId) {
        List<String> classes = jdbcTemplate.query("""
                        select c.code
                        from class_enrollments ce
                        join classes c on c.id = ce.class_id
                        where ce.student_id = ?
                          and ce.active = true
                          and c.status = 'ACTIVE'
                        order by c.code
                        """,
                (rs, rowNum) -> rs.getString("code"),
                studentId);
        return classes.isEmpty() ? "尚未分班" : String.join(" / ", classes);
    }

    private void ensureActiveClassExists(Long classId) {
        if (classId == null || count("select count(*) from classes where id = ? and status = 'ACTIVE'", classId) == 0) {
            throw badRequest("請選擇有效班級，或改為暫不分班。");
        }
    }

    private void upsertEnrollment(Long studentId, Long classId) {
        List<Long> enrollmentIds = jdbcTemplate.query("""
                        select id
                        from class_enrollments
                        where class_id = ?
                          and student_id = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                classId,
                studentId);
        if (enrollmentIds.isEmpty()) {
            jdbcTemplate.update("""
                            insert into class_enrollments (class_id, student_id, active)
                            values (?, ?, true)
                            """,
                    classId,
                    studentId);
            return;
        }
        jdbcTemplate.update("""
                        update class_enrollments
                        set active = true
                        where id = ?
                        """,
                enrollmentIds.get(0));
    }

    private String nextRegistrationStudentNo(Long requestId) {
        String base = "REG" + String.format("%06d", requestId == null ? 0 : requestId);
        String candidate = base;
        int suffix = 2;
        while (count("select count(*) from students where student_no = ?", candidate) > 0) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String studentNo(Long studentId) {
        List<String> values = jdbcTemplate.query("""
                        select student_no
                        from students
                        where id = ?
                        """,
                (rs, rowNum) -> rs.getString("student_no"),
                studentId);
        return values.isEmpty() ? "" : values.get(0);
    }

    private String classLabel(Long classId) {
        List<String> values = jdbcTemplate.query("""
                        select concat(code, ' ', name)
                        from classes
                        where id = ?
                        """,
                (rs, rowNum) -> rs.getString(1),
                classId);
        return values.isEmpty() ? "指定班級" : values.get(0);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankFallback(String value) {
        return isBlank(value) ? "未填" : value.trim();
    }

    private String normalizeStatusFilter(String status) {
        if (isBlank(status) || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        return normalizeStatus(status);
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (VALID_STATUSES.contains(normalized)) {
            return normalized;
        }
        throw badRequest("諮詢狀態不正確。");
    }

    private String normalizeInquiryType(String inquiryType) {
        String normalized = inquiryType == null ? "COURSE" : inquiryType.trim().toUpperCase(Locale.ROOT);
        return VALID_INQUIRY_TYPES.contains(normalized) ? normalized : "OTHER";
    }

    private String statusLabel(String status) {
        return switch (String.valueOf(status)) {
            case "NEW" -> "新諮詢";
            case "CONTACTED" -> "已聯絡";
            case "FOLLOW_UP" -> "需追蹤";
            case "WAITLIST" -> "候補名單";
            case "TEACHER_REVIEW" -> "老師評估";
            case "DIRECTOR_REVIEW" -> "主任確認";
            case "ENROLLED" -> "已入班";
            case "ARCHIVED" -> "已封存";
            default -> "未分類";
        };
    }

    private String inquiryTypeLabel(String inquiryType) {
        return switch (String.valueOf(inquiryType)) {
            case "TRIAL" -> "預約試聽";
            case "AFTERSCHOOL" -> "安親課輔";
            case "SUMMER" -> "暑期課程";
            case "GEPT" -> "全民英檢";
            case "TRANSFER" -> "轉班 / 插班";
            case "OTHER" -> "其他需求";
            default -> "課程諮詢";
        };
    }

    private String normalizePhotoConsent(String photoConsent) {
        String normalized = photoConsent == null ? "" : photoConsent.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AGREE", "DISAGREE" -> normalized;
            default -> throw badRequest("請選擇孩子照片與作品使用授權。");
        };
    }

    private String photoConsentLabel(String photoConsent) {
        return switch (String.valueOf(photoConsent)) {
            case "AGREE" -> "同意公開使用";
            case "DISAGREE" -> "不同意公開使用";
            default -> "尚未確認";
        };
    }

    private String required(String value, String label) {
        String trimmed = trimToLength(value, 120);
        if (trimmed.isBlank()) {
            throw badRequest("請填寫" + label + "。");
        }
        return trimmed;
    }

    private String joinList(List<String> values, int maxLength) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = trimToLength(value, 80);
            if (!trimmed.isBlank()) {
                cleaned.add(trimmed.replace("|", "/"));
            }
        }
        return trimToLength(String.join(" | ", cleaned), maxLength);
    }

    private List<String> splitList(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : value.split("\\s*\\|\\s*")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到目前登入使用者。");
        }
        return ids.get(0);
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
