package com.interviewmaster.backend.model.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class AdminQuestionUpsertRequest {

    @NotNull(message = "moduleId 不能为空")
    private Long moduleId;

    @NotNull(message = "type 不能为空")
    private Integer type;

    @NotNull(message = "difficulty 不能为空")
    private Integer difficulty;

    @NotBlank(message = "stem 不能为空")
    @Size(max = 2000, message = "stem 长度不能超过2000")
    private String stem;

    @Size(max = 5000, message = "analysis 长度不能超过5000")
    private String analysis;

    @Size(max = 500, message = "tags 长度不能超过500")
    private String tags;

    private Integer status;

    @NotEmpty(message = "options 不能为空")
    @Valid
    private List<OptionItem> options;

    @Data
    public static class OptionItem {

        @NotBlank(message = "key 不能为空")
        @Size(max = 8, message = "key 长度不能超过8")
        private String key;

        @Size(max = 2000, message = "text 长度不能超过2000")
        private String text;

        @NotNull(message = "correct 不能为空")
        private Boolean correct;

        private Integer sort;
    }
}
