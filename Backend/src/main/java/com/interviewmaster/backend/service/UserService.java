package com.interviewmaster.backend.service;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.response.CurrentUserResponse;
import com.interviewmaster.backend.model.entity.User;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    public CurrentUserResponse getCurrentUserInfo(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        CurrentUserResponse response = new CurrentUserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setContinuousCheckinDays(calcContinuousCheckinDays(userId));
        response.setDoneQuestionCount(countDoneQuestion(userId));
        return response;
    }

    /**
     * 计算用户已刷题目数
     * @param userId 用户ID
     * @return 已刷题目数
     */
    private Integer countDoneQuestion(Long userId) {
        Map<String, Object> stats = userMapper.selectPracticeStats(userId);
        Object doneCount = stats.get("doneCount");
        if (doneCount instanceof Number num) {
            long value = num.longValue();
            if (value < 0) return 0;
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
        }
        return 0;
    }

    /**
     * 计算用户连续打卡天数
     * @param userId 用户ID
     * @return 连续打卡天数
     */
    private Integer calcContinuousCheckinDays(Long userId) {
        List<LocalDate> checkinDates = userMapper.listCheckinDatesByUserId(userId);
        if (checkinDates == null || checkinDates.isEmpty()) {
            return 0;
        }
        Set<LocalDate> checkinSet = new HashSet<>(checkinDates);
        int days = 0;
        LocalDate cursor = LocalDate.now();
        while (checkinSet.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }
}
