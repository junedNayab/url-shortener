package com.urlshortener.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.dto.request.ShortenRequest;
import com.urlshortener.dto.response.ShortenResponse;
import com.urlshortener.exception.GlobalExceptionHandler;
import com.urlshortener.service.UrlService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UrlControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UrlService urlService;

    @InjectMocks
    private UrlController urlController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(urlController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Set up a mock authenticated user for @AuthenticationPrincipal
        UserDetails userDetails = new User("test@example.com", "pass", Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shorten_returns201() throws Exception {
        ShortenRequest request = new ShortenRequest("https://www.google.com", null, null);
        ShortenResponse response = new ShortenResponse(
                "http://localhost:8080/s/abc1234", "abc1234",
                "https://www.google.com", LocalDateTime.now(), null);

        when(urlService.shortenUrl(any(ShortenRequest.class), eq("test@example.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/urls/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://www.google.com"));
    }

    @Test
    void redirect_existingCode_returns302() throws Exception {
        when(urlService.resolve(eq("abc1234"), anyString(), any(), any()))
                .thenReturn("https://www.google.com");

        mockMvc.perform(get("/s/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://www.google.com"));
    }

    @Test
    void listUrls_returns200() throws Exception {
        ShortenResponse url1 = new ShortenResponse(
                "http://localhost:8080/s/abc", "abc",
                "https://google.com", LocalDateTime.now(), null);

        when(urlService.getUserUrls("test@example.com")).thenReturn(List.of(url1));

        mockMvc.perform(get("/api/urls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shortCode").value("abc"));
    }

    @Test
    void deleteUrl_returns204() throws Exception {
        mockMvc.perform(delete("/api/urls/abc1234"))
                .andExpect(status().isNoContent());
    }
}
