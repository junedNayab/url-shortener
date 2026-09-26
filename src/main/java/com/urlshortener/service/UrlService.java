package com.urlshortener.service;

import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;
import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.util.Base62Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);
    private static final String CACHE_PREFIX = "url:";

    private final UrlRepository urlRepository;
    private final UserRepository userRepository;
    private final ClickEventRepository clickEventRepository;
    private final StringRedisTemplate redisTemplate;
    private final String baseUrl;

    public UrlService(UrlRepository urlRepository,
                      UserRepository userRepository,
                      ClickEventRepository clickEventRepository,
                      StringRedisTemplate redisTemplate,
                      @Value("${app.base-url}") String baseUrl) {
        this.urlRepository = urlRepository;
        this.userRepository = userRepository;
        this.clickEventRepository = clickEventRepository;
        this.redisTemplate = redisTemplate;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public ShortenResponse shortenUrl(ShortenRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String shortCode;
        if (request.customAlias() != null && !request.customAlias().isBlank()) {
            shortCode = request.customAlias().trim();
            if (urlRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(shortCode);
            }
        } else {
            shortCode = generateUniqueCode();
        }

        Url url = new Url();
        url.setOriginalUrl(request.originalUrl());
        url.setShortCode(shortCode);
        url.setUser(user);
        if (request.expiresInDays() != null && request.expiresInDays() > 0) {
            url.setExpiresAt(LocalDateTime.now().plusDays(request.expiresInDays()));
        }

        url = urlRepository.save(url);

        // Cache in Redis
        cacheUrl(shortCode, url.getOriginalUrl());

        log.info("Shortened URL: {} -> {}", shortCode, url.getOriginalUrl());

        return new ShortenResponse(
                baseUrl + "/s/" + shortCode,
                shortCode,
                url.getOriginalUrl(),
                url.getCreatedAt(),
                url.getExpiresAt()
        );
    }

    @Transactional
    public String resolve(String shortCode, String ipAddress, String userAgent, String referrer) {
        // Try Redis cache first
        String cachedUrl = getCachedUrl(shortCode);
        if (cachedUrl != null) {
            recordClick(shortCode, ipAddress, userAgent, referrer);
            return cachedUrl;
        }

        // Fallback to DB
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        // Check expiry
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UrlNotFoundException(shortCode);
        }

        // Populate cache
        cacheUrl(shortCode, url.getOriginalUrl());

        recordClick(shortCode, ipAddress, userAgent, referrer);

        return url.getOriginalUrl();
    }

    public List<ShortenResponse> getUserUrls(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return urlRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(url -> new ShortenResponse(
                        baseUrl + "/s/" + url.getShortCode(),
                        url.getShortCode(),
                        url.getOriginalUrl(),
                        url.getCreatedAt(),
                        url.getExpiresAt()
                ))
                .toList();
    }

    @Transactional
    public void deleteUrl(String shortCode, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        if (!url.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("You don't own this URL");
        }

        evictCachedUrl(shortCode);
        urlRepository.delete(url);
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = Base62Encoder.generateRandom(7);
        } while (urlRepository.existsByShortCode(code));
        return code;
    }

    private void cacheUrl(String shortCode, String originalUrl) {
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, originalUrl, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache URL in Redis: {}", e.getMessage());
        }
    }

    private String getCachedUrl(String shortCode) {
        try {
            return redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Redis cache read failed, falling back to database: {}", e.getMessage());
            return null;
        }
    }

    private void evictCachedUrl(String shortCode) {
        try {
            redisTemplate.delete(CACHE_PREFIX + shortCode);
        } catch (Exception e) {
            log.warn("Failed to evict URL from Redis: {}", e.getMessage());
        }
    }

    private void recordClick(String shortCode, String ipAddress, String userAgent, String referrer) {
        try {
            Url url = urlRepository.findByShortCode(shortCode).orElse(null);
            if (url != null) {
                ClickEvent click = new ClickEvent();
                click.setUrl(url);
                click.setIpAddress(ipAddress);
                click.setUserAgent(userAgent);
                click.setReferrer(referrer);
                clickEventRepository.save(click);
            }
        } catch (Exception e) {
            log.warn("Failed to record click event: {}", e.getMessage());
        }
    }
}
