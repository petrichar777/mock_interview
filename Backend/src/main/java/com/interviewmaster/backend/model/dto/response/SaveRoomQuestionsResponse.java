package com.interviewmaster.backend.model.dto.response;

import lombok.Data;

@Data
public class SaveRoomQuestionsResponse {

    private Integer updatedCount;

    private Integer answeredCount;

    private Integer totalCount;
}
