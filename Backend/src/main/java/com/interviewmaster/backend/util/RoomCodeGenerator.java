package com.interviewmaster.backend.util;

import java.security.SecureRandom;

public final class RoomCodeGenerator {

    public static final int CODE_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private RoomCodeGenerator() {
    }

    public static String generate() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            code[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
