package com.interviewmaster.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CheckinDayMapper;
import com.interviewmaster.backend.model.dto.response.CheckinHeatmapResponse;
import com.interviewmaster.backend.model.dto.response.CheckinResultResponse;
import com.interviewmaster.backend.model.entity.CheckinDay;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinService {

    private static final String SIGN_BITMAP_KEY_PREFIX = "user:sign:";
    private static final String CONTINUOUS_DAYS_CACHE_KEY_PREFIX = "user:sign:continuous:";
    private static final Duration CONTINUOUS_DAYS_CACHE_TTL = Duration.ofDays(1);

    private final CheckinDayMapper checkinDayMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CheckinPersistenceService checkinPersistenceService;

    public CheckinResultResponse checkinToday(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        LocalDate today = LocalDate.now();
        String bitmapKey = buildBitmapKey(userId, today);

        // Atomic check-and-set: SETBIT returns the previous bit value, eliminating the
        // race window between GETBIT check and MySQL insert.
        boolean alreadyCheckedIn;
        try {
            alreadyCheckedIn = Boolean.TRUE.equals(
                    stringRedisTemplate.opsForValue().setBit(bitmapKey, today.getDayOfMonth() - 1, true));
        } catch (Exception ex) {
            log.warn("Failed to set Redis bitmap for checkin, userId={}, date={}, reason={}",
                    userId, today, ex.getMessage());
            alreadyCheckedIn = false;
        }

        if (!alreadyCheckedIn) {
            CheckinDay checkinDay = new CheckinDay();
            checkinDay.setUserId(userId);
            checkinDay.setCheckinDate(today);
            checkinDay.setCreatedAt(LocalDateTime.now());
            checkinPersistenceService.persistCheckinAsync(checkinDay);
        }

        CheckinResultResponse response = new CheckinResultResponse();
        response.setCheckedIn(true);
        // Invalidate cached continuous days so it's recalculated fresh
        stringRedisTemplate.delete(CONTINUOUS_DAYS_CACHE_KEY_PREFIX + userId);
        response.setContinuousDays(calcContinuousDays(userId));
        return response;
    }

    public CheckinHeatmapResponse getHeatmap(Long userId, String month) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
        YearMonth yearMonth;
        try {
            yearMonth = YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("参数错误：month 格式应为 yyyy-MM");
        }

        String bitmapKey = buildBitmapKey(userId, yearMonth);
        List<Integer> checkedDays;

        try {
            checkedDays = readCheckedDaysFromBitmap(bitmapKey, yearMonth);
            if (checkedDays.isEmpty()) {
                checkedDays = readCheckedDaysFromDatabase(userId, yearMonth);
            }
        } catch (Exception ex) {
            log.warn("Failed to read Redis bitmap, falling back to DB. userId={}, month={}, reason={}",
                    userId, month, ex.getMessage());
            checkedDays = readCheckedDaysFromDatabase(userId, yearMonth);
        }

        CheckinHeatmapResponse response = new CheckinHeatmapResponse();
        response.setMonth(yearMonth.toString());
        response.setCheckedDays(checkedDays);
        response.setContinuousDays(calcContinuousDays(userId));
        return response;
    }

    private String buildBitmapKey(Long userId, LocalDate date) {
        return SIGN_BITMAP_KEY_PREFIX + userId + ":" + date.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private String buildBitmapKey(Long userId, YearMonth yearMonth) {
        return SIGN_BITMAP_KEY_PREFIX + userId + ":" + yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private List<Integer> readCheckedDaysFromBitmap(String bitmapKey, YearMonth yearMonth) {
        int daysInMonth = yearMonth.lengthOfMonth();
        byte[] keyBytes = bitmapKey.getBytes(StandardCharsets.UTF_8);
        List<Object> pipeResults = stringRedisTemplate.executePipelined((RedisConnection connection) -> {
            for (int day = 1; day <= daysInMonth; day++) {
                connection.getBit(keyBytes, day - 1);
            }
            return null;
        });
        List<Integer> result = new ArrayList<>();
        if (pipeResults != null) {
            int day = 1;
            for (Object obj : pipeResults) {
                if (Boolean.TRUE.equals(obj) || (obj instanceof Long l && l == 1L)) {
                    result.add(day);
                }
                day++;
            }
        }
        return result;
    }

    private List<Integer> readCheckedDaysFromDatabase(Long userId, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        List<CheckinDay> records = checkinDayMapper.selectList(new LambdaQueryWrapper<CheckinDay>()
                .eq(CheckinDay::getUserId, userId)
                .between(CheckinDay::getCheckinDate, startDate, endDate)
                .orderByAsc(CheckinDay::getCheckinDate));
        return records.stream()
                .map(CheckinDay::getCheckinDate)
                .map(LocalDate::getDayOfMonth)
                .distinct()
                .collect(Collectors.toList());
    }

    public int getContinuousDays(Long userId) {
        return calcContinuousDays(userId);
    }

    private Integer calcContinuousDays(Long userId) {
        String cacheKey = CONTINUOUS_DAYS_CACHE_KEY_PREFIX + userId;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return Integer.parseInt(cached);
            } catch (NumberFormatException ignored) {
            }
        }
        Set<LocalDate> checkinSet = buildCheckinSet(userId);
        int days = 0;
        LocalDate cursor = LocalDate.now();
        while (checkinSet.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        stringRedisTemplate.opsForValue().set(cacheKey, String.valueOf(days), CONTINUOUS_DAYS_CACHE_TTL);
        return days;
    }

    private Set<LocalDate> buildCheckinSet(Long userId) {
        Set<LocalDate> checkinSet = new HashSet<>();
        try {
            LocalDate today = LocalDate.now();
            for (int monthOffset = 0; monthOffset < 3; monthOffset++) {
                YearMonth yearMonth = YearMonth.from(today.minusMonths(monthOffset));
                String bitmapKey = buildBitmapKey(userId, yearMonth);
                byte[] keyBytes = bitmapKey.getBytes(StandardCharsets.UTF_8);
                int daysInMonth = yearMonth.lengthOfMonth();
                final YearMonth capturedMonth = yearMonth;
                List<Object> pipeResults = stringRedisTemplate.executePipelined((RedisConnection connection) -> {
                    for (int day = 1; day <= daysInMonth; day++) {
                        connection.getBit(keyBytes, day - 1);
                    }
                    return null;
                });
                if (pipeResults != null) {
                    int day = 1;
                    for (Object obj : pipeResults) {
                        if (Boolean.TRUE.equals(obj) || (obj instanceof Long l && l == 1L)) {
                            checkinSet.add(capturedMonth.atDay(day));
                        }
                        day++;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to read Redis bitmap for continuous days, userId={}, reason={}", userId, ex.getMessage());
        }
        if (checkinSet.isEmpty()) {
            List<CheckinDay> records = checkinDayMapper.selectList(new LambdaQueryWrapper<CheckinDay>()
                    .eq(CheckinDay::getUserId, userId)
                    .orderByDesc(CheckinDay::getCheckinDate));
            if (records != null) {
                for (CheckinDay record : records) {
                    if (record.getCheckinDate() != null) {
                        checkinSet.add(record.getCheckinDate());
                    }
                }
            }
        }
        return checkinSet;
    }
}
