package com.interviewmaster.backend.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface InterviewSessionMapper {

    @Insert("""
            INSERT INTO im_interview_session
            (id, user_id, title, difficulty, focus, style, status, started_at, created_at, updated_at)
            VALUES
            (#{sessionId}, #{userId}, #{title}, #{difficulty}, #{focus}, #{style}, 0, #{startedAt}, #{startedAt}, #{startedAt})
            """)
    int insertSession(@Param("sessionId") Long sessionId,
                      @Param("userId") Long userId,
                      @Param("title") String title,
                      @Param("difficulty") Integer difficulty,
                      @Param("focus") Integer focus,
                      @Param("style") Integer style,
                      @Param("startedAt") String startedAt);

    @Insert("""
            INSERT INTO im_interview_session
            (id, user_id, title, difficulty, focus, style, status, started_at, finished_at, created_at, updated_at)
            VALUES
            (#{sessionId}, #{userId}, #{title}, #{difficulty}, #{focus}, #{style}, 1, #{startedAt}, #{finishedAt}, #{startedAt}, #{finishedAt})
            ON DUPLICATE KEY UPDATE
                status = 1,
                finished_at = VALUES(finished_at),
                updated_at = VALUES(updated_at)
            """)
    int upsertFinishedSession(@Param("sessionId") Long sessionId,
                              @Param("userId") Long userId,
                              @Param("title") String title,
                              @Param("difficulty") Integer difficulty,
                              @Param("focus") Integer focus,
                              @Param("style") Integer style,
                              @Param("startedAt") String startedAt,
                              @Param("finishedAt") String finishedAt);

    @Update("UPDATE im_interview_session SET evaluation = #{evaluation}, updated_at = NOW() WHERE id = #{sessionId}")
    int upsertEvaluation(@Param("sessionId") Long sessionId, @Param("evaluation") String evaluation);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM im_interview_session",
            "WHERE user_id = #{userId} AND status = 1",
            "<if test='mode != null'>",
            "  AND difficulty = #{difficulty}",
            "</if>",
            "</script>"
    })
    long countCompletedSessions(@Param("userId") Long userId,
                                @Param("mode") String mode,
                                @Param("difficulty") Integer difficulty);

    @Select({
            "<script>",
            "SELECT id, title, difficulty, focus, style, evaluation, finished_at",
            "FROM im_interview_session",
            "WHERE user_id = #{userId} AND status = 1",
            "<if test='mode != null'>",
            "  AND difficulty = #{difficulty}",
            "</if>",
            "ORDER BY finished_at DESC",
            "LIMIT #{offset}, #{limit}",
            "</script>"
    })
    List<Map<String, Object>> listCompletedSessions(@Param("userId") Long userId,
                                                     @Param("mode") String mode,
                                                     @Param("difficulty") Integer difficulty,
                                                     @Param("offset") int offset,
                                                     @Param("limit") int limit);

    @Select("SELECT id, user_id, title, difficulty, focus, style, status, evaluation, started_at, finished_at FROM im_interview_session WHERE id = #{sessionId}")
    Map<String, Object> selectSessionById(@Param("sessionId") Long sessionId);

    @Select({
            "SELECT id, title, difficulty, focus, evaluation, finished_at",
            "FROM im_interview_session",
            "WHERE user_id = #{userId} AND status = 1",
            "  AND evaluation IS NOT NULL AND evaluation != ''",
            "ORDER BY finished_at DESC",
            "LIMIT #{limit}"
    })
    List<Map<String, Object>> listLatestScoredSessions(@Param("userId") Long userId, @Param("limit") int limit);
}
