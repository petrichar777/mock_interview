package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.Question;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    @Select("<script>" +
            "SELECT module_id AS moduleId, COUNT(*) AS cnt " +
            "FROM im_question " +
            "WHERE module_id IN <foreach collection='moduleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY module_id" +
            "</script>")
    List<Map<String, Object>> countByModuleIds(List<Long> moduleIds);
}
