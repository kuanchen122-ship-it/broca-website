package com.example.brocawebsite.config;

public record CurrentUserResponse(
        String username,
        String displayName,
        String role
) {
}
