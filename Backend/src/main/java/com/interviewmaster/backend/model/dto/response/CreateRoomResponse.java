package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class CreateRoomResponse {

    private String roomCode;

    private Long trtcRoomId;

    private Integer status;
}
