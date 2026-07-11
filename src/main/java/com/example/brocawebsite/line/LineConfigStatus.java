package com.example.brocawebsite.line;

public record LineConfigStatus(
        boolean channelAccessTokenConfigured,
        boolean channelSecretConfigured,
        boolean liffIdConfigured,
        boolean sendingEnabled,
        String mode,
        String webhookPath,
        String liffPath
) {
}
