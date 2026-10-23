package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CheckinDayMapper;
import com.interviewmaster.backend.model.dto.response.CheckinHeatmapResponse;
import com.interviewmaster.backend.model.dto.response.CheckinResultResponse;
import com.interviewmaster.backend.model.entity.CheckinDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock
    private CheckinDayMapper checkinDayMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private CheckinPersistenceService checkinPersistenceService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CheckinService checkinService;

    @BeforeEach
    void setUp() {
        checkinService = new CheckinService(checkinDayMapper, stringRedisTemplate, checkinPersistenceService);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(stringRedisTemplate.delete(anyString())).thenReturn(true);
    }

    @Test
    void checkin_today_success() {
        LocalDate today = LocalDate.now();
        CheckinDay checkinDay = new CheckinDay();
        checkinDay.setCheckinDate(today);
        when(valueOperations.setBit(anyString(), any(long.class), any(boolean.class))).thenReturn(false);
        when(checkinDayMapper.selectList(any())).thenReturn(List.of(checkinDay));

        CheckinResultResponse response = checkinService.checkinToday(1L);

        assertTrue(response.getCheckedIn());
        assertEquals(1, response.getContinuousDays());
    }

    @Test
    void get_heatmap_success() {
        LocalDate today = LocalDate.now();
        CheckinDay day1 = new CheckinDay();
        day1.setCheckinDate(LocalDate.of(2026, 3, 1));
        CheckinDay day2 = new CheckinDay();
        day2.setCheckinDate(LocalDate.of(2026, 3, 3));
        CheckinDay todayRecord = new CheckinDay();
        todayRecord.setCheckinDate(today);
        when(checkinDayMapper.selectList(any())).thenReturn(List.of(day1, day2), List.of(todayRecord));

        CheckinHeatmapResponse response = checkinService.getHeatmap(1L, "2026-03");

        assertEquals("2026-03", response.getMonth());
        assertEquals(List.of(1, 3), response.getCheckedDays());
        assertEquals(1, response.getContinuousDays());
    }

    @Test
    void get_heatmap_invalid_month() {
        BusinessException ex = assertThrows(BusinessException.class, () -> checkinService.getHeatmap(1L, "2026/03"));

        assertEquals("参数错误：month 格式应为 yyyy-MM", ex.getMessage());
    }
}
