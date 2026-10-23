package com.interviewmaster.backend.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoomReportStatus {

    GENERATING(0, "生成中"),
    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    private final int code;
    private final String label;
}
