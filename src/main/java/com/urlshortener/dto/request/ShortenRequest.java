package com.urlshortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record ShortenRequest(
        @NotBlank(message = "URL is required")
        @URL(message = "Must be a valid URL")
        String originalUrl,

        String customAlias,

        Long expiresInDays
) {
}
