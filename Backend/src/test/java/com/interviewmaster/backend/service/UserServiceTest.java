package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.response.CurrentUserResponse;
import com.interviewmaster.backend.model.entity.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper);
    }

    @Test
    void get_current_user_info_success() {
        User user = new User();
        user.setId(1L);
        user.setUsername("jack");
        user.setEmail("jack@example.com");
        user.setRole("USER");
        user.setStatus(0);
        LocalDate today = LocalDate.now();
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.selectPracticeStats(1L)).thenReturn(Map.of("doneCount", 12L, "totalCount", 0L, "correctCount", 0L));
        when(userMapper.listCheckinDatesByUserId(1L)).thenReturn(List.of(today, today.minusDays(1), today.minusDays(2)));

        CurrentUserResponse response = userService.getCurrentUserInfo(1L);

        assertEquals(1L, response.getId());
        assertEquals("jack", response.getUsername());
        assertEquals("jack@example.com", response.getEmail());
        assertEquals("USER", response.getRole());
        assertEquals(0, response.getStatus());
        assertEquals(3, response.getContinuousCheckinDays());
        assertEquals(12, response.getDoneQuestionCount());
    }

    @Test
    void get_current_user_info_user_not_found() {
        when(userMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> userService.getCurrentUserInfo(99L));

        assertEquals(404, ex.getCode());
        assertEquals("用户不存在", ex.getMessage());
    }
}
