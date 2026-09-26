package com.urlshortener.dto.response;

import java.time.LocalDateTime;

public record ShortenResponse(
        String shortUrl,
        String shortCode,
        String originalUrl,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
