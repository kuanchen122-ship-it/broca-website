package com.example.brocawebsite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class BootstrapUserConfig {

    @Bean
    ApplicationRunner bootstrapUsers(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            @Value("${broca.bootstrap.admin-username:director}") String adminUsername,
            @Value("${broca.bootstrap.admin-password:}") String adminPassword,
            @Value("${broca.bootstrap.teacher-username:teacher}") String teacherUsername,
            @Value("${broca.bootstrap.teacher-password:}") String teacherPassword) {
        return args -> {
            upsertUser(jdbcTemplate, passwordEncoder, adminUsername, adminPassword, "教務主任", "ADMIN");
            upsertUser(jdbcTemplate, passwordEncoder, teacherUsername, teacherPassword, "授課老師", "TEACHER");
        };
    }

    private void upsertUser(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            String username,
            String password,
            String displayName,
            String role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }

        String normalizedUsername = username.trim();
        String encodedPassword = passwordEncoder.encode(password);
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from users where username = ?",
                Integer.class,
                normalizedUsername);

        if (count != null && count > 0) {
            jdbcTemplate.update("""
                            update users
                            set password_hash = ?, display_name = ?, role = ?, enabled = true,
                                updated_at = current_timestamp
                            where username = ?
                            """,
                    encodedPassword, displayName, role, normalizedUsername);
            return;
        }

        jdbcTemplate.update("""
                        insert into users (username, password_hash, display_name, role, enabled)
                        values (?, ?, ?, ?, true)
                        """,
                normalizedUsername, encodedPassword, displayName, role);
    }
}
