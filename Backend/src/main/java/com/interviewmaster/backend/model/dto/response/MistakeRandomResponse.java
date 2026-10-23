package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class MistakeRandomResponse {

    private List<Long> questionIds;
}
