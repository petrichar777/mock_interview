package com.interviewmaster.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InterviewMessageMapper {

    @Insert("""
            INSERT INTO im_interview_message
            (id, session_id, role, turn_no, content, created_at)
            VALUES
            (#{messageId}, #{sessionId}, #{role}, #{turnNo}, #{content}, #{createdAt})
            """)
    int insertMessage(@Param("messageId") Long messageId,
                      @Param("sessionId") Long sessionId,
                      @Param("role") String role,
                      @Param("turnNo") Integer turnNo,
                      @Param("content") String content,
                      @Param("createdAt") String createdAt);

    @Select("SELECT id, session_id, role, turn_no, content, created_at FROM im_interview_message WHERE session_id = #{sessionId} ORDER BY id ASC")
    List<Map<String, Object>> selectMessagesBySessionId(@Param("sessionId") Long sessionId);
}
