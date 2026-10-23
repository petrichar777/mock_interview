package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.RoomReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoomReportMapper extends BaseMapper<RoomReport> {

    @Update("UPDATE im_room_report "
            + "SET overall_score = #{overallScore}, summary = #{summary}, "
            + "report_json = #{reportJson}, model = #{model}, status = 1, error_message = NULL "
            + "WHERE id = #{id}")
    int markSuccess(@Param("id") Long id,
                    @Param("overallScore") Integer overallScore,
                    @Param("summary") String summary,
                    @Param("reportJson") String reportJson,
                    @Param("model") String model);

    @Update("UPDATE im_room_report "
            + "SET status = 2, error_message = #{errorMessage} WHERE id = #{id}")
    int markFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    @Update("UPDATE im_room_report "
            + "SET status = 0, overall_score = NULL, summary = NULL, "
            + "report_json = NULL, model = NULL, error_message = NULL WHERE id = #{id}")
    int resetForRetry(@Param("id") Long id);
}
