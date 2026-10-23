package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class RoomParticipantResponse {

    private Long id;

    private String username;

    private String role;
}
