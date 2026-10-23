package com.interviewmaster.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("im_question_option")
public class QuestionOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("question_id")
    private Long questionId;

    @TableField("option_key")
    private String optionKey;

    @TableField("option_text")
    private String optionText;

    @TableField("is_correct")
    private Integer isCorrect;

    private Integer sort;
}
