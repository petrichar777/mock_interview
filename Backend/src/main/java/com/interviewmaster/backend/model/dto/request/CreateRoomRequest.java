package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class CreateRoomRequest {

    @NotBlank(message = "不能为空")
    @Size(max = 100, message = "长度不能超过100")
    private String position;

    @NotBlank(message = "不能为空")
    @Size(max = 32, message = "长度不能超过32")
    private String level;

    @NotEmpty(message = "不能为空")
    @Size(max = 20, message = "最多选择20道题")
    private List<Long> questionIds;
}
