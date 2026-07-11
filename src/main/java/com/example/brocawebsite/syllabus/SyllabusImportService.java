package com.example.brocawebsite.syllabus;

import java.io.IOException;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class SyllabusImportService {

    private static final int PREVIEW_LIMIT = 50;
    private static final DateTimeFormatter DATE_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy年 M月 d日 (E)", Locale.TAIWAN);
    private static final DateTimeFormatter DATE_TIME_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN);

    private final JdbcTemplate jdbcTemplate;
    private final SyllabusWorkbookParser parser;

    SyllabusImportService(JdbcTemplate jdbcTemplate, SyllabusWorkbookParser parser) {
        this.jdbcTemplate = jdbcTemplate;
        this.parser = parser;
    }

    @Transactional
    SyllabusImportResponse importFile(MultipartFile file, String sheetName, boolean syncRoster, Authentication authentication) {
        validateFile(file);

        ParsedSyllabusWorkbook parsedWorkbook = parseWorkbook(file, sheetName, syncRoster);

        Long importedBy = findUserId(authentication);
        String fileName = safeFileName(file.getOriginalFilename());
        Long batchId = insertBatch(fileName, parsedWorkbook, importedBy, syncRoster);

        for (ParsedLessonPlan lesson : parsedWorkbook.lessons()) {
            insertLessonPlan(batchId, lesson);
        }
        RosterImportSummary rosterSummary = syncRoster
                ? importStudentRoster(parsedWorkbook.studentEnrollments())
                : RosterImportSummary.skipped();
        updateBatchRosterStats(batchId, rosterSummary);
        activateBatch(batchId, importedBy, "IMPORT", "匯入 Excel 課程進度：" + fileName);

        return toResponse(batchId, fileName, parsedWorkbook, rosterSummary);
    }

    SyllabusImportPreviewResponse previewFile(MultipartFile file, String sheetName, boolean syncRoster) {
        validateFile(file);
        ParsedSyllabusWorkbook parsedWorkbook = parseWorkbook(file, sheetName, syncRoster);
        SyllabusRosterDiff rosterDiff = syncRoster
                ? previewStudentRoster(parsedWorkbook.studentEnrollments())
                : SyllabusRosterDiff.noSync();
        return toPreviewResponse(safeFileName(file.getOriginalFilename()), parsedWorkbook, syncRoster, rosterDiff);
    }

    SyllabusImportHistoryResponse importHistory() {
        List<SyllabusImportBatchHistoryRow> rows = jdbcTemplate.query("""
                        select b.id, b.original_filename, b.sheet_name, b.imported_at,
                               coalesce(u.display_name, u.username, '系統') as imported_by_name,
                               b.activated_at, b.active, b.status, b.total_lessons, b.approved_count,
                               b.draft_count, b.review_count, b.warning_count, b.sync_roster,
                               b.roster_student_count, b.roster_class_count, b.roster_enrollment_count,
                               r.first_lesson_date, r.last_lesson_date
                        from syllabus_import_batches b
                        left join users u on u.id = b.imported_by
                        left join (
                            select import_batch_id, min(lesson_date) as first_lesson_date,
                                   max(lesson_date) as last_lesson_date
                            from lesson_plans
                            group by import_batch_id
                        ) r on r.import_batch_id = b.id
                        order by b.active desc, coalesce(b.activated_at, b.imported_at) desc,
                                 b.imported_at desc, b.id desc
                        limit 12
                        """,
                (rs, rowNum) -> {
                    Date firstLessonDate = rs.getDate("first_lesson_date");
                    Date lastLessonDate = rs.getDate("last_lesson_date");
                    boolean active = rs.getBoolean("active");
                    return new SyllabusImportBatchHistoryRow(
                            rs.getLong("id"),
                            rs.getString("original_filename"),
                            rs.getString("sheet_name"),
                            timestampString(rs.getTimestamp("imported_at")),
                            rs.getString("imported_by_name"),
                            timestampString(rs.getTimestamp("activated_at")),
                            active,
                            rs.getString("status"),
                            rs.getInt("total_lessons"),
                            rs.getInt("approved_count"),
                            rs.getInt("draft_count"),
                            rs.getInt("review_count"),
                            rs.getInt("warning_count"),
                            rs.getBoolean("sync_roster"),
                            rs.getInt("roster_student_count"),
                            rs.getInt("roster_class_count"),
                            rs.getInt("roster_enrollment_count"),
                            sqlDateString(firstLessonDate),
                            sqlDateString(lastLessonDate),
                            dateRange(firstLessonDate, lastLessonDate),
                            !active
                    );
                });

        Integer total = jdbcTemplate.queryForObject("select count(*) from syllabus_import_batches", Integer.class);
        Long activeBatchId = rows.stream()
                .filter(SyllabusImportBatchHistoryRow::active)
                .map(SyllabusImportBatchHistoryRow::id)
                .findFirst()
                .orElse(rows.isEmpty() ? null : rows.get(0).id());

        return new SyllabusImportHistoryResponse(activeBatchId, total == null ? 0 : total, rows);
    }

    @Transactional
    SyllabusImportRestoreResponse restoreBatch(Long batchId, Authentication authentication) {
        requireAdmin(authentication);
        StoredBatch batch = requireBatch(batchId);
        Long actorId = findUserId(authentication);

        Integer lessonCount = jdbcTemplate.queryForObject("""
                        select count(*)
                        from lesson_plans
                        where import_batch_id = ?
                        """,
                Integer.class,
                batchId);
        if (lessonCount == null || lessonCount == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "此批次沒有課程內容，無法設為現行課表。");
        }

        activateBatch(batchId, actorId, "RESTORE", "回復課表批次：" + batch.fileName());
        SyllabusImportResponse currentImport = responseForBatch(requireBatch(batchId));
        return new SyllabusImportRestoreResponse(
                batchId,
                "已回復為現行課表批次：" + batch.fileName() + "。學生資料、LINE 與家長聯絡資料未被回復或覆蓋。",
                currentImport
        );
    }

    @Transactional
    SyllabusImportDeleteResponse deleteBatch(Long batchId, Authentication authentication) {
        requireAdmin(authentication);
        StoredBatch batch = requireBatch(batchId);
        if (batch.active()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "現行課表不能直接刪除；請先把另一個批次設為現行後再刪除。");
        }

        jdbcTemplate.update("delete from syllabus_import_actions where batch_id = ?", batchId);
        int lessonRows = jdbcTemplate.update("delete from lesson_plans where import_batch_id = ?", batchId);
        int batchRows = jdbcTemplate.update("delete from syllabus_import_batches where id = ?", batchId);
        if (batchRows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到這個匯入批次。");
        }

        String message = "已刪除「" + batch.fileName() + "」與 " + lessonRows + " 筆課程進度；學生、分班與聯絡資料未被刪除。";
        return new SyllabusImportDeleteResponse(batchId, true, message);
    }

    private ParsedSyllabusWorkbook parseWorkbook(MultipartFile file, String sheetName, boolean syncRoster) {
        try {
            return parser.parse(file, sheetName, syncRoster);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 檔案讀取失敗，請確認檔案格式。", ex);
        }
    }

    SyllabusImportResponse latestImport() {
        List<StoredBatch> batches = findCurrentBatch();
        if (batches.isEmpty()) {
            return SyllabusImportResponse.empty();
        }

        return responseForBatch(batches.get(0));
    }

    private SyllabusImportResponse responseForBatch(StoredBatch batch) {
        List<LessonPlanPreview> preview = jdbcTemplate.query("""
                        select lesson_date, class_label, teacher_names, content, approval_status, source_sheet,
                               source_row_number, source_column_label
                        from lesson_plans
                        where import_batch_id = ?
                        order by lesson_date, source_row_number, source_column_label
                        limit ?
                        """,
                (rs, rowNum) -> toPreview(
                        rs.getDate("lesson_date").toLocalDate(),
                        rs.getString("class_label"),
                        rs.getString("teacher_names"),
                        rs.getString("content"),
                        rs.getString("approval_status"),
                        rs.getString("source_sheet"),
                        rs.getInt("source_row_number"),
                        rs.getString("source_column_label")
                ),
                batch.id(),
                PREVIEW_LIMIT);

        return new SyllabusImportResponse(
                true,
                batch.id(),
                batch.fileName(),
                batch.sheetName(),
                batch.totalLessons(),
                batch.approvedCount(),
                batch.draftCount(),
                batch.reviewCount(),
                batch.rosterStudentCount(),
                batch.rosterClassCount(),
                batch.rosterEnrollmentCount(),
                batch.syncRoster(),
                batch.syncRoster()
                        ? "此批次匯入時已同步 Excel 學生名單、班級與分班。"
                        : "此批次只匯入課程進度；學生資料、班級與分班沒有變更。",
                batch.warningCount(),
                splitWarnings(batch.warningsText()),
                preview
        );
    }

    ScheduleDayResponse scheduleForDay(String requestedDate) {
        List<StoredBatch> batches = findCurrentBatch();
        if (batches.isEmpty()) {
            return new ScheduleDayResponse(false, null, "", "", "尚未匯入課程進度",
                    "", "", "", "", List.of());
        }

        StoredBatch batch = batches.get(0);
        LocalDate selectedDate = resolveScheduleDate(batch.id(), requestedDate);
        if (selectedDate == null) {
            return new ScheduleDayResponse(true, batch.id(), batch.fileName(), "", "目前沒有課程進度",
                    "", "", "", "", List.of());
        }

        List<ScheduleLessonCard> lessons = jdbcTemplate.query("""
                        select id, lesson_date, class_label, teacher_names, content, approval_status, source_sheet,
                               source_row_number, source_column_label
                        from lesson_plans
                        where import_batch_id = ?
                          and lesson_date = ?
                        order by class_label, source_row_number, source_column_label
                        """,
                (rs, rowNum) -> new ScheduleLessonCard(
                        rs.getLong("id"),
                        rs.getDate("lesson_date").toLocalDate().toString(),
                        rs.getString("class_label"),
                        rs.getString("teacher_names"),
                        summarize(rs.getString("content")),
                        rs.getString("content"),
                        rs.getString("approval_status"),
                        approvalLabel(rs.getString("approval_status")),
                        rs.getString("source_sheet") + "!" + rs.getString("source_column_label") + rs.getInt("source_row_number")
                ),
                batch.id(),
                Date.valueOf(selectedDate));

        LocalDate firstLessonDate = findFirstLessonDate(batch.id());
        LocalDate lastLessonDate = findLastLessonDate(batch.id());
        LocalDate previousLessonDate = findPreviousLessonDate(batch.id(), selectedDate);
        LocalDate nextLessonDate = findNextLessonDate(batch.id(), selectedDate);

        return new ScheduleDayResponse(
                true,
                batch.id(),
                batch.fileName(),
                selectedDate.toString(),
                selectedDate.format(DATE_DISPLAY_FORMATTER),
                dateString(firstLessonDate),
                dateString(lastLessonDate),
                dateString(previousLessonDate),
                dateString(nextLessonDate),
                lessons
        );
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請先選擇 Excel 檔案。");
        }

        String filename = safeFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目前只支援 .xlsx 或 .xls 檔案。");
        }
    }

    private List<StoredBatch> findCurrentBatch() {
        return jdbcTemplate.query("""
                        select id, original_filename, sheet_name, total_lessons, approved_count, draft_count,
                               review_count, sync_roster, roster_student_count, roster_class_count,
                               roster_enrollment_count, warning_count, warnings_text, active, status
                        from syllabus_import_batches
                        order by active desc, coalesce(activated_at, imported_at) desc,
                                 imported_at desc, id desc
                        limit 1
                        """,
                (rs, rowNum) -> mapStoredBatch(rs));
    }

    private LocalDate resolveScheduleDate(Long batchId, String requestedDate) {
        LocalDate parsedRequest = parseRequestedDate(requestedDate);
        if (parsedRequest != null) {
            return parsedRequest;
        }

        LocalDate today = LocalDate.now();
        List<LocalDate> futureDates = jdbcTemplate.query("""
                        select distinct lesson_date
                        from lesson_plans
                        where import_batch_id = ?
                          and lesson_date >= ?
                        order by lesson_date
                        limit 1
                        """,
                (rs, rowNum) -> rs.getDate("lesson_date").toLocalDate(),
                batchId,
                Date.valueOf(today));
        if (!futureDates.isEmpty()) {
            return futureDates.get(0);
        }

        List<LocalDate> firstDates = jdbcTemplate.query("""
                        select distinct lesson_date
                        from lesson_plans
                        where import_batch_id = ?
                        order by lesson_date
                        limit 1
                        """,
                (rs, rowNum) -> rs.getDate("lesson_date").toLocalDate(),
                batchId);
        return firstDates.isEmpty() ? null : firstDates.get(0);
    }

    private LocalDate findFirstLessonDate(Long batchId) {
        List<LocalDate> dates = jdbcTemplate.query("""
                        select distinct lesson_date
                        from lesson_plans
                        where import_batch_id = ?
                        order by lesson_date
                        limit 1
                        """,
                (rs, rowNum) -> rs.getDate("lesson_date").toLocalDate(),
                batchId);
        return dates.isEmpty() ? null : dates.get(0);
    }

    private LocalDate findLastLessonDate(Long batchId) {
        List<LocalDate> dates = jdbcTemplate.query("""
                        select distinct lesson_date
                        from lesson_plans
                        where import_batch_id = ?
                        order by lesson_date desc
                        limit 1
                        """,
                (rs, rowNum) -> rs.getDate("lesson_date").toLocalDate(),
                batchId);
        return dates.isEmpty() ? null : dates.get(0);
    }

    private LocalDate findPreviousLessonDate(Long batchId, LocalDate selectedDate) {
        List<LocalDate> dates = jdbcTemplate.query("""
                        select distinct lesson_date
                        from lesson_plans
                        where import_batch_id = ?
                          and lesson_date < ?
                        order by lesson_date desc
                        limit 1
                        """,
                (rs, rowNum) -> rs.getDate("lesson_date").toLocalDate(),
                batchId,
                Date.valueOf(selectedDate));
        return dates.isEmpty() ? null : dates.get(0);
    }

    private LocalDate findNextLessonDate(Long batchId, LocalDate selectedDate) {
        List<LocalDate> dates = jdbcTemplate.query("""
                        select distinct lesson_date
                        from lesson_plans
                        where import_batch_id = ?
                          and lesson_date > ?
                        order by lesson_date
                        limit 1
                        """,
                (rs, rowNum) -> rs.getDate("lesson_date").toLocalDate(),
                batchId,
                Date.valueOf(selectedDate));
        return dates.isEmpty() ? null : dates.get(0);
    }

    private String dateString(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private LocalDate parseRequestedDate(String requestedDate) {
        if (requestedDate == null || requestedDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(requestedDate);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Long insertBatch(String fileName, ParsedSyllabusWorkbook parsedWorkbook, Long importedBy, boolean syncRoster) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            insert into syllabus_import_batches (
                                original_filename, sheet_name, imported_by, total_lessons, approved_count,
                                draft_count, review_count, sync_roster, warning_count, warnings_text, status
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    new String[]{"id"});

            statement.setString(1, fileName);
            statement.setString(2, parsedWorkbook.sheetName());
            if (importedBy == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, importedBy);
            }
            statement.setInt(4, parsedWorkbook.lessons().size());
            statement.setInt(5, Math.toIntExact(parsedWorkbook.approvedCount()));
            statement.setInt(6, Math.toIntExact(parsedWorkbook.draftCount()));
            statement.setInt(7, Math.toIntExact(parsedWorkbook.reviewCount()));
            statement.setBoolean(8, syncRoster);
            statement.setInt(9, parsedWorkbook.warningCount());
            statement.setString(10, String.join("\n", parsedWorkbook.warnings()));
            statement.setString(11, "IMPORTED");
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "匯入批次建立失敗。");
        }
        return key.longValue();
    }

    private void insertLessonPlan(Long batchId, ParsedLessonPlan lesson) {
        jdbcTemplate.update("""
                        insert into lesson_plans (
                            import_batch_id, lesson_date, class_label, teacher_names, content, approval_status, source_sheet,
                            source_row_number, source_column_label, approval_source_value
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                batchId,
                Date.valueOf(lesson.lessonDate()),
                lesson.classLabel(),
                lesson.teacherNames(),
                lesson.content(),
                lesson.approvalStatus(),
                lesson.sourceSheet(),
                lesson.sourceRowNumber(),
                lesson.sourceColumnLabel(),
                lesson.approvalSourceValue());
    }

    private void updateBatchRosterStats(Long batchId, RosterImportSummary rosterSummary) {
        jdbcTemplate.update("""
                        update syllabus_import_batches
                        set sync_roster = ?, roster_student_count = ?, roster_class_count = ?,
                            roster_enrollment_count = ?
                        where id = ?
                        """,
                rosterSummary.requested(),
                rosterSummary.studentCount(),
                rosterSummary.classCount(),
                rosterSummary.enrollmentCount(),
                batchId);
    }

    private void activateBatch(Long batchId, Long actorId, String actionType, String note) {
        jdbcTemplate.update("""
                        update syllabus_import_batches
                        set active = false, status = 'SUPERSEDED',
                            superseded_by = ?, superseded_at = current_timestamp
                        where active = true
                          and id <> ?
                        """,
                actorId,
                batchId);

        int updated = jdbcTemplate.update("""
                        update syllabus_import_batches
                        set active = true, status = 'ACTIVE', activated_by = ?, activated_at = current_timestamp,
                            superseded_by = null, superseded_at = null
                        where id = ?
                        """,
                actorId,
                batchId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定的匯入批次。");
        }

        insertImportAction(batchId, actionType, actorId, note);
    }

    private void insertImportAction(Long batchId, String actionType, Long actorId, String note) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            insert into syllabus_import_actions (batch_id, action_type, actor_id, note)
                            values (?, ?, ?, ?)
                            """);
            statement.setLong(1, batchId);
            statement.setString(2, actionType);
            if (actorId == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, actorId);
            }
            statement.setString(4, note);
            return statement;
        });
    }

    private Long findUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }

        List<Long> ids = jdbcTemplate.query("""
                        select id
                        from users
                        where username = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                authentication.getName());

        return ids.isEmpty() ? null : ids.get(0);
    }

    private StoredBatch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少匯入批次編號。");
        }

        List<StoredBatch> batches = jdbcTemplate.query("""
                        select id, original_filename, sheet_name, total_lessons, approved_count, draft_count,
                               review_count, sync_roster, roster_student_count, roster_class_count,
                               roster_enrollment_count, warning_count, warnings_text, active, status
                        from syllabus_import_batches
                        where id = ?
                        """,
                (rs, rowNum) -> mapStoredBatch(rs),
                batchId);
        if (batches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到指定的匯入批次。");
        }
        return batches.get(0);
    }

    private void requireAdmin(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有主任帳號可以回復匯入批次。");
        }
    }

    private SyllabusImportResponse toResponse(Long batchId, String fileName, ParsedSyllabusWorkbook parsedWorkbook,
                                              RosterImportSummary rosterSummary) {
        List<LessonPlanPreview> preview = parsedWorkbook.lessons().stream()
                .limit(PREVIEW_LIMIT)
                .map(this::toPreview)
                .toList();

        return new SyllabusImportResponse(
                true,
                batchId,
                fileName,
                parsedWorkbook.sheetName(),
                parsedWorkbook.lessons().size(),
                Math.toIntExact(parsedWorkbook.approvedCount()),
                Math.toIntExact(parsedWorkbook.draftCount()),
                Math.toIntExact(parsedWorkbook.reviewCount()),
                rosterSummary.studentCount(),
                rosterSummary.classCount(),
                rosterSummary.enrollmentCount(),
                rosterSummary.requested(),
                rosterSummary.message(),
                parsedWorkbook.warningCount(),
                parsedWorkbook.warnings(),
                preview
        );
    }

    private SyllabusImportPreviewResponse toPreviewResponse(String fileName, ParsedSyllabusWorkbook parsedWorkbook,
                                                            boolean syncRoster, SyllabusRosterDiff rosterDiff) {
        List<LessonPlanPreview> preview = parsedWorkbook.lessons().stream()
                .limit(PREVIEW_LIMIT)
                .map(this::toPreview)
                .toList();

        return new SyllabusImportPreviewResponse(
                true,
                fileName,
                parsedWorkbook.sheetName(),
                parsedWorkbook.lessons().size(),
                Math.toIntExact(parsedWorkbook.approvedCount()),
                Math.toIntExact(parsedWorkbook.draftCount()),
                Math.toIntExact(parsedWorkbook.reviewCount()),
                Math.toIntExact(parsedWorkbook.rosterStudentCount()),
                Math.toIntExact(parsedWorkbook.rosterClassCount()),
                Math.toIntExact(parsedWorkbook.rosterEnrollmentCount()),
                syncRoster,
                syncRoster
                        ? "預覽已讀取 Excel 學生名單，尚未寫入資料庫。"
                        : "本次預覽只檢查課程進度，學生與分班不會變更。",
                rosterDiff,
                parsedWorkbook.warningCount(),
                parsedWorkbook.warnings(),
                preview
        );
    }

    private LessonPlanPreview toPreview(ParsedLessonPlan lesson) {
        return toPreview(
                lesson.lessonDate(),
                lesson.classLabel(),
                lesson.teacherNames(),
                lesson.content(),
                lesson.approvalStatus(),
                lesson.sourceSheet(),
                lesson.sourceRowNumber(),
                lesson.sourceColumnLabel()
        );
    }

    private LessonPlanPreview toPreview(LocalDate lessonDate, String classLabel, String teacherNames, String content,
                                        String approvalStatus, String sourceSheet, int sourceRowNumber,
                                        String sourceColumnLabel) {
        return new LessonPlanPreview(
                lessonDate.toString(),
                classLabel,
                teacherNames == null ? "" : teacherNames,
                summarize(content),
                approvalStatus,
                approvalLabel(approvalStatus),
                sourceSheet + "!" + sourceColumnLabel + sourceRowNumber,
                actionLabel(approvalStatus)
        );
    }

    private String summarize(String content) {
        String summary = content == null ? "" : content.replace('\u00a0', ' ').trim().replaceAll("\\s+", " ");
        int maxLength = 220;
        if (summary.length() <= maxLength) {
            return summary;
        }
        return summary.substring(0, maxLength - 3) + "...";
    }

    private String approvalLabel(String status) {
        return switch (status) {
            case "DIRECTOR_APPROVED" -> "主任已核准";
            case "TEACHER_DRAFT" -> "老師草稿";
            default -> "需確認";
        };
    }

    private String actionLabel(String status) {
        return switch (status) {
            case "DIRECTOR_APPROVED" -> "建立已核准進度";
            case "TEACHER_DRAFT" -> "建立老師草稿";
            default -> "存為待確認";
        };
    }

    private RosterImportSummary importStudentRoster(List<ParsedStudentEnrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return RosterImportSummary.completed(0, 0, 0);
        }

        Set<String> studentNos = new LinkedHashSet<>();
        Set<String> classCodes = new LinkedHashSet<>();
        Set<String> enrollmentKeys = new LinkedHashSet<>();

        for (ParsedStudentEnrollment enrollment : enrollments) {
            studentNos.add(enrollment.studentNo());
            classCodes.add(enrollment.classCode());
            enrollmentKeys.add(enrollment.studentNo() + "|" + enrollment.classCode());
        }

        deactivateReadingAssignmentEnrollments(classCodes);

        for (ParsedStudentEnrollment enrollment : enrollments) {
            Long classId = upsertClass(enrollment.classCode());
            Long studentId = upsertStudent(enrollment);
            upsertClassEnrollment(classId, studentId);
        }

        return RosterImportSummary.completed(studentNos.size(), classCodes.size(), enrollmentKeys.size());
    }

    private SyllabusRosterDiff previewStudentRoster(List<ParsedStudentEnrollment> enrollments) {
        if (enrollments.isEmpty()) {
            return new SyllabusRosterDiff(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    "Excel 沒有讀到 Reading Assignment 名單。");
        }

        Map<String, ParsedStudentEnrollment> parsedStudents = new LinkedHashMap<>();
        Map<String, Set<String>> parsedClassesByStudent = new LinkedHashMap<>();
        Set<String> parsedClassCodes = new LinkedHashSet<>();
        Set<String> parsedEnrollmentKeys = new LinkedHashSet<>();

        for (ParsedStudentEnrollment enrollment : enrollments) {
            parsedStudents.putIfAbsent(enrollment.studentNo(), enrollment);
            parsedClassesByStudent
                    .computeIfAbsent(enrollment.studentNo(), ignored -> new LinkedHashSet<>())
                    .add(enrollment.classCode());
            parsedClassCodes.add(enrollment.classCode());
            parsedEnrollmentKeys.add(enrollment.studentNo() + "|" + enrollment.classCode());
        }

        Map<String, StoredStudent> existingStudents = loadExistingStudents();
        Set<String> existingClassCodes = loadExistingClassCodes();
        Map<String, StoredEnrollment> existingEnrollments = loadExistingReadingAssignmentEnrollments();
        Map<String, Set<String>> activeClassesByStudent = new HashMap<>();
        for (StoredEnrollment enrollment : existingEnrollments.values()) {
            if (enrollment.active()) {
                activeClassesByStudent
                        .computeIfAbsent(enrollment.studentNo(), ignored -> new HashSet<>())
                        .add(enrollment.classCode());
            }
        }

        int newStudents = 0;
        int existingStudentCount = 0;
        int changedStudents = 0;
        int movedStudents = 0;
        List<String> newStudentSamples = new ArrayList<>();
        List<String> changedStudentSamples = new ArrayList<>();
        List<String> movedStudentSamples = new ArrayList<>();
        for (Map.Entry<String, ParsedStudentEnrollment> entry : parsedStudents.entrySet()) {
            StoredStudent existing = existingStudents.get(entry.getKey());
            if (existing == null) {
                newStudents++;
                addSample(newStudentSamples, studentLabel(entry.getValue()));
                continue;
            }
            existingStudentCount++;
            if (studentChanged(entry.getValue(), existing)) {
                changedStudents++;
                addSample(changedStudentSamples, studentLabel(entry.getValue()));
            }
            Set<String> activeClassSet = activeClassesByStudent.getOrDefault(entry.getKey(), Set.of());
            Set<String> parsedClassSet = parsedClassesByStudent.getOrDefault(entry.getKey(), Set.of());
            if (!activeClassSet.isEmpty() && !activeClassSet.equals(parsedClassSet)) {
                movedStudents++;
                addSample(movedStudentSamples, studentLabel(entry.getValue()) + "：" + String.join("/", activeClassSet) + " -> " + String.join("/", parsedClassSet));
            }
        }

        int newClasses = 0;
        List<String> newClassSamples = new ArrayList<>();
        for (String classCode : parsedClassCodes) {
            if (!existingClassCodes.contains(classCode)) {
                newClasses++;
                addSample(newClassSamples, classCode);
            }
        }

        int newEnrollments = 0;
        int reactivatedEnrollments = 0;
        int unchangedEnrollments = 0;
        List<String> newEnrollmentSamples = new ArrayList<>();
        for (String key : parsedEnrollmentKeys) {
            StoredEnrollment existing = existingEnrollments.get(key);
            if (existing == null) {
                newEnrollments++;
                ParsedStudentEnrollment enrollment = parsedEnrollmentByKey(enrollments, key);
                addSample(newEnrollmentSamples, enrollment == null ? key.replace("|", " -> ") : studentLabel(enrollment) + " -> " + enrollment.classCode());
            } else if (existing.active()) {
                unchangedEnrollments++;
            } else {
                reactivatedEnrollments++;
            }
        }

        int deactivatedEnrollments = 0;
        Set<String> studentsWithActiveAssignments = new HashSet<>();
        List<String> deactivatedEnrollmentSamples = new ArrayList<>();
        for (Map.Entry<String, StoredEnrollment> entry : existingEnrollments.entrySet()) {
            StoredEnrollment existing = entry.getValue();
            if (!existing.active() || !parsedClassCodes.contains(existing.classCode())) {
                continue;
            }
            studentsWithActiveAssignments.add(existing.studentNo());
            if (!parsedEnrollmentKeys.contains(entry.getKey())) {
                deactivatedEnrollments++;
                addSample(deactivatedEnrollmentSamples, existing.studentNo() + " -> " + existing.classCode());
            }
        }

        int studentsLosingAssignments = 0;
        List<String> studentsLosingAssignmentSamples = new ArrayList<>();
        for (String studentNo : studentsWithActiveAssignments) {
            if (!parsedStudents.containsKey(studentNo)) {
                studentsLosingAssignments++;
                addSample(studentsLosingAssignmentSamples, studentNo);
            }
        }

        String message = "學生與分班差異已整理；本次只會更新 Excel 內出現的班級，其他班級維持原狀。";
        return new SyllabusRosterDiff(false, newStudents, existingStudentCount, changedStudents, movedStudents,
                studentsLosingAssignments, newClasses, parsedClassCodes.size() - newClasses, newEnrollments,
                reactivatedEnrollments, unchangedEnrollments, deactivatedEnrollments,
                newStudentSamples, changedStudentSamples, movedStudentSamples, newClassSamples,
                newEnrollmentSamples, deactivatedEnrollmentSamples, studentsLosingAssignmentSamples, message);
    }

    private void addSample(List<String> samples, String value) {
        if (value != null && !value.isBlank() && samples.size() < 8) {
            samples.add(value);
        }
    }

    private String studentLabel(ParsedStudentEnrollment enrollment) {
        String english = enrollment.englishName() == null || enrollment.englishName().isBlank()
                ? ""
                : " " + enrollment.englishName();
        return enrollment.chineseName() + english + " / " + enrollment.studentNo();
    }

    private ParsedStudentEnrollment parsedEnrollmentByKey(List<ParsedStudentEnrollment> enrollments, String key) {
        for (ParsedStudentEnrollment enrollment : enrollments) {
            if ((enrollment.studentNo() + "|" + enrollment.classCode()).equals(key)) {
                return enrollment;
            }
        }
        return null;
    }

    private void deactivateReadingAssignmentEnrollments(Set<String> classCodes) {
        if (classCodes.isEmpty()) {
            return;
        }
        String placeholders = "?,".repeat(classCodes.size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        jdbcTemplate.update("""
                        update class_enrollments
                        set active = false
                        where class_id in (
                            select id
                            from classes
                            where code in (%s)
                        )
                          and student_id in (
                            select id
                            from students
                            where import_source = 'READING_ASSIGNMENT'
                        )
                        """.formatted(placeholders), classCodes.toArray());
    }

    private Long upsertClass(String classCode) {
        List<Long> classIds = jdbcTemplate.query("""
                        select id
                        from classes
                        where code = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                classCode);

        if (!classIds.isEmpty()) {
            Long classId = classIds.get(0);
            jdbcTemplate.update("""
                            update classes
                            set name = ?, category = ?, status = 'ACTIVE', updated_at = current_timestamp
                            where id = ?
                            """,
                    classCode + " 班",
                    classCategory(classCode),
                    classId);
            return classId;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            insert into classes (code, name, category, status)
                            values (?, ?, ?, 'ACTIVE')
                            """,
                    new String[]{"id"});
            statement.setString(1, classCode);
            statement.setString(2, classCode + " 班");
            statement.setString(3, classCategory(classCode));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "班級名單建立失敗。");
        }
        return key.longValue();
    }

    private Long upsertStudent(ParsedStudentEnrollment enrollment) {
        List<Long> studentIds = jdbcTemplate.query("""
                        select id
                        from students
                        where student_no = ?
                        """,
                (rs, rowNum) -> rs.getLong("id"),
                enrollment.studentNo());

        if (!studentIds.isEmpty()) {
            Long studentId = studentIds.get(0);
            jdbcTemplate.update("""
                            update students
                            set chinese_name = ?, english_name = ?, school = ?, grade_level = ?,
                                import_source = 'READING_ASSIGNMENT', active = true, updated_at = current_timestamp
                            where id = ?
                            """,
                    enrollment.chineseName(),
                    blankToNull(enrollment.englishName()),
                    blankToNull(enrollment.school()),
                    blankToNull(enrollment.gradeLevel()),
                    studentId);
            return studentId;
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                            insert into students (
                                student_no, chinese_name, english_name, school, grade_level, import_source, active
                            )
                            values (?, ?, ?, ?, ?, 'READING_ASSIGNMENT', true)
                            """,
                    new String[]{"id"});
            statement.setString(1, enrollment.studentNo());
            statement.setString(2, enrollment.chineseName());
            statement.setString(3, blankToNull(enrollment.englishName()));
            statement.setString(4, blankToNull(enrollment.school()));
            statement.setString(5, blankToNull(enrollment.gradeLevel()));
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "學生名單建立失敗。");
        }
        return key.longValue();
    }

    private void upsertClassEnrollment(Long classId, Long studentId) {
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

    private String classCategory(String classCode) {
        String normalized = classCode.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("GEPT")) {
            return "GEPT";
        }
        if (classCode.startsWith("國")) {
            return "JUNIOR";
        }
        if (classCode.startsWith("高")) {
            return "SENIOR";
        }
        if (normalized.startsWith("K")) {
            return "KINDERGARTEN";
        }
        return "ENGLISH";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, StoredStudent> loadExistingStudents() {
        Map<String, StoredStudent> students = new HashMap<>();
        jdbcTemplate.query("""
                        select student_no, chinese_name, english_name, school, grade_level
                        from students
                        """,
                rs -> {
                    students.put(rs.getString("student_no"), new StoredStudent(
                            rs.getString("student_no"),
                            rs.getString("chinese_name"),
                            rs.getString("english_name"),
                            rs.getString("school"),
                            rs.getString("grade_level")
                    ));
                });
        return students;
    }

    private Set<String> loadExistingClassCodes() {
        return new HashSet<>(jdbcTemplate.query("""
                        select code
                        from classes
                        """,
                (rs, rowNum) -> rs.getString("code")));
    }

    private Map<String, StoredEnrollment> loadExistingReadingAssignmentEnrollments() {
        Map<String, StoredEnrollment> enrollments = new HashMap<>();
        jdbcTemplate.query("""
                        select s.student_no, c.code as class_code, ce.active
                        from class_enrollments ce
                        join students s on s.id = ce.student_id
                        join classes c on c.id = ce.class_id
                        where s.import_source = 'READING_ASSIGNMENT'
                        """,
                rs -> {
                    StoredEnrollment enrollment = new StoredEnrollment(
                            rs.getString("student_no"),
                            rs.getString("class_code"),
                            rs.getBoolean("active")
                    );
                    enrollments.put(enrollment.studentNo() + "|" + enrollment.classCode(), enrollment);
                });
        return enrollments;
    }

    private boolean studentChanged(ParsedStudentEnrollment parsed, StoredStudent stored) {
        return !Objects.equals(normalize(parsed.chineseName()), normalize(stored.chineseName()))
                || !Objects.equals(normalize(parsed.englishName()), normalize(stored.englishName()))
                || !Objects.equals(normalize(parsed.school()), normalize(stored.school()))
                || !Objects.equals(normalize(parsed.gradeLevel()), normalize(stored.gradeLevel()));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private int countActiveStudents() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from students where active = true", Integer.class);
        return count == null ? 0 : count;
    }

    private int countActiveClasses() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from classes where status = 'ACTIVE'", Integer.class);
        return count == null ? 0 : count;
    }

    private int countActiveEnrollments() {
        Integer count = jdbcTemplate.queryForObject("select count(*) from class_enrollments where active = true", Integer.class);
        return count == null ? 0 : count;
    }

    private StoredBatch mapStoredBatch(ResultSet rs) throws SQLException {
        return new StoredBatch(
                rs.getLong("id"),
                rs.getString("original_filename"),
                rs.getString("sheet_name"),
                rs.getInt("total_lessons"),
                rs.getInt("approved_count"),
                rs.getInt("draft_count"),
                rs.getInt("review_count"),
                rs.getBoolean("sync_roster"),
                rs.getInt("roster_student_count"),
                rs.getInt("roster_class_count"),
                rs.getInt("roster_enrollment_count"),
                rs.getInt("warning_count"),
                rs.getString("warnings_text"),
                rs.getBoolean("active"),
                rs.getString("status")
        );
    }

    private record RosterImportSummary(
            boolean requested,
            int studentCount,
            int classCount,
            int enrollmentCount,
            String message
    ) {
        static RosterImportSummary skipped() {
            return new RosterImportSummary(false, 0, 0, 0,
                    "本次只匯入課程進度，學生資料、班級與分班沒有變更。");
        }

        static RosterImportSummary completed(int studentCount, int classCount, int enrollmentCount) {
            String message = studentCount > 0
                    ? "本次已同步 Excel 內出現的學生、班級與分班；其他既有班級維持原狀。"
                    : "已勾選同步學生與分班，但 Excel 沒有解析到學生名單。";
            return new RosterImportSummary(true, studentCount, classCount, enrollmentCount, message);
        }
    }

    private List<String> splitWarnings(String warningsText) {
        if (warningsText == null || warningsText.isBlank()) {
            return List.of();
        }
        return Arrays.stream(warningsText.split("\\R"))
                .filter(warning -> !warning.isBlank())
                .toList();
    }

    private String timestampString(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_DISPLAY_FORMATTER);
    }

    private String sqlDateString(Date date) {
        return date == null ? "" : date.toLocalDate().toString();
    }

    private String dateRange(Date firstDate, Date lastDate) {
        if (firstDate == null || lastDate == null) {
            return "尚未建立日期範圍";
        }
        String first = compactDate(firstDate.toLocalDate());
        String last = compactDate(lastDate.toLocalDate());
        if (Objects.equals(first, last)) {
            return first;
        }
        return first + " 到 " + last;
    }

    private String compactDate(LocalDate date) {
        return date == null ? "" : date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private String safeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unknown.xlsx";
        }
        return originalFilename.replace("\\", "/").substring(originalFilename.replace("\\", "/").lastIndexOf('/') + 1);
    }

    private record StoredStudent(
            String studentNo,
            String chineseName,
            String englishName,
            String school,
            String gradeLevel
    ) {
    }

    private record StoredEnrollment(
            String studentNo,
            String classCode,
            boolean active
    ) {
    }

    private record StoredBatch(
            Long id,
            String fileName,
            String sheetName,
            int totalLessons,
            int approvedCount,
            int draftCount,
            int reviewCount,
            boolean syncRoster,
            int rosterStudentCount,
            int rosterClassCount,
            int rosterEnrollmentCount,
            int warningCount,
            String warningsText,
            boolean active,
            String status
    ) {
    }
}
