package com.interviewmaster.backend.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.interviewmaster.backend.exception.BusinessException;
import com.interviewmaster.backend.mapper.UserMapper;
import com.interviewmaster.backend.model.dto.request.LoginByCodeRequest;
import com.interviewmaster.backend.model.dto.request.LoginRequest;
import com.interviewmaster.backend.model.dto.request.ResetPasswordRequest;
import com.interviewmaster.backend.model.dto.request.RegisterRequest;
import com.interviewmaster.backend.model.dto.request.SendLoginCodeRequest;
import com.interviewmaster.backend.model.dto.request.SendResetCodeRequest;
import com.interviewmaster.backend.model.entity.User;
import com.interviewmaster.backend.util.RedisUtil;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String RESET_CODE_KEY_PREFIX = "auth:reset:code:";
    private static final Duration RESET_CODE_TTL = Duration.ofMinutes(10);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final String VERIFY_ATTEMPT_KEY_PREFIX = "auth:reset:verify_attempt:";

    // 登录验证码
    private static final String LOGIN_CODE_KEY_PREFIX = "auth:login:code:";
    private static final Duration LOGIN_CODE_TTL = Duration.ofMinutes(5);
    private static final int MAX_LOGIN_CODE_ATTEMPTS = 5;
    private static final String LOGIN_CODE_ATTEMPT_KEY_PREFIX = "auth:login:code:attempt:";
    private static final String LOGIN_CODE_IP_KEY_PREFIX = "auth:login:code:ip:";
    private static final int MAX_LOGIN_CODE_IP_ATTEMPTS = 10;
    private static final Duration LOGIN_CODE_IP_WINDOW = Duration.ofMinutes(15);
    private static final String LOGIN_CODE_EMAIL_KEY_PREFIX = "auth:login:code:email:";

    // 登录限流
    private static final String LOGIN_IP_KEY_PREFIX = "auth:login:ip:";
    private static final Duration LOGIN_IP_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_IP_ATTEMPTS = 20;
    private static final String LOGIN_ACCOUNT_KEY_PREFIX = "auth:login:account:";
    private static final Duration LOGIN_ACCOUNT_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_LOGIN_ACCOUNT_ATTEMPTS = 5;

    // 注册限流: IP 维度
    private static final String REGISTER_IP_KEY_PREFIX = "auth:register:ip:";
    private static final Duration REGISTER_IP_WINDOW = Duration.ofHours(1);
    private static final int MAX_REGISTER_IP_ATTEMPTS = 3;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final RedisUtil redisUtil;

    @Transactional
    public Long register(RegisterRequest request, String clientIp) {
        // IP 维度注册限流
        String ipKey = REGISTER_IP_KEY_PREFIX + clientIp;
        Long ipAttempts = redisUtil.incrementWithExpiry(ipKey, REGISTER_IP_WINDOW);
        if (ipAttempts != null && ipAttempts > MAX_REGISTER_IP_ATTEMPTS) {
            throw new BusinessException(429, "注册操作过于频繁，请1小时后再试");
        }

        String username = request.getUsername() == null ? null : request.getUsername().trim();
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();

        if (username == null || username.isBlank()) {
            throw new BusinessException("参数错误：username 不能为空");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessException("参数错误：email 不能为空");
        }

        if (existsByUsername(username) || existsByEmail(email)) {
            throw new BusinessException("用户名或邮箱已被注册");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole("USER");
        user.setStatus(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            int inserted = userMapper.insert(user);
            if (inserted != 1 || user.getId() == null) {
                throw new BusinessException(500, "服务器内部错误");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException("用户名或邮箱已存在");
        }

        return user.getId();
    }

    @Transactional
    public Map<String, Object> login(LoginRequest request, String clientIp) {
        String account = request.getAccount() == null ? null : request.getAccount().trim();
        String password = request.getPassword();
        if (account == null || account.isBlank()) {
            throw new BusinessException("参数错误：account 不能为空");
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException("参数错误：password 不能为空");
        }

        // IP 维度限流检查
        String ipKey = LOGIN_IP_KEY_PREFIX + clientIp;
        Long ipAttempts = redisUtil.incrementWithExpiry(ipKey, LOGIN_IP_WINDOW);
        if (ipAttempts != null && ipAttempts > MAX_LOGIN_IP_ATTEMPTS) {
            throw new BusinessException(429, "登录尝试过于频繁，请15分钟后再试");
        }

        // 账号维度限流检查
        String accountKey = LOGIN_ACCOUNT_KEY_PREFIX + account.toLowerCase();
        Long accountAttempts = redisUtil.incrementWithExpiry(accountKey, LOGIN_ACCOUNT_WINDOW);
        if (accountAttempts != null && accountAttempts > MAX_LOGIN_ACCOUNT_ATTEMPTS) {
            throw new BusinessException(429, "该账号登录尝试过于频繁，请15分钟后再试");
        }

        User user = findByAccount(account);
        if (user == null) {
            throw new BusinessException(401, "账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 0) {
            // 账号正常但被禁用，回退计数器（不应该消耗用户的尝试次数）
            stringRedisTemplate.delete(accountKey);
            throw new BusinessException(403, "账号已禁用");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "账号或密码错误");
        }

        // 登录成功，清除限流计数器
        stringRedisTemplate.delete(ipKey);
        stringRedisTemplate.delete(accountKey);

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        LocalDateTime now = LocalDateTime.now();
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginAt(now);
        update.setUpdatedAt(now);
        userMapper.updateById(update);

        return Map.of(
                "token", token,
                "userId", user.getId(),
                "role", user.getRole()
        );
    }

    public void logout() {
        StpUtil.logout();
    }

    public void sendResetCode(SendResetCodeRequest request, String clientIp) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        if (email == null || email.isBlank()) {
            throw new BusinessException("参数错误：email 不能为空");
        }
        // IP rate limit: 3 attempts per 10 minutes (使用 Lua 脚本原子化操作)
        String ipKey = "auth:reset:ip:" + clientIp;
        Long ipCount = redisUtil.incrementWithExpiry(ipKey, Duration.ofMinutes(10));
        if (ipCount != null && ipCount > 3) {
            throw new BusinessException(429, "操作过于频繁，请10分钟后再试");
        }
        // Email rate limit: 1 attempt per 60 seconds (atomic setIfAbsent)
        String emailLimitKey = "auth:reset:email:" + email;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(emailLimitKey, "1", Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(429, "验证码已发送，请60秒后再试");
        }
        User user = findByEmail(email);
        if (user == null) {
            // 不泄露邮箱是否已注册，直接返回成功
            return;
        }

        String verifyCode = generateVerifyCode();
        String redisKey = resetCodeKey(email);
        stringRedisTemplate.opsForValue().set(redisKey, verifyCode, RESET_CODE_TTL);
        try {
            sendResetCodeMail(email, verifyCode);
        } catch (BusinessException ex) {
            stringRedisTemplate.delete(redisKey);
            stringRedisTemplate.delete(emailLimitKey);
            throw ex;
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(redisKey);
            stringRedisTemplate.delete(emailLimitKey);
            log.error("send reset code email failed, email={}", email, ex);
            if (ex.getMessage() != null
                    && ex.getMessage().contains("Mail from address must be same as authorization user")) {
                throw new BusinessException("邮件配置错误：发件人必须与授权账号一致，请检查 spring.mail.username");
            }
            throw new BusinessException("邮件发送失败，请稍后重试");
        }
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        String verifyCode = request.getVerifyCode() == null ? null : request.getVerifyCode().trim();
        String newPassword = request.getNewPassword();

        if (email == null || email.isBlank()) {
            throw new BusinessException("参数错误：email 不能为空");
        }
        if (verifyCode == null || verifyCode.isBlank()) {
            throw new BusinessException("参数错误：verifyCode 不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException("参数错误：newPassword 不能为空");
        }

        User user = findByEmail(email);
        if (user == null) {
            throw new BusinessException("验证码错误或已过期");
        }

        // 检查验证码错误次数，防止暴力破解（使用 Lua 脚本原子化操作）
        String attemptKey = VERIFY_ATTEMPT_KEY_PREFIX + email;
        Long attempts = redisUtil.incrementWithExpiry(attemptKey, RESET_CODE_TTL);
        if (attempts != null && attempts > MAX_VERIFY_ATTEMPTS) {
            // 超过最大尝试次数，删除验证码并重置计数
            stringRedisTemplate.delete(resetCodeKey(email));
            stringRedisTemplate.delete(attemptKey);
            throw new BusinessException("验证码错误次数过多，请重新获取验证码");
        }

        String redisKey = resetCodeKey(email);
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedCode == null || cachedCode.isBlank()) {
            throw new BusinessException("验证码已过期或不存在，请重新获取");
        }
        if (!cachedCode.equals(verifyCode)) {
            int remainingAttempts = attempts != null ? (int) (MAX_VERIFY_ATTEMPTS - attempts) : MAX_VERIFY_ATTEMPTS;
            if (remainingAttempts <= 0) {
                // 超过最大尝试次数，删除验证码并重置计数
                stringRedisTemplate.delete(redisKey);
                stringRedisTemplate.delete(attemptKey);
                throw new BusinessException("验证码错误次数过多，请重新获取验证码");
            }
            throw new BusinessException("验证码错误，剩余 " + remainingAttempts + " 次尝试机会");
        }

        // 验证通过，删除验证码
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.delete(attemptKey);

        LocalDateTime now = LocalDateTime.now();
        User update = new User();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(newPassword));
        update.setUpdatedAt(now);
        int updated = userMapper.updateById(update);
        if (updated != 1) {
            throw new BusinessException(500, "服务器内部错误");
        }
        // 清除邮箱限流标记，允许立即重新发送
        stringRedisTemplate.delete("auth:reset:email:" + email);
    }

    public void sendLoginCode(SendLoginCodeRequest request, String clientIp) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        if (email == null || email.isBlank()) {
            throw new BusinessException("参数错误：email 不能为空");
        }
        // IP rate limit: 10 attempts per 15 minutes
        String ipKey = LOGIN_CODE_IP_KEY_PREFIX + clientIp;
        Long ipCount = redisUtil.incrementWithExpiry(ipKey, LOGIN_CODE_IP_WINDOW);
        if (ipCount != null && ipCount > MAX_LOGIN_CODE_IP_ATTEMPTS) {
            throw new BusinessException(429, "操作过于频繁，请15分钟后再试");
        }
        // Email rate limit: 1 attempt per 60 seconds
        String emailLimitKey = LOGIN_CODE_EMAIL_KEY_PREFIX + email;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(emailLimitKey, "1", Duration.ofSeconds(60));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException(429, "验证码已发送，请60秒后再试");
        }
        User user = findByEmail(email);
        if (user == null) {
            return;
        }

        String verifyCode = generateVerifyCode();
        String redisKey = LOGIN_CODE_KEY_PREFIX + email;
        stringRedisTemplate.opsForValue().set(redisKey, verifyCode, LOGIN_CODE_TTL);
        try {
            sendLoginCodeMail(email, verifyCode);
        } catch (BusinessException ex) {
            stringRedisTemplate.delete(redisKey);
            stringRedisTemplate.delete(emailLimitKey);
            throw ex;
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(redisKey);
            stringRedisTemplate.delete(emailLimitKey);
            log.error("send login code email failed, email={}", email, ex);
            if (ex.getMessage() != null
                    && ex.getMessage().contains("Mail from address must be same as authorization user")) {
                throw new BusinessException("邮件配置错误：发件人必须与授权账号一致，请检查 spring.mail.username");
            }
            throw new BusinessException("邮件发送失败，请稍后重试");
        }
    }

    @Transactional
    public Map<String, Object> loginByCode(LoginByCodeRequest request, String clientIp) {
        String email = request.getEmail() == null ? null : request.getEmail().trim().toLowerCase();
        String code = request.getCode() == null ? null : request.getCode().trim();

        if (email == null || email.isBlank()) {
            throw new BusinessException("参数错误：email 不能为空");
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException("参数错误：code 不能为空");
        }

        User user = findByEmail(email);
        if (user == null) {
            throw new BusinessException("验证码错误或已过期");
        }
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new BusinessException(403, "账号已禁用");
        }

        // Check attempt limit
        String attemptKey = LOGIN_CODE_ATTEMPT_KEY_PREFIX + email;
        Long attempts = redisUtil.incrementWithExpiry(attemptKey, LOGIN_CODE_TTL);
        if (attempts != null && attempts > MAX_LOGIN_CODE_ATTEMPTS) {
            stringRedisTemplate.delete(LOGIN_CODE_KEY_PREFIX + email);
            stringRedisTemplate.delete(attemptKey);
            throw new BusinessException("验证码错误次数过多，请重新获取验证码");
        }

        String redisKey = LOGIN_CODE_KEY_PREFIX + email;
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedCode == null || cachedCode.isBlank()) {
            throw new BusinessException("验证码已过期或不存在，请重新获取");
        }
        if (!cachedCode.equals(code)) {
            int remaining = attempts != null ? (int) (MAX_LOGIN_CODE_ATTEMPTS - attempts) : MAX_LOGIN_CODE_ATTEMPTS;
            if (remaining <= 0) {
                stringRedisTemplate.delete(redisKey);
                stringRedisTemplate.delete(attemptKey);
                throw new BusinessException("验证码错误次数过多，请重新获取验证码");
            }
            throw new BusinessException("验证码错误，剩余 " + remaining + " 次尝试机会");
        }

        // Code correct, clean up
        stringRedisTemplate.delete(redisKey);
        stringRedisTemplate.delete(attemptKey);
        stringRedisTemplate.delete(LOGIN_CODE_EMAIL_KEY_PREFIX + email);

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        LocalDateTime now = LocalDateTime.now();
        User update = new User();
        update.setId(user.getId());
        update.setLastLoginAt(now);
        update.setUpdatedAt(now);
        userMapper.updateById(update);

        return Map.of(
                "token", token,
                "userId", user.getId(),
                "role", user.getRole()
        );
    }

    private boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        return count != null && count > 0;
    }

    private boolean existsByEmail(String email) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        return count != null && count > 0;
    }

    private User findByAccount(String account) {
        String email = account.toLowerCase();
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, email));
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }

    private String generateVerifyCode() {
        int number = ThreadLocalRandom.current().nextInt(100000, 1000000);
        return String.valueOf(number);
    }


    private String resetCodeKey(String email) {
        return RESET_CODE_KEY_PREFIX + email;
    }

    private void sendResetCodeMail(String email, String verifyCode) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BusinessException("邮件服务未配置，请先配置 spring.mail");
        }
        String from = null;
        if (mailSender instanceof JavaMailSenderImpl sender && (sender.getHost() == null || sender.getHost().isBlank())) {
            throw new BusinessException("邮件服务未配置，请先配置 spring.mail.host");
        } else if (mailSender instanceof JavaMailSenderImpl sender) {
            from = sender.getUsername();
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(email);
            helper.setSubject("面试达人 - 重置密码验证码");
            helper.setText(buildResetCodeHtml(verifyCode), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new BusinessException("邮件发送失败: " + e.getMessage());
        }
    }

    private String buildResetCodeHtml(String code) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#f8f9fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 16px;">
                  <tr>
                    <td align="center">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.06);">
                        <!-- Header -->
                        <tr>
                          <td style="padding:32px 32px 24px;text-align:center;background:linear-gradient(135deg,#f43f5e,#e11d48);">
                            <div style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:1px;">面 试 达 人</div>
                            <div style="font-size:12px;color:rgba(255,255,255,0.75);margin-top:4px;">Interview Master</div>
                          </td>
                        </tr>
                        <!-- Body -->
                        <tr>
                          <td style="padding:28px 32px 32px;">
                            <div style="font-size:15px;color:#374151;line-height:1.7;">
                              您好，
                            </div>
                            <div style="font-size:15px;color:#374151;line-height:1.7;margin-top:8px;">
                              您正在申请<strong>重置密码</strong>，请在 10 分钟内输入下方验证码完成验证：
                            </div>
                            <!-- Code box -->
                            <table width="100%%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
                              <tr>
                                <td align="center" style="background:#fef2f2;border:2px dashed #fca5a5;border-radius:12px;padding:20px 16px;">
                                  <span style="font-family:'SF Mono','Fira Code','Consolas',monospace;font-size:28px;font-weight:700;color:#e11d48;letter-spacing:8px;">%s</span>
                                </td>
                              </tr>
                            </table>
                            <!-- Tips -->
                            <div style="font-size:13px;color:#9ca3af;line-height:1.6;">
                              <div>· 验证码 10 分钟内有效，请尽快使用</div>
                              <div style="margin-top:4px;">· 如非本人操作，请忽略此邮件</div>
                            </div>
                          </td>
                        </tr>
                        <!-- Footer -->
                        <tr>
                          <td style="padding:16px 32px;border-top:1px solid #f3f4f6;text-align:center;">
                            <span style="font-size:11px;color:#d1d5db;">面试达人 · AI 驱动的高效面试准备平台</span>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(code);
    }

    private void sendLoginCodeMail(String email, String verifyCode) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new BusinessException("邮件服务未配置，请先配置 spring.mail");
        }
        String from = null;
        if (mailSender instanceof JavaMailSenderImpl sender && (sender.getHost() == null || sender.getHost().isBlank())) {
            throw new BusinessException("邮件服务未配置，请先配置 spring.mail.host");
        } else if (mailSender instanceof JavaMailSenderImpl sender) {
            from = sender.getUsername();
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            if (from != null && !from.isBlank()) {
                helper.setFrom(from);
            }
            helper.setTo(email);
            helper.setSubject("面试达人 - 登录验证码");
            helper.setText(buildLoginCodeHtml(verifyCode), true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new BusinessException("邮件发送失败: " + e.getMessage());
        }
    }

    private String buildLoginCodeHtml(String code) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"></head>
                <body style="margin:0;padding:0;background-color:#f8f9fa;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 16px;">
                  <tr>
                    <td align="center">
                      <table width="100%%" cellpadding="0" cellspacing="0" style="max-width:440px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.06);">
                        <tr>
                          <td style="padding:32px 32px 24px;text-align:center;background:linear-gradient(135deg,#f43f5e,#e11d48);">
                            <div style="font-size:22px;font-weight:800;color:#ffffff;letter-spacing:1px;">面 试 达 人</div>
                            <div style="font-size:12px;color:rgba(255,255,255,0.75);margin-top:4px;">Interview Master</div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 32px 32px;">
                            <div style="font-size:15px;color:#374151;line-height:1.7;">
                              您好，
                            </div>
                            <div style="font-size:15px;color:#374151;line-height:1.7;margin-top:8px;">
                              您正在通过<strong>邮箱验证码登录</strong>，请在 5 分钟内输入下方验证码完成登录：
                            </div>
                            <table width="100%%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
                              <tr>
                                <td align="center" style="background:#fef2f2;border:2px dashed #fca5a5;border-radius:12px;padding:20px 16px;">
                                  <span style="font-family:'SF Mono','Fira Code','Consolas',monospace;font-size:28px;font-weight:700;color:#e11d48;letter-spacing:8px;">%s</span>
                                </td>
                              </tr>
                            </table>
                            <div style="font-size:13px;color:#9ca3af;line-height:1.6;">
                              <div>· 验证码 5 分钟内有效，请尽快使用</div>
                              <div style="margin-top:4px;">· 如非本人操作，请忽略此邮件</div>
                            </div>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:16px 32px;border-top:1px solid #f3f4f6;text-align:center;">
                            <span style="font-size:11px;color:#d1d5db;">面试达人 · AI 驱动的高效面试准备平台</span>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
                </body>
                </html>
                """.formatted(code);
    }
}
