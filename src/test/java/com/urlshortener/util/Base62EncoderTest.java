package com.urlshortener.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    @Test
    void encode_zero_returnsZeroChar() {
        assertEquals("0", Base62Encoder.encode(0));
    }

    @Test
    void encode_positiveNumbers() {
        assertEquals("1", Base62Encoder.encode(1));
        assertEquals("a", Base62Encoder.encode(10));
        assertEquals("A", Base62Encoder.encode(36));
        assertEquals("10", Base62Encoder.encode(62));
        assertEquals("11", Base62Encoder.encode(63));
    }

    @Test
    void encode_largeNumber() {
        String result = Base62Encoder.encode(999999999L);
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void generateRandom_correctLength() {
        String result = Base62Encoder.generateRandom(7);
        assertEquals(7, result.length());
    }

    @Test
    void generateRandom_onlyBase62Characters() {
        String result = Base62Encoder.generateRandom(100);
        assertTrue(result.matches("[0-9a-zA-Z]+"));
    }

    @Test
    void generateRandom_producesUniqueValues() {
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            results.add(Base62Encoder.generateRandom(7));
        }
        // With 62^7 possible values, 1000 should all be unique
        assertEquals(1000, results.size());
    }
}
