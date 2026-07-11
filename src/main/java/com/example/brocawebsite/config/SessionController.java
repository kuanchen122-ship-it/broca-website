package com.example.brocawebsite.config;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/session")
class SessionController {

    private final JdbcTemplate jdbcTemplate;

    SessionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/me")
    CurrentUserResponse me(Authentication authentication) {
        String username = authentication.getName();
        List<CurrentUserResponse> users = jdbcTemplate.query("""
                        select username, display_name, role
                        from users
                        where username = ?
                        """,
                (rs, rowNum) -> new CurrentUserResponse(
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role")),
                username);

        if (users.isEmpty()) {
            return new CurrentUserResponse(username, username, "");
        }
        return users.get(0);
    }
}
