package com.urlshortener.controller;

import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@Tag(name = "URLs", description = "Shorten, redirect, list, and delete URLs")
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    @PostMapping("/api/urls/shorten")
    @Operation(summary = "Shorten a URL (requires authentication)")
    public ResponseEntity<ShortenResponse> shorten(
            @Valid @RequestBody ShortenRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ShortenResponse response = urlService.shortenUrl(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/s/{shortCode}")
    @Operation(summary = "Redirect to original URL (public)")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {
        String originalUrl = urlService.resolve(
                shortCode,
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT),
                request.getHeader(HttpHeaders.REFERER)
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(originalUrl))
                .build();
    }

    @GetMapping("/api/urls")
    @Operation(summary = "List all your shortened URLs (requires authentication)")
    public ResponseEntity<List<ShortenResponse>> listUrls(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<ShortenResponse> urls = urlService.getUserUrls(userDetails.getUsername());
        return ResponseEntity.ok(urls);
    }

    @DeleteMapping("/api/urls/{shortCode}")
    @Operation(summary = "Delete a shortened URL (requires authentication)")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable String shortCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        urlService.deleteUrl(shortCode, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
