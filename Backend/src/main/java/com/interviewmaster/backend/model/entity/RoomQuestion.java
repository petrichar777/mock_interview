package com.interviewmaster.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("im_room_question")
public class RoomQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("room_id")
    private Long roomId;

    @TableField("question_id")
    private Long questionId;

    @TableField("question_stem")
    private String questionStem;

    @TableField("module_id")
    private Long moduleId;

    @TableField("module_name")
    private String moduleName;

    @TableField("order_no")
    private Integer orderNo;

    private Integer result;

    private String note;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
