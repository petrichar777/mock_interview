package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.ResumeOptimization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResumeOptimizationMapper extends BaseMapper<ResumeOptimization> {

    @Select("SELECT * FROM im_resume_optimization WHERE user_id = #{userId} ORDER BY id DESC LIMIT 1")
    ResumeOptimization selectLatestByUserId(@Param("userId") Long userId);

    @Update("UPDATE im_resume_optimization SET status = #{status}, result_json = #{resultJson}, error_message = #{errorMessage}, updated_at = NOW() WHERE task_id = #{taskId}")
    int updateResultByTaskId(@Param("taskId") String taskId,
                             @Param("status") String status,
                             @Param("resultJson") String resultJson,
                             @Param("errorMessage") String errorMessage);
}
