package com.interviewmaster.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("im_question")
public class Question {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("module_id")
    private Long moduleId;

    private Integer type;

    private Integer difficulty;

    private String stem;

    private String analysis;

    private String tags;

    private Integer status;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
