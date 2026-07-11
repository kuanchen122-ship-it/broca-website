package com.example.brocawebsite.line;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class LineIntegrationService {

    private final JdbcTemplate jdbcTemplate;
    private final String channelAccessToken;
    private final String channelSecret;
    private final String liffId;
    private final boolean sendingEnabled;

    LineIntegrationService(
            JdbcTemplate jdbcTemplate,
            @Value("${line.messaging.channel-access-token:}") String channelAccessToken,
            @Value("${line.messaging.channel-secret:}") String channelSecret,
            @Value("${line.liff.rollcall-id:}") String liffId,
            @Value("${line.messaging.sending-enabled:false}") boolean sendingEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelAccessToken = channelAccessToken;
        this.channelSecret = channelSecret;
        this.liffId = liffId;
        this.sendingEnabled = sendingEnabled;
    }

    LineIntegrationOverview overview() {
        LineConfigStatus config = configStatus();
        LineAudienceSummary audience = audienceSummary();
        LineAttendanceQueueSummary attendanceQueue = attendanceQueueSummary();

        return new LineIntegrationOverview(
                config,
                audience,
                attendanceQueue,
                workflow(config, audience),
                templates());
    }

    private LineConfigStatus configStatus() {
        boolean tokenReady = hasText(channelAccessToken);
        boolean secretReady = hasText(channelSecret);
        boolean liffReady = hasText(liffId);
        String mode = tokenReady && secretReady && sendingEnabled
                ? "READY_TO_SEND"
                : tokenReady && secretReady ? "READY_FOR_REVIEW" : "DRAFT_ONLY";

        return new LineConfigStatus(
                tokenReady,
                secretReady,
                liffReady,
                sendingEnabled,
                mode,
                "/api/line/webhook",
                "/mobile-rollcall.html");
    }

    private LineAudienceSummary audienceSummary() {
        int activeStudents = count("select count(*) from students where active = true");
        int linkedStudents = count("""
                select count(*)
                from students
                where active = true
                  and parent_line_id is not null
                  and trim(parent_line_id) <> ''
                """);
        int missingLineStudents = count("""
                select count(*)
                from students
                where active = true
                  and (parent_line_id is null or trim(parent_line_id) = '')
                """);
        int readyForInviteStudents = count("""
                select count(*)
                from students
                where active = true
                  and (parent_line_id is null or trim(parent_line_id) = '')
                  and parent_phone is not null
                  and trim(parent_phone) <> ''
                """);
        int distinctParentLineIds = count("""
                select count(distinct parent_line_id)
                from students
                where active = true
                  and parent_line_id is not null
                  and trim(parent_line_id) <> ''
                """);
        int sharedParentLineIds = count("""
                select count(*)
                from (
                    select parent_line_id
                    from students
                    where active = true
                      and parent_line_id is not null
                      and trim(parent_line_id) <> ''
                    group by parent_line_id
                    having count(*) > 1
                ) grouped_parent_lines
                """);
        int coveragePercent = activeStudents == 0 ? 0 : Math.round((linkedStudents * 100f) / activeStudents);

        return new LineAudienceSummary(
                activeStudents,
                linkedStudents,
                missingLineStudents,
                readyForInviteStudents,
                distinctParentLineIds,
                sharedParentLineIds,
                coveragePercent);
    }

    private LineAttendanceQueueSummary attendanceQueueSummary() {
        int total = count("select count(*) from attendance");
        int notConfigured = countStatus("NOT_CONFIGURED");
        int pending = countStatus("PENDING");
        int sent = countStatus("SENT");
        int failed = countStatus("FAILED");
        int notRequested = countStatus("NOT_REQUESTED");
        String latestRecordedAt = latestAttendanceRecordedAt();

        return new LineAttendanceQueueSummary(
                total,
                notConfigured,
                pending,
                sent,
                failed,
                notRequested,
                latestRecordedAt);
    }

    private List<LineWorkflowStep> workflow(LineConfigStatus config, LineAudienceSummary audience) {
        return List.of(
                new LineWorkflowStep(
                        "建立 LINE 官方帳號 Channel",
                        config.channelSecretConfigured() ? "ready" : "pending",
                        "填入 Channel Secret 後，後端才能驗證 webhook 來源。"),
                new LineWorkflowStep(
                        "設定 Messaging API Token",
                        config.channelAccessTokenConfigured() ? "ready" : "pending",
                        "填入 Channel Access Token 後，系統才可正式推播家長通知。"),
                new LineWorkflowStep(
                        "補齊家長 LINE ID",
                        audience.missingLineStudents() == 0 ? "ready" : "action",
                        "家長聯絡資料中心會列出缺 LINE ID 或可邀請綁定的學生。"),
                new LineWorkflowStep(
                        "點名通知轉正式發送",
                        "READY_TO_SEND".equals(config.mode()) ? "ready" : "pending",
                        "Token/Secret、webhook 與後端發送流程確認後，再把 sending-enabled 切成 true。"),
                new LineWorkflowStep(
                        "LIFF 老師手機點名",
                        config.liffIdConfigured() ? "ready" : "pending",
                        "填入 LIFF ID 後，可把手機點名入口掛進 LINE 內部工作流程。"));
    }

    private List<LineMessageTemplate> templates() {
        return List.of(
                new LineMessageTemplate(
                        "arrival",
                        "安全抵達",
                        "您的孩子「{學生姓名}」今日已於 {抵達時間} 安全抵達布魯卡美語。"),
                new LineMessageTemplate(
                        "late",
                        "遲到提醒",
                        "您的孩子「{學生姓名}」今日點名狀態為遲到，抵達時間：{抵達時間}。"),
                new LineMessageTemplate(
                        "absent",
                        "缺席通知",
                        "您的孩子「{學生姓名}」今日尚未完成到班點名，如已請假請忽略此提醒。"),
                new LineMessageTemplate(
                        "leave",
                        "請假確認",
                        "您的孩子「{學生姓名}」今日請假紀錄已建立，感謝您的告知。"));
    }

    private int countStatus(String status) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from attendance where line_notification_status = ?",
                Integer.class,
                status);
        return count == null ? 0 : count;
    }

    private String latestAttendanceRecordedAt() {
        List<Timestamp> timestamps = jdbcTemplate.query("""
                        select max(recorded_at) as latest_recorded_at
                        from attendance
                        """,
                (rs, rowNum) -> rs.getTimestamp("latest_recorded_at"));
        if (timestamps.isEmpty() || timestamps.get(0) == null) {
            return null;
        }
        return timestamps.get(0).toLocalDateTime().toString();
    }

    private int count(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count == null ? 0 : count;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
