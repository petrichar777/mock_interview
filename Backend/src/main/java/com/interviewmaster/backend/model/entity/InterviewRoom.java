package com.interviewmaster.backend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("im_interview_room")
public class InterviewRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String roomCode;

    @TableField("trtc_room_id")
    private Long trtcRoomId;

    @TableField("interviewer_id")
    private Long interviewerId;

    @TableField("candidate_id")
    private Long candidateId;

    private String position;

    private String level;

    private Integer status;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
