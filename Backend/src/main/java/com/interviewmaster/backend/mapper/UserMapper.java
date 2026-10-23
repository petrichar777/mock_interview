package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT COUNT(DISTINCT question_id) AS doneCount, COUNT(*) AS totalCount, COALESCE(SUM(is_correct), 0) AS correctCount, COALESCE(SUM(time_cost_ms), 0) AS totalTimeMs FROM im_practice_record WHERE user_id = #{userId}")
    Map<String, Object> selectPracticeStats(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM im_interview_session WHERE user_id = #{userId}")
    Long countInterviewSessionByUserId(@Param("userId") Long userId);

    @Select("SELECT checkin_date FROM im_checkin_day WHERE user_id = #{userId} ORDER BY checkin_date DESC")
    List<LocalDate> listCheckinDatesByUserId(@Param("userId") Long userId);

    @Update("UPDATE im_user SET extra_interview_quota = extra_interview_quota - 1 WHERE id = #{userId} AND extra_interview_quota > 0")
    int decrementExtraInterviewQuota(@Param("userId") Long userId);

    @Update("UPDATE im_user SET extra_resume_quota = extra_resume_quota - 1 WHERE id = #{userId} AND extra_resume_quota > 0")
    int decrementExtraResumeQuota(@Param("userId") Long userId);

    @Update("UPDATE im_user SET extra_interview_quota = 0, extra_resume_quota = 0 WHERE id = #{userId}")
    int clearExtraQuota(@Param("userId") Long userId);
}
