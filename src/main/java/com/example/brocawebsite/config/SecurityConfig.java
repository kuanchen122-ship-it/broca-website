package com.example.brocawebsite.config;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/about.html", "/courses.html", "/course-details.html",
                                "/info.html", "/learning.html", "/register.html", "/login.html", "/*.png", "/*.jpg", "/*.jpeg", "/*.svg", "/*.webp",
                                "/*.gif", "/*.avif", "/*.css", "/*.js", "/*.ico", "/assets/**", "/error", "/h2-console/**").permitAll()
                        .requestMatchers("/api/public/registration-requests/**").permitAll()
                        .requestMatchers("/api/public/learning-posts/**").permitAll()
                        .requestMatchers("/admin-dashboard.html", "/admin-leave.html", "/admin-grades.html",
                                "/admin-students.html", "/admin-contacts.html", "/admin-payroll.html",
                                "/admin-registration.html", "/admin-line.html", "/admin-system.html").hasRole("ADMIN")
                        .requestMatchers("/teacher-dashboard.html", "/admin-attendance.html", "/admin-schedule.html", "/admin-learning.html",
                                "/mobile-rollcall.html").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/session/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/syllabus-import/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/learning-posts/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/classes/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/attendance/**").hasAnyRole("ADMIN", "TEACHER")
                        .requestMatchers("/api/admin/students/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/parent-contacts/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/registration-requests/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/leave-requests/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/payroll/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/line/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/system-health/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login.html")
                        .loginProcessingUrl("/login")
                        .successHandler(roleAwareSuccessHandler())
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(request -> "GET".equalsIgnoreCase(request.getMethod())
                                && "/logout".equals(request.getServletPath()))
                        .logoutSuccessUrl("/login.html?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll())
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/login", "/logout", "/h2-console/**",
                                "/api/admin/syllabus-import/**", "/api/admin/attendance/**",
                                "/api/admin/learning-posts/**",
                                "/api/admin/payroll/**",
                                "/api/public/registration-requests/**",
                                "/api/public/learning-posts/**",
                                "/api/admin/registration-requests/**",
                                "/api/admin/leave-requests/**",
                                "/api/admin/students/**",
                                "/api/admin/parent-contacts/**",
                                "/api/admin/line/**"))
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(JdbcTemplate jdbcTemplate) {
        return username -> jdbcTemplate.query("""
                        select username, password_hash, role, enabled
                        from users
                        where username = ?
                        """,
                resultSet -> {
                    if (!resultSet.next()) {
                        throw new UsernameNotFoundException("Unknown user: " + username);
                    }

                    String role = resultSet.getString("role").toUpperCase(Locale.ROOT);
                    return User.withUsername(resultSet.getString("username"))
                            .password(resultSet.getString("password_hash"))
                            .roles(role)
                            .disabled(!resultSet.getBoolean("enabled"))
                            .build();
                },
                username);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    private AuthenticationSuccessHandler roleAwareSuccessHandler() {
        return (request, response, authentication) -> {
            if (hasRole(authentication, "ROLE_ADMIN")) {
                response.sendRedirect("/admin-dashboard.html");
            } else {
                response.sendRedirect("/teacher-dashboard.html");
            }
        };
    }

    private AuthenticationFailureHandler loginFailureHandler() {
        return new AuthenticationFailureHandler() {
            @Override
            public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                                AuthenticationException exception) throws IOException, ServletException {
                response.sendRedirect("/login.html?error");
            }
        };
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role::equals);
    }
}
