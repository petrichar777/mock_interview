package com.interviewmaster.backend.mapper;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PracticeMapper {

    @Select("SELECT COUNT(*) FROM im_question WHERE module_id = #{moduleId} AND status = 0")
    Long countAvailableQuestionByModuleId(@Param("moduleId") Long moduleId);

    @Select({
            "<script>",
            "SELECT module_id AS moduleId, COUNT(*) AS total",
            "FROM im_question",
            "WHERE status = 0",
            "AND module_id IN",
            "<foreach collection='moduleIds' item='moduleId' open='(' separator=',' close=')'>",
            "#{moduleId}",
            "</foreach>",
            "GROUP BY module_id",
            "</script>"
    })
    List<Map<String, Object>> countAvailableQuestionByModuleIds(@Param("moduleIds") List<Long> moduleIds);

    @Select("""
            SELECT COUNT(DISTINCT question_id)
            FROM im_practice_record
            WHERE user_id = #{userId} AND module_id = #{moduleId}
            """)
    Long countDoneQuestionByUserAndModule(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    @Select({
            "<script>",
            "SELECT module_id AS moduleId, COUNT(DISTINCT question_id) AS total",
            "FROM im_practice_record",
            "WHERE user_id = #{userId}",
            "AND module_id IN",
            "<foreach collection='moduleIds' item='moduleId' open='(' separator=',' close=')'>",
            "#{moduleId}",
            "</foreach>",
            "GROUP BY module_id",
            "</script>"
    })
    List<Map<String, Object>> countDoneQuestionByUserAndModuleIds(@Param("userId") Long userId, @Param("moduleIds") List<Long> moduleIds);

    @Select("""
            SELECT DISTINCT type
            FROM im_question
            WHERE module_id = #{moduleId} AND status = 0
            ORDER BY type ASC
            """)
    List<Integer> listQuestionTypesByModuleId(@Param("moduleId") Long moduleId);

    @Select({
            "<script>",
            "SELECT module_id AS moduleId, type AS type",
            "FROM im_question",
            "WHERE status = 0",
            "AND module_id IN",
            "<foreach collection='moduleIds' item='moduleId' open='(' separator=',' close=')'>",
            "#{moduleId}",
            "</foreach>",
            "GROUP BY module_id, type",
            "ORDER BY module_id ASC, type ASC",
            "</script>"
    })
    List<Map<String, Object>> listQuestionTypeRowsByModuleIds(@Param("moduleIds") List<Long> moduleIds);

    @Select("""
            SELECT DISTINCT question_id
            FROM im_practice_record
            WHERE user_id = #{userId} AND module_id = #{moduleId}
            """)
    List<Long> listDoneQuestionIdsByUserAndModule(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    @Select({
            "<script>",
            "SELECT DISTINCT question_id",
            "FROM im_practice_record",
            "WHERE user_id = #{userId}",
            "AND question_id IN",
            "<foreach collection='questionIds' item='questionId' open='(' separator=',' close=')'>",
            "#{questionId}",
            "</foreach>",
            "</script>"
    })
    List<Long> listDoneQuestionIdsByUserAndQuestionIds(@Param("userId") Long userId, @Param("questionIds") List<Long> questionIds);

    @Insert("""
            INSERT INTO im_practice_record(user_id, module_id, question_id, answers, is_correct, time_cost_ms, answered_at)
            VALUES(#{userId}, #{moduleId}, #{questionId}, #{answers}, #{isCorrect}, #{timeCostMs}, #{answeredAt})
            """)
    int insertPracticeRecord(@Param("userId") Long userId,
                             @Param("moduleId") Long moduleId,
                             @Param("questionId") Long questionId,
                             @Param("answers") String answers,
                             @Param("isCorrect") Integer isCorrect,
                             @Param("timeCostMs") Long timeCostMs,
                             @Param("answeredAt") LocalDateTime answeredAt);

    @Select("SELECT COALESCE(SUM(time_cost_ms), 0) FROM im_practice_record WHERE user_id = #{userId}")
    Long sumPracticeTimeMsByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT MAX(answered_at)
            FROM im_practice_record
            WHERE user_id = #{userId} AND question_id = #{questionId} AND is_correct = 0
            """)
    LocalDateTime getLastWrongAt(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select({
            "<script>",
            "SELECT question_id AS questionId, MAX(answered_at) AS lastWrongAt",
            "FROM im_practice_record",
            "WHERE user_id = #{userId}",
            "AND is_correct = 0",
            "AND question_id IN",
            "<foreach collection='questionIds' item='questionId' open='(' separator=',' close=')'>",
            "#{questionId}",
            "</foreach>",
            "GROUP BY question_id",
            "</script>"
    })
    List<Map<String, Object>> listLastWrongAtByQuestionIds(@Param("userId") Long userId, @Param("questionIds") List<Long> questionIds);

    @Select("""
            SELECT answers
            FROM im_practice_record
            WHERE user_id = #{userId} AND question_id = #{questionId} AND is_correct = 0
            ORDER BY answered_at DESC, id DESC
            LIMIT 1
            """)
    String getLastWrongAnswers(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select({
            "<script>",
            "SELECT q.id",
            "FROM im_question q",
            "INNER JOIN im_user_mistake m ON m.question_id = q.id AND m.user_id = #{userId}",
            "LEFT JOIN (",
            "  SELECT question_id, MAX(answered_at) AS last_wrong_at",
            "  FROM im_practice_record",
            "  WHERE user_id = #{userId} AND is_correct = 0",
            "  GROUP BY question_id",
            ") p ON q.id = p.question_id",
            "WHERE q.status = 0",
            "AND q.id IN",
            "<foreach collection='questionIds' item='qid' open='(' separator=',' close=')'>",
            "  #{qid}",
            "</foreach>",
            "<if test='moduleId != null'>",
            "  AND q.module_id = #{moduleId}",
            "</if>",
            "ORDER BY",
            "<choose>",
            "  <when test='sortBy == \"module\"'>q.module_id ASC, COALESCE(p.last_wrong_at, m.created_at) DESC</when>",
            "  <when test='sortBy == \"difficulty_desc\"'>q.difficulty DESC, COALESCE(p.last_wrong_at, m.created_at) DESC</when>",
            "  <when test='sortBy == \"difficulty_asc\"'>q.difficulty ASC, COALESCE(p.last_wrong_at, m.created_at) DESC</when>",
            "  <otherwise>COALESCE(p.last_wrong_at, m.created_at) DESC, q.id DESC</otherwise>",
            "</choose>",
            "LIMIT #{offset}, #{limit}",
            "</script>"
    })
    List<Long> listMistakeQuestionIdsPaged(@Param("userId") Long userId,
                                            @Param("questionIds") List<Long> questionIds,
                                            @Param("moduleId") Long moduleId,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit,
                                            @Param("sortBy") String sortBy);

    @Select({
            "<script>",
            "SELECT COUNT(*)",
            "FROM im_question q",
            "INNER JOIN im_user_mistake m ON m.question_id = q.id AND m.user_id = #{userId}",
            "WHERE q.status = 0",
            "AND q.id IN",
            "<foreach collection='questionIds' item='qid' open='(' separator=',' close=')'>",
            "  #{qid}",
            "</foreach>",
            "<if test='moduleId != null'>",
            "  AND q.module_id = #{moduleId}",
            "</if>",
            "</script>"
    })
    int countMistakeQuestions(@Param("userId") Long userId,
                               @Param("questionIds") List<Long> questionIds,
                               @Param("moduleId") Long moduleId);

    @Select({
            "<script>",
            "SELECT",
            "  q.module_id AS moduleId,",
            "  COUNT(DISTINCT q.id) AS mistakeCount,",
            "  COUNT(pr.id) AS totalAttempts,",
            "  SUM(CASE WHEN pr.is_correct = 1 THEN 1 ELSE 0 END) AS correctAttempts",
            "FROM im_question q",
            "LEFT JOIN im_practice_record pr ON pr.question_id = q.id AND pr.user_id = #{userId}",
            "WHERE q.id IN",
            "<foreach collection='questionIds' item='qid' open='(' separator=',' close=')'>",
            "#{qid}",
            "</foreach>",
            "GROUP BY q.module_id",
            "ORDER BY mistakeCount DESC",
            "<if test='limit != null'>",
            "LIMIT #{limit}",
            "</if>",
            "</script>"
    })
    List<Map<String, Object>> listMistakeModuleStats(@Param("userId") Long userId,
                                                      @Param("questionIds") List<Long> questionIds,
                                                      @Param("limit") Integer limit);
}
