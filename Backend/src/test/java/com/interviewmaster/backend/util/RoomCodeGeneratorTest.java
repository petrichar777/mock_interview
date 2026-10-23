package com.interviewmaster.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    @Test
    void generate_returnsSixCharactersFromSafeAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = RoomCodeGenerator.generate();

            assertEquals(6, code.length());
            assertTrue(code.matches("[A-Z2-9]{6}"));
            assertTrue(code.chars().noneMatch(ch -> ch == 'I' || ch == 'O' || ch == '0' || ch == '1'));
        }
    }
}
