package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class FinishRoomResponse {

    private String roomCode;

    private Integer roomStatus;

    private Integer reportStatus;
}
