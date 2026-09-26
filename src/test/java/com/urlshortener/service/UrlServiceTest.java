package com.urlshortener.service;

import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;
import com.urlshortener.entity.Url;
import com.urlshortener.entity.User;
import com.urlshortener.exception.AliasAlreadyExistsException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlRepository;
import com.urlshortener.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClickEventRepository clickEventRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UrlService urlService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "baseUrl", "http://localhost:8080");

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setName("Test User");
    }

    @Test
    void shortenUrl_withoutAlias_generatesCode() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", null, null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setId(1L);
            url.setCreatedAt(LocalDateTime.now());
            return url;
        });

        ShortenResponse response = urlService.shortenUrl(request, "test@example.com");

        assertNotNull(response);
        assertNotNull(response.shortCode());
        assertEquals(7, response.shortCode().length());
        assertEquals("https://www.google.com", response.originalUrl());
        assertTrue(response.shortUrl().startsWith("http://localhost:8080/s/"));
        verify(urlRepository).save(any(Url.class));
    }

    @Test
    void shortenUrl_withCustomAlias_usesAlias() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", "google", null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(urlRepository.existsByShortCode("google")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(urlRepository.save(any(Url.class))).thenAnswer(invocation -> {
            Url url = invocation.getArgument(0);
            url.setId(1L);
            url.setCreatedAt(LocalDateTime.now());
            return url;
        });

        ShortenResponse response = urlService.shortenUrl(request, "test@example.com");

        assertEquals("google", response.shortCode());
        assertEquals("http://localhost:8080/s/google", response.shortUrl());
    }

    @Test
    void shortenUrl_duplicateAlias_throwsException() {
        ShortenRequest request = new ShortenRequest("https://www.google.com", "taken", null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(urlRepository.existsByShortCode("taken")).thenReturn(true);

        assertThrows(AliasAlreadyExistsException.class,
                () -> urlService.shortenUrl(request, "test@example.com"));
    }

    @Test
    void resolve_cachedUrl_returnsCachedValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abc1234")).thenReturn("https://www.google.com");

        Url url = new Url();
        url.setId(1L);
        url.setShortCode("abc1234");
        url.setOriginalUrl("https://www.google.com");
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        String result = urlService.resolve("abc1234", "127.0.0.1", "Mozilla", null);

        assertEquals("https://www.google.com", result);
    }

    @Test
    void resolve_notFound_throwsException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:notexist")).thenReturn(null);
        when(urlRepository.findByShortCode("notexist")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class,
                () -> urlService.resolve("notexist", "127.0.0.1", "Mozilla", null));
    }

    @Test
    void resolve_expiredUrl_throwsException() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:expired")).thenReturn(null);

        Url url = new Url();
        url.setId(1L);
        url.setShortCode("expired");
        url.setOriginalUrl("https://expired.com");
        url.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(urlRepository.findByShortCode("expired")).thenReturn(Optional.of(url));

        assertThrows(UrlNotFoundException.class,
                () -> urlService.resolve("expired", "127.0.0.1", "Mozilla", null));
    }
}
