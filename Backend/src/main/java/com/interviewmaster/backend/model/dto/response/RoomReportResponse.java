package com.interviewmaster.backend.model.dto.response;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class RoomReportResponse {

    private String roomCode;

    private Integer status;

    private Integer overallScore;

    private String summary;

    private RoomReportPayload report;

    private String errorMessage;

    private LocalDateTime createdAt;
}
