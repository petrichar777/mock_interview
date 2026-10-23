package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.CategoryMapper;
import com.interviewmaster.backend.mapper.PracticeMapper;
import com.interviewmaster.backend.mapper.QuestionModuleMapper;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.response.DashboardRecommendedPracticesResponse;
import com.interviewmaster.backend.model.dto.response.DashboardSummaryResponse;
import com.interviewmaster.backend.model.entity.QuestionModule;
import com.interviewmaster.backend.model.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private QuestionModuleMapper questionModuleMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private PracticeMapper practiceMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CheckinService checkinService;

    @Mock
    private ObjectMapper objectMapper;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(checkinService.getContinuousDays(anyLong())).thenReturn(0);
        dashboardService = new DashboardService(userMapper, questionModuleMapper, categoryMapper, practiceMapper, stringRedisTemplate, checkinService, objectMapper);
    }

    @Test
    void get_summary_success() {
        LocalDate today = LocalDate.now();
        when(userMapper.selectPracticeStats(1L)).thenReturn(Map.of(
                "doneCount", 20L, "totalCount", 8L, "correctCount", 6L,
                "totalTimeMs", 750L * 60 * 1000));
        when(userMapper.countInterviewSessionByUserId(1L)).thenReturn(3L);
        when(checkinService.getContinuousDays(1L)).thenReturn(2);
        User user = new User();
        user.setId(1L);
        user.setExtraInterviewQuota(0);
        when(userMapper.selectById(1L)).thenReturn(user);

        DashboardSummaryResponse response = dashboardService.getSummary(1L);

        assertEquals(20, response.getDoneQuestionCount());
        assertEquals(0.75D, response.getCorrectRate());
        assertEquals(3, response.getInterviewCount());
        assertEquals(750, response.getTotalPracticeTimeMinutes());
        assertEquals(2, response.getContinuousCheckinDays());
        assertEquals(6, response.getRemainingDailyInterviews());
        assertEquals(6, response.getMaxDailyInterviews());
    }

    @Test
    void get_summary_no_practice() {
        when(userMapper.selectPracticeStats(2L)).thenReturn(Map.of("doneCount", 0L, "totalCount", 0L, "correctCount", 0L, "totalTimeMs", 0L));
        when(userMapper.countInterviewSessionByUserId(2L)).thenReturn(0L);
        User user = new User();
        user.setId(2L);
        user.setExtraInterviewQuota(0);
        when(userMapper.selectById(2L)).thenReturn(user);

        DashboardSummaryResponse response = dashboardService.getSummary(2L);

        assertEquals(0D, response.getCorrectRate());
        assertEquals(0, response.getContinuousCheckinDays());
    }

    @Test
    void get_summary_not_login() {
        BusinessException ex = assertThrows(BusinessException.class, () -> dashboardService.getSummary(null));
        assertEquals(401, ex.getCode());
        assertEquals("未登录", ex.getMessage());
    }

    @Test
    void get_recommended_practices_success() {
        QuestionModule backend = new QuestionModule();
        backend.setId(1L);
        backend.setCategoryName("Java基础");
        backend.setName("异常处理");
        backend.setDescription("异常模块");
        backend.setStatus(0);
        backend.setSort(1);
        QuestionModule frontend = new QuestionModule();
        frontend.setId(7L);
        frontend.setCategoryName("前端");
        frontend.setName("JavaScript基础");
        frontend.setDescription("前端模块");
        frontend.setStatus(0);
        frontend.setSort(2);
        when(questionModuleMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(backend, frontend));
        when(categoryMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(practiceMapper.countAvailableQuestionByModuleIds(org.mockito.ArgumentMatchers.anyList())).thenReturn(
                List.of(Map.of("moduleId", 1L, "total", 20L), Map.of("moduleId", 7L, "total", 10L)));
        when(practiceMapper.countDoneQuestionByUserAndModuleIds(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.anyList())).thenReturn(
                List.of(Map.of("moduleId", 1L, "total", 3L)));

        DashboardRecommendedPracticesResponse response = dashboardService.getRecommendedPractices(1L, null);

        assertNotNull(response);
        assertEquals(2, response.getList().size());
        assertEquals("CONTINUE", response.getList().get(0).getCtaType());
        assertEquals("START", response.getList().get(1).getCtaType());
        assertEquals("frontend", response.getList().get(1).getCategoryCode());
    }

    @Test
    void get_recommended_practices_invalid_limit() {
        BusinessException ex = assertThrows(BusinessException.class, () -> dashboardService.getRecommendedPractices(1L, 7));

        assertEquals("参数错误：limit 取值范围为 1-6", ex.getMessage());
    }
}
