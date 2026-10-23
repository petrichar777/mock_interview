package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.Mistake;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MistakeMapper extends BaseMapper<Mistake> {

    @Insert("INSERT INTO im_user_mistake (user_id, question_id, created_at) VALUES (#{userId}, #{questionId}, NOW()) ON DUPLICATE KEY UPDATE created_at = NOW()")
    int upsert(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Delete("DELETE FROM im_user_mistake WHERE user_id = #{userId} AND question_id = #{questionId}")
    int deleteByUserAndQuestion(@Param("userId") Long userId, @Param("questionId") Long questionId);

    @Select("SELECT question_id FROM im_user_mistake WHERE user_id = #{userId}")
    List<Long> selectQuestionIdsByUserId(@Param("userId") Long userId);
}
