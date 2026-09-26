package com.urlshortener.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AnalyticsResponse(
        String shortCode,
        String originalUrl,
        long totalClicks,
        LocalDateTime createdAt,
        List<ClickDetail> recentClicks
) {
    public record ClickDetail(
            LocalDateTime clickedAt,
            String ipAddress,
            String referrer
    ) {
    }
}
