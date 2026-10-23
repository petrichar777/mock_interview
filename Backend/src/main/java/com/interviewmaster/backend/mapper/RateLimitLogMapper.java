package com.interviewmaster.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.interviewmaster.backend.model.entity.RateLimitLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RateLimitLogMapper extends BaseMapper<RateLimitLog> {
}
