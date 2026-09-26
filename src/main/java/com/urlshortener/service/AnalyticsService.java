package com.urlshortener.service;

import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsService {

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(UrlRepository urlRepository,
                            UserRepository userRepository,
                            ClickEventRepository clickEventRepository) {
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.clickEventRepository = clickEventRepository;
    }

    public AnalyticsResponse getAnalytics(String shortCode, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (!url.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You don't own this URL");
        }

        long totalClicks = clickEventRepository.countByUrlId(url.getId());

        List<AnalyticsResponse.ClickDetail> recentClicks = clickEventRepository
                .findTop20ByUrlIdOrderByClickedAtDesc(url.getId())
                .stream()
                .map(click -> new AnalyticsResponse.ClickDetail(
                        click.getClickedAt(),
                        click.getIpAddress(),
                        click.getReferrer()
                ))
                .toList();

        return new AnalyticsResponse(
                url.getShortCode(),
                url.getOriginalUrl(),
                totalClicks,
                url.getCreatedAt(),
                recentClicks
        );
    }
}
