package com.interviewmaster.backend.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomQuestionResult {

    UNRATED(0, "未评"),
    NONE(1, "不会"),
    AVERAGE(2, "一般"),
    MASTERED(3, "掌握");

    private final int code;
    private final String label;

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (RoomQuestionResult result : values()) {
            if (result.code == code) {
                return true;
            }
        }
        return false;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return UNRATED.label;
        }
        for (RoomQuestionResult result : values()) {
            if (result.code == code) {
                return result.label;
            }
        }
        return UNRATED.label;
    }
}
