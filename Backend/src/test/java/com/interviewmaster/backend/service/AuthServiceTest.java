package com.interviewmaster.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.util.RedisUtil;
import com.interviewmaster.backend.model.dto.request.LoginRequest;
import com.interviewmaster.backend.model.dto.request.ResetPasswordRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.request.SendResetCodeRequest;
import com.interviewmaster.backend.model.entity.User;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private RedisUtil redisUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userMapper, passwordEncoder, stringRedisTemplate, mailSenderProvider, redisUtil);
        // 注册和登录限流：默认返回 1（未超限），各测试可按需覆盖
        lenient().when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(1L);
    }

    @Test
    void register_ipLimitExceeded_throws429() {
        when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(4L);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("limitprobe");
        request.setEmail("limitprobe@example.com");
        request.setPassword("12345678");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.register(request, "127.0.0.1"));

        assertEquals(429, ex.getCode());
    }

    @Test
    void login_ipLimitExceeded_throws429() {
        when(redisUtil.incrementWithExpiry(anyString(), any())).thenReturn(21L);
        LoginRequest request = new LoginRequest();
        request.setAccount("nobody");
        request.setPassword("Password123");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(request, "127.0.0.1"));

        assertEquals(429, ex.getCode());
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("jack");
        request.setEmail("jack@example.com");
        request.setPassword("12345678");

        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        Long userId = authService.register(request, "127.0.0.1");

        assertEquals(1L, userId);
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void register_duplicate_username() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("jack");
        request.setEmail("jack@example.com");
        request.setPassword("12345678");

        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request, "127.0.0.1"));
        assertEquals("用户名或邮箱已被注册", ex.getMessage());
    }

    @Test
    void register_duplicate_email() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("jack");
        request.setEmail("jack@example.com");
        request.setPassword("12345678");

        when(userMapper.selectCount(any())).thenReturn(0L, 1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request, "127.0.0.1"));
        assertEquals("用户名或邮箱已被注册", ex.getMessage());
    }

    @Test
    void register_password_is_hashed() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("jack");
        request.setEmail("jack@example.com");
        request.setPassword("12345678");

        when(userMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertNotEquals("12345678", user.getPasswordHash());
            user.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        authService.register(request, "127.0.0.1");
    }

    @Test
    void send_reset_code_success() {
        SendResetCodeRequest request = new SendResetCodeRequest();
        request.setEmail("jack@example.com");
        User user = new User();
        user.setId(1L);
        user.setEmail("jack@example.com");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("auth:reset:email:jack@example.com"), eq("1"), eq(Duration.ofSeconds(60)))).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        authService.sendResetCode(request, "127.0.0.1");

        verify(valueOperations).set(eq("auth:reset:code:jack@example.com"), anyString(), eq(Duration.ofMinutes(10)));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void reset_password_success() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("jack@example.com");
        request.setVerifyCode("123456");
        request.setNewPassword("new-password-123");
        User user = new User();
        user.setId(1L);
        user.setEmail("jack@example.com");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(valueOperations.get("auth:reset:code:jack@example.com")).thenReturn("123456");
        when(stringRedisTemplate.delete(anyString())).thenReturn(true);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        authService.resetPassword(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertNotEquals("new-password-123", captor.getValue().getPasswordHash());
    }

    @Test
    void reset_password_wrong_verify_code() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("jack@example.com");
        request.setVerifyCode("123456");
        request.setNewPassword("new-password-123");
        User user = new User();
        user.setId(1L);
        user.setEmail("jack@example.com");

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(valueOperations.get("auth:reset:code:jack@example.com")).thenReturn("888888");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.resetPassword(request));
        org.junit.jupiter.api.Assertions.assertTrue(
            ex.getMessage().contains("验证码错误"),
            "Expected message to contain '验证码错误', but was: " + ex.getMessage()
        );
    }
}
