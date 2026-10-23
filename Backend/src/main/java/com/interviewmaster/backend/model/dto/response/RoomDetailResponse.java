package com.interviewmaster.backend.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class RoomDetailResponse {

    private String roomCode;

    private Integer status;

    private String position;

    private String level;

    private Long trtcRoomId;

    private RoomParticipantResponse interviewer;

    private RoomParticipantResponse candidate;

    private List<RoomQuestionResponse> questions;

    private Integer answeredCount;

    private Integer totalCount;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
