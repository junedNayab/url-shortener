package com.urlshortener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class UrlShortenerApplicationTests {

    @Test
    void mainClassExists() {
        assertDoesNotThrow(() -> Class.forName("com.urlshortener.UrlShortenerApplication"));
    }
}
