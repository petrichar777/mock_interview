package com.interviewmaster.backend.model.dto.response;

import java.util.List;
import lombok.Data;

@Data
public class ResumeParseResponse {

    private String resumeFileId;

    private String fileName;

    private Long fileSize;

    private String parseStatus;

    private String extractedText;

    private List<String> warnings;
}
