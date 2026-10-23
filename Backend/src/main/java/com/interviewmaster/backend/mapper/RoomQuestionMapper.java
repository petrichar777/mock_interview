package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.RoomQuestion;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoomQuestionMapper extends BaseMapper<RoomQuestion> {

    @Insert("<script>"
            + "INSERT INTO im_room_question "
            + "(room_id, question_id, question_stem, module_id, module_name, order_no, result, note) "
            + "VALUES "
            + "<foreach collection='items' item='item' separator=','>"
            + "(#{item.roomId}, #{item.questionId,jdbcType=BIGINT}, #{item.questionStem}, "
            + "#{item.moduleId,jdbcType=BIGINT}, #{item.moduleName,jdbcType=VARCHAR}, "
            + "#{item.orderNo}, #{item.result}, #{item.note,jdbcType=VARCHAR})"
            + "</foreach>"
            + "</script>")
    int batchInsert(@Param("items") List<RoomQuestion> items);

    @Update("<script>"
            + "UPDATE im_room_question SET "
            + "result = CASE id "
            + "<foreach collection='items' item='item'>WHEN #{item.id} THEN #{item.result} </foreach>"
            + "ELSE result END, "
            + "note = CASE id "
            + "<foreach collection='items' item='item'>WHEN #{item.id} THEN #{item.note,jdbcType=VARCHAR} </foreach>"
            + "ELSE note END "
            + "WHERE room_id = #{roomId} AND id IN "
            + "<foreach collection='items' item='item' open='(' separator=',' close=')'>#{item.id}</foreach>"
            + "</script>")
    int batchUpdateEvaluation(@Param("roomId") Long roomId, @Param("items") List<RoomQuestion> items);
}
