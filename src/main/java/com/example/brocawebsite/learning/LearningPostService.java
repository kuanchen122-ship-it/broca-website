package com.example.brocawebsite.learning;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
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
class LearningPostService {

    private static final Set<String> VALID_STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final Set<String> VALID_CATEGORIES = Set.of("GENERAL", "VOCABULARY", "PHONICS", "GRAMMAR", "READING", "EXAM");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final ScheduleLearningContentParser scheduleContentParser;

    LearningPostService(JdbcTemplate jdbcTemplate, ScheduleLearningContentParser scheduleContentParser) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleContentParser = scheduleContentParser;
    }

    LearningAdminResponse adminPosts(String date, String status, String classLabel) {
        LocalDate lessonDate = parseOptionalDate(date);
        String normalizedStatus = normalizeStatusFilter(status);
        String classKeyword = isBlank(classLabel) ? null : "%" + classLabel.trim().toLowerCase(Locale.ROOT) + "%";

        List<LearningAdminPost> posts = jdbcTemplate.query("""
                        select lp.id, lp.lesson_date, lp.class_label, lp.category, lp.title,
                               lp.vocabulary_text, lp.sentence_pattern, lp.homework_note, lp.teacher_note,
                               lp.status, lp.pinned, lp.published_at,
                               creator.display_name as created_by_name,
                               updater.display_name as updated_by_name,
                               lp.created_at, lp.updated_at
                        from learning_posts lp
                        left join users creator on creator.id = lp.created_by
                        left join users updater on updater.id = lp.updated_by
                        where (? is null or lp.lesson_date = ?)
                          and (? is null or lp.status = ?)
                          and (? is null or lower(lp.class_label) like ?)
                        order by lp.lesson_date desc, lp.pinned desc, lp.updated_at desc, lp.id desc
                        limit 120
                        """,
                (rs, rowNum) -> adminPost(rs),
                lessonDate,
                lessonDate,
                normalizedStatus,
                normalizedStatus,
                classKeyword,
                classKeyword);

        return new LearningAdminResponse(summary(), classOptions(), posts);
    }

    LearningPublicResponse publicPosts(String date, String classLabel) {
        LocalDate requestedDate = parseOptionalDate(date);
        if (requestedDate == null) {
            requestedDate = LocalDate.now();
        }
        String classKeyword = isBlank(classLabel) ? null : classLabel.trim().toLowerCase(Locale.ROOT);

        List<LearningPublicPost> posts = combinedPublicPostsForDate(requestedDate, classKeyword);
        LocalDate displayDate = requestedDate;
        boolean fallback = false;

        if (posts.isEmpty()) {
            displayDate = null;
            for (LocalDate candidate : availableDatesOnOrBefore(requestedDate, classKeyword)) {
                if (candidate.equals(requestedDate)) continue;
                List<LearningPublicPost> candidatePosts = combinedPublicPostsForDate(candidate, classKeyword);
                if (!candidatePosts.isEmpty()) {
                    displayDate = candidate;
                    posts = candidatePosts;
                    fallback = true;
                    break;
                }
            }
        }

        boolean includesApprovedSchedule = posts.stream().anyMatch(LearningPublicPost::scheduleDerived);

        return new LearningPublicResponse(
                requestedDate.format(DATE_FORMATTER),
                displayDate == null ? requestedDate.format(DATE_FORMATTER) : displayDate.format(DATE_FORMATTER),
                fallback,
                includesApprovedSchedule,
                publicClassOptions(),
                posts);
    }

    @Transactional
    LearningAdminPost create(LearningPostRequest request, String username) {
        LearningPostDraft draft = draftFrom(request);
        Long userId = findUserId(username);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                            insert into learning_posts (
                                lesson_date, class_label, category, title,
                                vocabulary_text, sentence_pattern, homework_note, teacher_note,
                                status, pinned, published_at,
                                created_by, updated_by, created_at, updated_at
                            )
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                            """,
                    new String[]{"id"});
            statement.setObject(1, draft.lessonDate());
            statement.setString(2, draft.classLabel());
            statement.setString(3, draft.category());
            statement.setString(4, draft.title());
            statement.setString(5, draft.vocabularyText());
            statement.setString(6, draft.sentencePattern());
            statement.setString(7, draft.homeworkNote());
            statement.setString(8, draft.teacherNote());
            statement.setString(9, draft.status());
            statement.setBoolean(10, draft.pinned());
            statement.setTimestamp(11, "PUBLISHED".equals(draft.status()) ? new Timestamp(System.currentTimeMillis()) : null);
            statement.setObject(12, userId);
            statement.setObject(13, userId);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "學習內容儲存失敗，請稍後再試。");
        }
        return adminPostById(key.longValue());
    }

    @Transactional
    LearningAdminPost update(Long postId, LearningPostRequest request, String username) {
        requireExisting(postId);
        LearningPostDraft draft = draftFrom(request);
        Long userId = findUserId(username);

        jdbcTemplate.update("""
                        update learning_posts
                        set lesson_date = ?,
                            class_label = ?,
                            category = ?,
                            title = ?,
                            vocabulary_text = ?,
                            sentence_pattern = ?,
                            homework_note = ?,
                            teacher_note = ?,
                            status = ?,
                            pinned = ?,
                            published_at = case
                                when ? = 'PUBLISHED' and published_at is null then current_timestamp
                                when ? <> 'PUBLISHED' then null
                                else published_at
                            end,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                draft.lessonDate(),
                draft.classLabel(),
                draft.category(),
                draft.title(),
                draft.vocabularyText(),
                draft.sentencePattern(),
                draft.homeworkNote(),
                draft.teacherNote(),
                draft.status(),
                draft.pinned(),
                draft.status(),
                draft.status(),
                userId,
                postId);

        return adminPostById(postId);
    }

    @Transactional
    LearningAdminPost updateStatus(Long postId, String status, String username) {
        requireExisting(postId);
        String normalizedStatus = normalizeStatus(status);
        Long userId = findUserId(username);

        jdbcTemplate.update("""
                        update learning_posts
                        set status = ?,
                            published_at = case
                                when ? = 'PUBLISHED' and published_at is null then current_timestamp
                                when ? <> 'PUBLISHED' then null
                                else published_at
                            end,
                            updated_by = ?,
                            updated_at = current_timestamp
                        where id = ?
                        """,
                normalizedStatus,
                normalizedStatus,
                normalizedStatus,
                userId,
                postId);

        return adminPostById(postId);
    }

    @Transactional
    LearningDeleteResponse delete(Long postId) {
        int deleted = jdbcTemplate.update("delete from learning_posts where id = ?", postId);
        return new LearningDeleteResponse(postId, deleted > 0);
    }

    List<String> classOptions() {
        return jdbcTemplate.query("""
                        select label
                        from (
                            select code as label from classes where code is not null
                            union
                            select class_label as label from learning_posts where class_label is not null
                        ) options
                        where label is not null and trim(label) <> ''
                        order by label
                        """,
                (rs, rowNum) -> rs.getString("label"));
    }

    private List<String> publicClassOptions() {
        return jdbcTemplate.query("""
                        select label
                        from (
                            select distinct class_label as label
                            from learning_posts
                            where status = 'PUBLISHED'
                            union
                            select distinct lp.class_label as label
                            from lesson_plans lp
                            where lp.import_batch_id = coalesce(
                                (select max(id) from syllabus_import_batches where active = true and status = 'IMPORTED'),
                                (select max(id) from syllabus_import_batches where status = 'IMPORTED')
                            )
                              and lp.approval_status = 'DIRECTOR_APPROVED'
                        ) public_classes
                        where label is not null and trim(label) <> ''
                        order by label
                        """,
                (rs, rowNum) -> rs.getString("label"));
    }

    private List<LearningPublicPost> combinedPublicPostsForDate(LocalDate date, String classLabel) {
        List<LearningPublicPost> posts = new ArrayList<>(publicPostsForDate(date, classLabel));
        Set<String> manuallyPublishedClasses = new LinkedHashSet<>();
        posts.forEach(post -> manuallyPublishedClasses.add(post.classLabel().toLowerCase(Locale.ROOT)));

        approvedSchedulePostsForDate(date, classLabel).stream()
                .filter(post -> !manuallyPublishedClasses.contains(post.classLabel().toLowerCase(Locale.ROOT)))
                .forEach(posts::add);

        posts.sort(Comparator
                .comparing(LearningPublicPost::pinned).reversed()
                .thenComparing(LearningPublicPost::classLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(LearningPublicPost::id));
        return posts;
    }

    private List<LearningPublicPost> publicPostsForDate(LocalDate date, String classLabel) {
        return jdbcTemplate.query("""
                        select id, lesson_date, class_label, category, title,
                               vocabulary_text, sentence_pattern, homework_note, pinned, published_at
                        from learning_posts
                        where status = 'PUBLISHED'
                          and lesson_date = ?
                          and (? is null or lower(class_label) = ?)
                        order by pinned desc, class_label, published_at desc, id desc
                        """,
                (rs, rowNum) -> publicPost(rs),
                date,
                classLabel,
                classLabel);
    }

    private List<LearningPublicPost> approvedSchedulePostsForDate(LocalDate date, String classLabel) {
        List<LearningPublicPost> posts = jdbcTemplate.query("""
                        select lp.id, lp.lesson_date, lp.class_label, lp.content
                        from lesson_plans lp
                        where lp.import_batch_id = coalesce(
                            (select max(id) from syllabus_import_batches where active = true and status = 'IMPORTED'),
                            (select max(id) from syllabus_import_batches where status = 'IMPORTED')
                        )
                          and lp.approval_status = 'DIRECTOR_APPROVED'
                          and lp.lesson_date = ?
                          and (? is null or lower(lp.class_label) = ?)
                        order by lp.class_label, lp.source_row_number, lp.source_column_label, lp.id
                        """,
                (rs, rowNum) -> {
                    String label = rs.getString("class_label");
                    var parsed = scheduleContentParser.parse(label, rs.getString("content"));
                    if (!parsed.hasPublicContent()) return null;
                    return new LearningPublicPost(
                            rs.getLong("id"),
                            rs.getDate("lesson_date").toLocalDate().format(DATE_FORMATTER),
                            label,
                            parsed.category(),
                            categoryLabel(parsed.category()),
                            parsed.title(),
                            parseVocabulary(parsed.vocabularyText()),
                            parsed.sentencePattern(),
                            parsed.homeworkNote(),
                            false,
                            "",
                            true);
                },
                date,
                classLabel,
                classLabel);
        return posts.stream().filter(post -> post != null).toList();
    }

    private List<LocalDate> availableDatesOnOrBefore(LocalDate date, String classLabel) {
        return jdbcTemplate.query("""
                        select available_date
                        from (
                            select distinct lesson_date as available_date
                            from learning_posts
                            where status = 'PUBLISHED'
                              and (? is null or lower(class_label) = ?)
                            union
                            select distinct lp.lesson_date as available_date
                            from lesson_plans lp
                            where lp.import_batch_id = coalesce(
                                (select max(id) from syllabus_import_batches where active = true and status = 'IMPORTED'),
                                (select max(id) from syllabus_import_batches where status = 'IMPORTED')
                            )
                              and lp.approval_status = 'DIRECTOR_APPROVED'
                              and (? is null or lower(lp.class_label) = ?)
                        ) available_learning_dates
                        where available_date <= ?
                        order by available_date desc
                        limit 60
                        """,
                (rs, rowNum) -> rs.getDate("available_date").toLocalDate(),
                classLabel,
                classLabel,
                classLabel,
                classLabel,
                date);
    }

    private LearningAdminPost adminPostById(Long id) {
        return jdbcTemplate.query("""
                        select lp.id, lp.lesson_date, lp.class_label, lp.category, lp.title,
                               lp.vocabulary_text, lp.sentence_pattern, lp.homework_note, lp.teacher_note,
                               lp.status, lp.pinned, lp.published_at,
                               creator.display_name as created_by_name,
                               updater.display_name as updated_by_name,
                               lp.created_at, lp.updated_at
                        from learning_posts lp
                        left join users creator on creator.id = lp.created_by
                        left join users updater on updater.id = lp.updated_by
                        where lp.id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到學習內容。");
                    }
                    return adminPost(rs);
                },
                id);
    }

    private LearningPostSummary summary() {
        return new LearningPostSummary(
                count("select count(*) from learning_posts where status = 'DRAFT'"),
                count("select count(*) from learning_posts where status = 'PUBLISHED'"),
                count("select count(*) from learning_posts where status = 'ARCHIVED'"),
                count("select count(*) from learning_posts where status = 'PUBLISHED' and lesson_date = current_date"),
                latestPublishedDateString()
        );
    }

    private String latestPublishedDateString() {
        List<String> dates = jdbcTemplate.query("""
                        select max(lesson_date) as latest_date
                        from learning_posts
                        where status = 'PUBLISHED'
                        """,
                (rs, rowNum) -> {
                    var sqlDate = rs.getDate("latest_date");
                    return sqlDate == null ? null : sqlDate.toLocalDate().format(DATE_FORMATTER);
                });
        return dates.isEmpty() ? null : dates.get(0);
    }

    private LearningAdminPost adminPost(ResultSet rs) throws SQLException {
        String vocabularyText = rs.getString("vocabulary_text");
        String category = rs.getString("category");
        String status = rs.getString("status");
        return new LearningAdminPost(
                rs.getLong("id"),
                rs.getDate("lesson_date").toLocalDate().format(DATE_FORMATTER),
                rs.getString("class_label"),
                category,
                categoryLabel(category),
                rs.getString("title"),
                nullToEmpty(vocabularyText),
                parseVocabulary(vocabularyText),
                nullToEmpty(rs.getString("sentence_pattern")),
                nullToEmpty(rs.getString("homework_note")),
                nullToEmpty(rs.getString("teacher_note")),
                status,
                statusLabel(status),
                rs.getBoolean("pinned"),
                formatTimestamp(rs.getTimestamp("published_at")),
                rs.getString("created_by_name"),
                rs.getString("updated_by_name"),
                formatTimestamp(rs.getTimestamp("created_at")),
                formatTimestamp(rs.getTimestamp("updated_at"))
        );
    }

    private LearningPublicPost publicPost(ResultSet rs) throws SQLException {
        String vocabularyText = rs.getString("vocabulary_text");
        String category = rs.getString("category");
        return new LearningPublicPost(
                rs.getLong("id"),
                rs.getDate("lesson_date").toLocalDate().format(DATE_FORMATTER),
                rs.getString("class_label"),
                category,
                categoryLabel(category),
                rs.getString("title"),
                parseVocabulary(vocabularyText),
                nullToEmpty(rs.getString("sentence_pattern")),
                nullToEmpty(rs.getString("homework_note")),
                rs.getBoolean("pinned"),
                formatTimestamp(rs.getTimestamp("published_at")),
                false
        );
    }

    private LearningPostDraft draftFrom(LearningPostRequest request) {
        if (request == null) {
            throw badRequest("請輸入學習內容。");
        }
        LocalDate lessonDate = parseRequiredDate(request.lessonDate());
        String classLabel = required(request.classLabel(), "班級");
        String title = required(request.title(), "標題");
        String category = normalizeCategory(request.category());
        String status = normalizeStatus(request.status());
        String vocabularyText = trimToLength(request.vocabularyText(), 5000);
        String sentencePattern = trimToLength(request.sentencePattern(), 2000);
        String homeworkNote = trimToLength(request.homeworkNote(), 2000);
        String teacherNote = trimToLength(request.teacherNote(), 2000);

        if (isBlank(vocabularyText) && isBlank(sentencePattern) && isBlank(homeworkNote)) {
            throw badRequest("請至少輸入今日單字、句型或作業提醒。");
        }

        return new LearningPostDraft(
                lessonDate,
                classLabel,
                category,
                title,
                vocabularyText,
                sentencePattern,
                homeworkNote,
                teacherNote,
                status,
                Boolean.TRUE.equals(request.pinned())
        );
    }

    private List<LearningVocabularyItem> parseVocabulary(String value) {
        if (isBlank(value)) return List.of();

        List<LearningVocabularyItem> items = new ArrayList<>();
        String[] lines = value.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) continue;
            line = line.replaceFirst("^[\\-•*\\d.、\\s]+", "").trim();
            if (line.isBlank()) continue;

            String[] cells = line.split("\\t+");
            if (cells.length == 1) {
                cells = line.split("\\s{2,}");
            }

            String word = cell(cells, 0);
            String meaning = cell(cells, 1);
            String example = joinCells(cells, 2);

            if (meaning.isBlank() && word.contains(" - ")) {
                String[] parts = word.split("\\s+-\\s+", 3);
                word = cell(parts, 0);
                meaning = cell(parts, 1);
                example = cell(parts, 2);
            }

            if (meaning.isBlank() && (word.contains("：") || word.contains(":"))) {
                String[] parts = word.split("\\s*[：:]\\s*", 2);
                word = cell(parts, 0);
                meaning = cell(parts, 1);
            }

            items.add(new LearningVocabularyItem(word, meaning, example));
            if (items.size() >= 80) break;
        }
        return items;
    }

    private String cell(String[] cells, int index) {
        return cells != null && index < cells.length && cells[index] != null ? cells[index].trim() : "";
    }

    private String joinCells(String[] cells, int start) {
        if (cells == null || cells.length <= start) return "";
        List<String> values = new ArrayList<>();
        for (int i = start; i < cells.length; i++) {
            if (cells[i] != null && !cells[i].trim().isBlank()) values.add(cells[i].trim());
        }
        return String.join(" ", values);
    }

    private void requireExisting(Long id) {
        if (id == null || count("select count(*) from learning_posts where id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "找不到學習內容。");
        }
    }

    private LocalDate parseRequiredDate(String value) {
        LocalDate date = parseOptionalDate(value);
        if (date == null) {
            throw badRequest("請選擇日期。");
        }
        return date;
    }

    private LocalDate parseOptionalDate(String value) {
        if (isBlank(value)) return null;
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw badRequest("日期格式需為 yyyy-MM-dd。");
        }
    }

    private String normalizeStatusFilter(String value) {
        if (isBlank(value) || "ALL".equalsIgnoreCase(value)) return null;
        return normalizeStatus(value);
    }

    private String normalizeStatus(String value) {
        String normalized = isBlank(value) ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw badRequest("狀態不正確。");
        }
        return normalized;
    }

    private String normalizeCategory(String value) {
        String normalized = isBlank(value) ? "GENERAL" : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_CATEGORIES.contains(normalized)) {
            throw badRequest("分類不正確。");
        }
        return normalized;
    }

    private String categoryLabel(String value) {
        return switch (value == null ? "" : value) {
            case "VOCABULARY" -> "今日單字";
            case "PHONICS" -> "發音練習";
            case "GRAMMAR" -> "句型文法";
            case "READING" -> "閱讀進度";
            case "EXAM" -> "測驗準備";
            default -> "一般學習";
        };
    }

    private String statusLabel(String value) {
        return switch (value == null ? "" : value) {
            case "PUBLISHED" -> "已發布";
            case "ARCHIVED" -> "已封存";
            default -> "草稿";
        };
    }

    private String required(String value, String label) {
        String trimmed = trimToLength(value, 160);
        if (trimmed.isBlank()) {
            throw badRequest("請輸入" + label + "。");
        }
        return trimmed;
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String formatTimestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private int count(String sql, Object... args) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }

    private Long findUserId(String username) {
        if (isBlank(username)) return null;
        return jdbcTemplate.query("""
                        select id
                        from users
                        where username = ?
                        """,
                rs -> rs.next() ? rs.getLong("id") : null,
                username);
    }

    private record LearningPostDraft(
            LocalDate lessonDate,
            String classLabel,
            String category,
            String title,
            String vocabularyText,
            String sentencePattern,
            String homeworkNote,
            String teacherNote,
            String status,
            boolean pinned
    ) {
    }
}
