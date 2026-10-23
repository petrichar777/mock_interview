package com.interviewmaster.backend.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewRoomStatus {

    WAITING(0, "待加入"),
    IN_PROGRESS(1, "进行中"),
    FINISHED(2, "已结束");

    private final int code;
    private final String label;

    public static boolean isFinished(Integer code) {
        return code != null && code == FINISHED.code;
    }
}
