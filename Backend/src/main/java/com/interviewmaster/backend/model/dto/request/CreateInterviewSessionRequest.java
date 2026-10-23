package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateInterviewSessionRequest {

    @Size(max = 128, message = "长度不能超过128")
    private String jobTitle;

    @Size(max = 10000, message = "长度不能超过10000")
    private String jdText;

    @Size(max = 64, message = "长度不能超过64")
    private String resumeFileId;

    @Size(max = 20000, message = "长度不能超过20000")
    private String extractedResumeText;

    @Size(max = 20000, message = "长度不能超过20000")
    private String resumeText;

    @jakarta.validation.constraints.NotBlank(message = "不能为空")
    @Size(max = 32, message = "长度不能超过32")
    private String mode;

    @jakarta.validation.constraints.NotBlank(message = "不能为空")
    @Size(max = 32, message = "长度不能超过32")
    private String style;

    @Size(max = 256, message = "长度不能超过256")
    private String algorithmDirection;
}
