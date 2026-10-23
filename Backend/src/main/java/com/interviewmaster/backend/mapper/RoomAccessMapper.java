package com.interviewmaster.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RoomAccessMapper {

    @Select("SELECT COUNT(*) FROM im_interview_room "
            + "WHERE room_code = #{roomCode} AND status <> 2 "
            + "AND (interviewer_id = #{userId} OR candidate_id = #{userId})")
    int countActiveMembership(@Param("roomCode") String roomCode, @Param("userId") Long userId);
}
