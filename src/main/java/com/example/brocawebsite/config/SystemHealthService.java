package com.example.brocawebsite.config;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class SystemHealthService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN);

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final Environment environment;
    private final String applicationName;
    private final String channelAccessToken;
    private final String channelSecret;
    private final String liffId;
    private final boolean lineSendingEnabled;

    SystemHealthService(
            JdbcTemplate jdbcTemplate,
            DataSource dataSource,
            Environment environment,
            @Value("${spring.application.name:broca-website}") String applicationName,
            @Value("${line.messaging.channel-access-token:}") String channelAccessToken,
            @Value("${line.messaging.channel-secret:}") String channelSecret,
            @Value("${line.liff.rollcall-id:}") String liffId,
            @Value("${line.messaging.sending-enabled:false}") boolean lineSendingEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.environment = environment;
        this.applicationName = applicationName;
        this.channelAccessToken = channelAccessToken;
        this.channelSecret = channelSecret;
        this.liffId = liffId;
        this.lineSendingEnabled = lineSendingEnabled;
    }

    SystemHealthResponse health() {
        DatabaseInfo databaseInfo = databaseInfo();
        SystemHealthSummary summary = summary();
        SystemHealthLineStatus line = lineStatus(summary.activeStudents());
        SystemHealthImportStatus currentImport = currentImport();
        SystemHealthRuntimeStatus runtime = runtimeStatus(databaseInfo);
        List<SystemHealthCheck> checks = checks(databaseInfo, summary, line, currentImport);
        int attentionCount = (int) checks.stream()
                .filter(check -> !"READY".equals(check.status()))
                .count();
        int readyCount = checks.size() - attentionCount;
        String overallStatus = attentionCount == 0 ? "READY"
                : checks.stream().anyMatch(check -> "ACTION".equals(check.status())) ? "ACTION" : "REVIEW";
        String overallLabel = switch (overallStatus) {
            case "READY" -> "可上線";
            case "ACTION" -> "需處理";
            default -> "可測試，仍有待補";
        };
        return new SystemHealthResponse(
                overallStatus,
                overallLabel,
                readyCount,
                attentionCount,
                runtime,
                summary,
                line,
                currentImport,
                checks
        );
    }

    private SystemHealthRuntimeStatus runtimeStatus(DatabaseInfo databaseInfo) {
        return new SystemHealthRuntimeStatus(
                applicationName,
                LocalDateTime.now().format(DATE_TIME_FORMATTER),
                activeProfiles(),
                databaseInfo.productName(),
                databaseMode(databaseInfo),
                maskDatabaseUrl(databaseInfo.url()),
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                ZoneId.systemDefault().toString()
        );
    }

    private List<SystemHealthCheck> checks(DatabaseInfo databaseInfo, SystemHealthSummary summary,
                                           SystemHealthLineStatus line, SystemHealthImportStatus currentImport) {
        List<SystemHealthCheck> checks = new ArrayList<>();
        checks.add(new SystemHealthCheck(
                "database",
                "資料庫連線",
                databaseInfo.ready() ? "READY" : "ACTION",
                databaseInfo.ready() ? databaseInfo.productName() : "未連線",
                databaseInfo.ready() ? "後端可以正常查詢資料庫。" : databaseInfo.errorMessage(),
                databaseInfo.ready() ? "" : "檢查 MariaDB/H2",
                ""
        ));
        checks.add(new SystemHealthCheck(
                "environment",
                "執行環境",
                databaseInfo.mariaDbLike() ? "READY" : "REVIEW",
                databaseMode(databaseInfo),
                databaseInfo.mariaDbLike()
                        ? "目前看起來是 MariaDB/MySQL 類型資料庫，適合 NAS 正式環境。"
                        : "目前看起來是本機 H2/開發資料庫；明天上 NAS 時要切到 mariadb profile。",
                databaseInfo.mariaDbLike() ? "" : "NAS 前切換設定",
                "admin-system.html"
        ));
        checks.add(new SystemHealthCheck(
                "import",
                "現行課表批次",
                currentImport.hasImport() ? "READY" : "ACTION",
                currentImport.hasImport() ? "#" + currentImport.batchId() : "尚未匯入",
                currentImport.hasImport()
                        ? currentImport.fileName() + "，" + currentImport.totalLessons() + " 筆課程。"
                        : "請先到數位互動課表匯入 Daily Syllabus。",
                currentImport.hasImport() ? "查看課表" : "匯入 Excel",
                "admin-schedule.html"
        ));
        checks.add(new SystemHealthCheck(
                "roster",
                "學生與分班",
                summary.activeStudents() > 0 && summary.activeClasses() > 0 ? "READY" : "ACTION",
                summary.activeStudents() + " 位 / " + summary.activeClasses() + " 班",
                "目前有效分班人次 " + summary.activeEnrollments() + " 筆。",
                "學生中心",
                "admin-students.html"
        ));
        checks.add(new SystemHealthCheck(
                "parentLine",
                "家長 LINE 資料",
                line.activeStudents() == 0 ? "ACTION" : line.missingLineStudents() == 0 ? "READY" : "REVIEW",
                line.coveragePercent() + "%",
                line.linkedStudents() + " 位學生已有 LINE ID，" + line.missingLineStudents() + " 位待補。",
                "補家長資料",
                "admin-contacts.html"
        ));
        checks.add(new SystemHealthCheck(
                "lineCredentials",
                "LINE 推播設定",
                line.sendingEnabled() && line.channelAccessTokenConfigured() && line.channelSecretConfigured() ? "READY" : "DRAFT",
                line.mode(),
                "Token：" + configuredLabel(line.channelAccessTokenConfigured())
                        + "，Secret：" + configuredLabel(line.channelSecretConfigured())
                        + "，LIFF：" + configuredLabel(line.liffConfigured()) + "。",
                "LINE 整合",
                "admin-line.html"
        ));
        checks.add(new SystemHealthCheck(
                "payroll",
                "薪資資料",
                summary.currentMonthPayroll().compareTo(BigDecimal.ZERO) > 0 ? "READY" : "DRAFT",
                formatMoney(summary.currentMonthPayroll()),
                "本月已登錄薪資估算；沒有資料時也不影響課表與點名上線。",
                "薪資管理",
                "admin-payroll.html"
        ));
        checks.add(new SystemHealthCheck(
                "users",
                "登入帳號",
                summary.enabledUsers() > 0 ? "READY" : "ACTION",
                summary.enabledUsers() + " 個啟用帳號",
                "主任與老師登入都依 users 表角色控管。",
                "",
                ""
        ));
        return checks;
    }

    private SystemHealthSummary summary() {
        YearMonth month = YearMonth.now();
        LocalDate start = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        return new SystemHealthSummary(
                count("select count(*) from students where active = true"),
                count("select count(*) from classes where status = 'ACTIVE'"),
                count("select count(*) from class_enrollments where active = true"),
                count("select count(*) from users where enabled = true"),
                count("select count(*) from attendance"),
                count("""
                        select count(*)
                        from attendance
                        where line_notification_status in ('PENDING', 'REQUESTED', 'FAILED')
                        """),
                money("""
                        select coalesce(sum(amount), 0)
                        from payroll_entries
                        where work_date >= ?
                          and work_date < ?
                        """, Date.valueOf(start), Date.valueOf(end))
        );
    }

    private SystemHealthLineStatus lineStatus(int activeStudents) {
        int linked = count("""
                select count(*)
                from students
                where active = true
                  and parent_line_id is not null
                  and trim(parent_line_id) <> ''
                """);
        int missing = count("""
                select count(*)
                from students
                where active = true
                  and (parent_line_id is null or trim(parent_line_id) = '')
                """);
        int coverage = activeStudents == 0 ? 0 : Math.round((linked * 100f) / activeStudents);
        boolean tokenConfigured = hasText(channelAccessToken);
        boolean secretConfigured = hasText(channelSecret);
        boolean liffConfigured = hasText(liffId);
        String mode = lineSendingEnabled && tokenConfigured && secretConfigured
                ? "正式推播"
                : tokenConfigured || secretConfigured || liffConfigured ? "憑證待確認" : "草稿模式";
        return new SystemHealthLineStatus(
                lineSendingEnabled,
                tokenConfigured,
                secretConfigured,
                liffConfigured,
                mode,
                activeStudents,
                linked,
                missing,
                coverage
        );
    }

    private SystemHealthImportStatus currentImport() {
        List<SystemHealthImportStatus> imports = jdbcTemplate.query("""
                        select b.id, b.original_filename, b.sheet_name, b.imported_at, b.total_lessons,
                               b.warning_count, b.sync_roster, b.roster_student_count, b.roster_class_count,
                               b.roster_enrollment_count, r.first_lesson_date, r.last_lesson_date
                        from syllabus_import_batches b
                        left join (
                            select import_batch_id, min(lesson_date) as first_lesson_date,
                                   max(lesson_date) as last_lesson_date
                            from lesson_plans
                            group by import_batch_id
                        ) r on r.import_batch_id = b.id
                        order by b.active desc, coalesce(b.activated_at, b.imported_at) desc,
                                 b.imported_at desc, b.id desc
                        limit 1
                        """,
                (rs, rowNum) -> {
                    Date first = rs.getDate("first_lesson_date");
                    Date last = rs.getDate("last_lesson_date");
                    return new SystemHealthImportStatus(
                            true,
                            rs.getLong("id"),
                            rs.getString("original_filename"),
                            rs.getString("sheet_name"),
                            timestampString(rs.getTimestamp("imported_at")),
                            dateRange(first, last),
                            rs.getInt("total_lessons"),
                            rs.getInt("warning_count"),
                            rs.getBoolean("sync_roster"),
                            rs.getInt("roster_student_count"),
                            rs.getInt("roster_class_count"),
                            rs.getInt("roster_enrollment_count")
                    );
                });
        return imports.isEmpty() ? SystemHealthImportStatus.empty() : imports.get(0);
    }

    private DatabaseInfo databaseInfo() {
        try (Connection connection = dataSource.getConnection()) {
            jdbcTemplate.queryForObject("select 1", Integer.class);
            String productName = connection.getMetaData().getDatabaseProductName();
            String url = connection.getMetaData().getURL();
            return new DatabaseInfo(true, productName == null ? "Unknown" : productName, url == null ? "" : url, "");
        } catch (SQLException | RuntimeException ex) {
            return new DatabaseInfo(false, "Unknown", "", ex.getMessage());
        }
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length == 0) {
            return "default";
        }
        return String.join(", ", profiles);
    }

    private String databaseMode(DatabaseInfo databaseInfo) {
        if (databaseInfo.mariaDbLike()) {
            return "NAS / MariaDB";
        }
        if (databaseInfo.productName().toLowerCase(Locale.ROOT).contains("h2")
                || databaseInfo.url().toLowerCase(Locale.ROOT).contains("h2")) {
            return "本機開發 / H2";
        }
        return "自訂資料庫";
    }

    private String maskDatabaseUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String masked = url.replaceAll("(?i)(password=)[^;&]+", "$1****");
        masked = masked.replaceAll("(?i)(pwd=)[^;&]+", "$1****");
        return masked;
    }

    private String timestampString(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String dateRange(Date firstDate, Date lastDate) {
        if (firstDate == null || lastDate == null) {
            return "尚未建立資料範圍";
        }
        String first = compactDate(firstDate.toLocalDate());
        String last = compactDate(lastDate.toLocalDate());
        if (Objects.equals(first, last)) {
            return first;
        }
        return first + " 到 " + last;
    }

    private String compactDate(LocalDate date) {
        return date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private String configuredLabel(boolean configured) {
        return configured ? "已設定" : "未設定";
    }

    private String formatMoney(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.TAIWAN);
        formatter.setMaximumFractionDigits(0);
        return "NT$" + formatter.format(value == null ? BigDecimal.ZERO : value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DatabaseInfo(
            boolean ready,
            String productName,
            String url,
            String errorMessage
    ) {
        boolean mariaDbLike() {
            String text = (productName + " " + url).toLowerCase(Locale.ROOT);
            return text.contains("mariadb") || text.contains("mysql");
        }
    }
}
