package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class SaveRoomQuestionsRequest {

    @NotEmpty(message = "不能为空")
    @Size(max = 50, message = "一次最多保存50道题")
    private List<@Valid RoomQuestionItem> questions;

    @Data
    public static class RoomQuestionItem {

        @NotNull(message = "不能为空")
        private Long roomQuestionId;

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "必须在0-3之间")
        @Max(value = 3, message = "必须在0-3之间")
        private Integer result;

        @Size(max = 500, message = "长度不能超过500")
        private String note;
    }
}
