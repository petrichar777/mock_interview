package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.InterviewRoom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InterviewRoomMapper extends BaseMapper<InterviewRoom> {

    @Update("UPDATE im_interview_room "
            + "SET candidate_id = #{userId}, status = 1, started_at = COALESCE(started_at, NOW()) "
            + "WHERE room_code = #{roomCode} AND status <> 2 AND interviewer_id <> #{userId} "
            + "AND (candidate_id IS NULL OR candidate_id = #{userId})")
    int joinAsCandidate(@Param("roomCode") String roomCode, @Param("userId") Long userId);

    @Update("UPDATE im_interview_room "
            + "SET status = 2, finished_at = COALESCE(finished_at, NOW()) "
            + "WHERE id = #{roomId} AND status <> 2")
    int finishRoom(@Param("roomId") Long roomId);
}
